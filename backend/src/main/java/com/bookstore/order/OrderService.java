package com.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookstore.catalog.Product;
import com.bookstore.catalog.ProductRepository;
import com.bookstore.common.ApiException;
import com.bookstore.common.AppProperties;
import com.bookstore.member.Address;
import com.bookstore.member.AddressRepository;
import com.bookstore.member.MemberDtos.AddressRequest;
import com.bookstore.member.User;
import com.bookstore.member.UserRepository;
import com.bookstore.order.OrderDtos.BasketView;
import com.bookstore.order.OrderDtos.CheckoutRequest;
import com.bookstore.order.OrderDtos.CouponRequest;
import com.bookstore.order.OrderDtos.CouponView;
import com.bookstore.order.OrderDtos.OrderItemView;
import com.bookstore.order.OrderDtos.OrderView;
import com.bookstore.order.OrderDtos.Quote;
import com.bookstore.order.OrderDtos.QuoteRequest;
import com.bookstore.payment.Payment;
import com.bookstore.payment.PaymentDtos.PaymentView;
import com.bookstore.payment.PaymentService;
import com.bookstore.security.CurrentUser;
import com.bookstore.shipping.ShippingService;

/** Order service: pricing, checkout, cancel (48h), return, order history, buy again. */
@Service
public class OrderService {

    static final Duration CANCEL_WINDOW = Duration.ofHours(48);
    static final String SUCCESS_MESSAGE = "Your purchase of the following reads is successful";

    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;
    private final AddressRepository addresses;
    private final CouponRepository coupons;
    private final BasketService baskets;
    private final ShippingService shipping;
    private final PaymentService payments;
    private final AppProperties props;

    public OrderService(OrderRepository orders, ProductRepository products, UserRepository users,
            AddressRepository addresses, CouponRepository coupons, BasketService baskets, ShippingService shipping,
            PaymentService payments, AppProperties props) {
        this.orders = orders;
        this.products = products;
        this.users = users;
        this.addresses = addresses;
        this.coupons = coupons;
        this.baskets = baskets;
        this.shipping = shipping;
        this.payments = payments;
        this.props = props;
    }

    // ---- pricing ----

    /** Price, tax, delivery, coupon and gift point discount for the current basket. */
    @Transactional(readOnly = true)
    public Quote quote(QuoteRequest req) {
        Long uid = CurrentUser.require().id();
        User user = loadUser(uid);
        Map<Product, Integer> lines = basketLines(uid, false);
        return price(user, lines, req.couponCode(), req.giftPointsToRedeem(), req.state());
    }

    private Quote price(User user, Map<Product, Integer> lines, String couponCode, Integer pointsRequested,
            String state) {
        BigDecimal subtotal = lines.entrySet().stream()
                .map(e -> e.getKey().getPrice().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = coupons.findByCodeIgnoreCase(couponCode.trim()).filter(Coupon::isUsable)
                    .orElseThrow(() -> ApiException.badRequest("Invalid or expired coupon"));
            discount = money(subtotal.multiply(BigDecimal.valueOf(coupon.getPercentOff()))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        }
        BigDecimal taxable = subtotal.subtract(discount);
        BigDecimal tax = money(taxable.multiply(props.taxRate()));
        BigDecimal delivery = lines.isEmpty() ? BigDecimal.ZERO : shipping.charge(subtotal, state);
        BigDecimal beforePoints = taxable.add(tax).add(delivery);

        BigDecimal perPoint = props.giftPoints().valuePerPoint();
        int requested = pointsRequested == null ? 0 : pointsRequested;
        if (requested > user.getGiftPoints()) {
            throw ApiException.badRequest("You only have " + user.getGiftPoints() + " gift points");
        }
        int maxUseful = beforePoints.divide(perPoint, 0, RoundingMode.DOWN).intValue();
        int redeemed = Math.min(requested, maxUseful);
        BigDecimal pointsDiscount = money(perPoint.multiply(BigDecimal.valueOf(redeemed)));
        BigDecimal total = beforePoints.subtract(pointsDiscount);
        int toEarn = money(total.multiply(props.giftPoints().earnRate())).divide(perPoint, 0, RoundingMode.DOWN)
                .intValue();

        LocalDate eta = lines.keySet().stream().map(p -> shipping.deliveryDate(p.getDeliveryDays(), state))
                .max(Comparator.naturalOrder()).orElse(null);
        return new Quote(subtotal, tax, delivery, discount, redeemed, pointsDiscount, total, toEarn, eta);
    }

    // ---- checkout ----

    @Transactional
    public OrderView checkout(CheckoutRequest req) {
        Long uid = CurrentUser.require().id();
        User user = loadUser(uid);
        Basket basket = baskets.forUser(uid);
        Map<Product, Integer> lines = basketLines(uid, true);
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Your basket is empty");
        }
        for (var e : lines.entrySet()) {
            if (!e.getKey().isActive()) {
                throw ApiException.badRequest("'" + e.getKey().getTitle() + "' is no longer available");
            }
            if (e.getValue() > e.getKey().getStock()) {
                throw ApiException.badRequest("Only " + e.getKey().getStock() + " copies of '"
                        + e.getKey().getTitle() + "' are available");
            }
        }

        AddressRequest to = resolveAddress(uid, req);
        Quote q = price(user, lines, req.couponCode(), req.giftPointsToRedeem(), to.state());

        CustomerOrder order = new CustomerOrder();
        order.setUserId(uid);
        order.setStatus(OrderStatus.PLACED);
        order.setSubtotal(q.subtotal());
        order.setDiscount(q.discount());
        order.setTax(q.tax());
        order.setDeliveryCharge(q.deliveryCharge());
        order.setGiftPointsRedeemed(q.giftPointsRedeemed());
        order.setGiftPointsDiscount(q.giftPointsDiscount());
        order.setGiftPointsEarned(q.giftPointsToEarn());
        order.setTotalAmount(q.totalAmount());
        order.setCouponCode(q.discount().signum() > 0 ? req.couponCode().trim().toUpperCase() : null);
        order.setFirstName(to.firstName());
        order.setLastName(to.lastName());
        order.setAddressLine(to.addressLine());
        order.setEmail(to.email());
        order.setPhoneCountryCode(to.phoneCountryCode() == null ? "+91" : to.phoneCountryCode());
        order.setPhone(to.phone());
        order.setCity(to.city());
        order.setPin(to.pin());
        order.setState(to.state());
        order.setCountry(to.country());
        order.setEstimatedDelivery(q.estimatedDelivery());
        Instant now = Instant.now();
        order.setPlacedAt(now);
        order.setCancellableUntil(now.plus(CANCEL_WINDOW));
        for (var e : lines.entrySet()) {
            Product p = e.getKey();
            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setTitle(p.getTitle());
            item.setQuantity(e.getValue());
            item.setUnitPrice(p.getPrice());
            item.setDeliveryDate(shipping.deliveryDate(p.getDeliveryDays(), to.state()));
            order.getItems().add(item);
            p.setStock(p.getStock() - e.getValue());
            p.setCopiesSold(p.getCopiesSold() + e.getValue());
        }
        order = orders.save(order);

        // Declined payments throw 402, which rolls back the order, stock and basket changes.
        Payment payment = payments.charge(user, order.getId(), q.totalAmount(), req.payment());

        user.setGiftPoints(user.getGiftPoints() - q.giftPointsRedeemed() + q.giftPointsToEarn());
        baskets.clear(basket);
        return view(order, payment);
    }

    private AddressRequest resolveAddress(Long uid, CheckoutRequest req) {
        if (req.addressId() != null) {
            Address a = addresses.findByIdAndUserId(req.addressId(), uid)
                    .orElseThrow(() -> ApiException.badRequest("Saved address not found"));
            return new AddressRequest(a.getFirstName(), a.getLastName(), a.getAddressLine(), a.getEmail(),
                    a.getPhoneCountryCode(), a.getPhone(), a.getCity(), a.getPin(), a.getState(), a.getCountry(),
                    a.isDefaultAddress());
        }
        AddressRequest in = req.address();
        if (in == null) {
            throw ApiException.badRequest("Provide addressId or an address");
        }
        if (Boolean.TRUE.equals(req.saveAddress())) {
            Address a = new Address();
            a.setUserId(uid);
            a.setFirstName(in.firstName());
            a.setLastName(in.lastName());
            a.setAddressLine(in.addressLine());
            a.setEmail(in.email());
            a.setPhoneCountryCode(in.phoneCountryCode() == null ? "+91" : in.phoneCountryCode());
            a.setPhone(in.phone());
            a.setCity(in.city());
            a.setPin(in.pin());
            a.setState(in.state());
            a.setCountry(in.country());
            addresses.save(a);
        }
        return in;
    }

    /** Basket lines keyed by product, in basket order. Optionally locks the product rows for update. */
    private Map<Product, Integer> basketLines(Long uid, boolean lock) {
        Basket basket = baskets.forUser(uid);
        List<BasketItem> rows = baskets.itemsOf(basket);
        List<Long> ids = rows.stream().map(BasketItem::getProductId).toList();
        Map<Long, Product> byId = ids.isEmpty() ? Map.of()
                : (lock ? products.lockAllByIdIn(ids) : products.findAllById(ids)).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Product, Integer> lines = new LinkedHashMap<>();
        for (BasketItem r : rows) {
            lines.put(byId.get(r.getProductId()), r.getQuantity());
        }
        return lines;
    }

    // ---- history and lifecycle ----

    @Transactional(readOnly = true)
    public List<OrderView> history() {
        Long uid = CurrentUser.require().id();
        return orders.findByUserIdOrderByPlacedAtDescIdDesc(uid).stream()
                .map(o -> view(o, payments.forOrder(o.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public OrderView get(Long id) {
        CustomerOrder o = own(id);
        return view(o, payments.forOrder(o.getId()));
    }

    /** Allowed for 48 hours after placing, while the order has not shipped. */
    @Transactional
    public OrderView cancel(Long id) {
        CustomerOrder o = own(id);
        if (o.getStatus() != OrderStatus.PLACED) {
            throw ApiException.conflict("Only orders that have not shipped can be cancelled");
        }
        if (!o.isCancellable()) {
            throw ApiException.conflict("The 48 hour cancellation window has passed");
        }
        User user = loadUser(o.getUserId());
        restoreStock(o);
        user.setGiftPoints(Math.max(0, user.getGiftPoints() + o.getGiftPointsRedeemed() - o.getGiftPointsEarned()));
        Payment payment = payments.forOrder(o.getId());
        payments.refund(payment, user);
        o.setStatus(OrderStatus.CANCELLED);
        return view(o, payment);
    }

    /** Simple return: a delivered order is marked and the payment refunded. */
    @Transactional
    public OrderView requestReturn(Long id) {
        CustomerOrder o = own(id);
        if (o.getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.conflict("Only delivered orders can be returned");
        }
        User user = loadUser(o.getUserId());
        restoreStock(o);
        Payment payment = payments.forOrder(o.getId());
        payments.refund(payment, user);
        o.setStatus(OrderStatus.RETURN_REQUESTED);
        return view(o, payment);
    }

    @Transactional
    public BasketView buyAgain(Long id) {
        CustomerOrder o = own(id);
        Map<Long, Integer> qty = new LinkedHashMap<>();
        o.getItems().forEach(i -> qty.merge(i.getProductId(), i.getQuantity(), Integer::sum));
        return baskets.addAvailable(o.getUserId(), qty);
    }

    /** ADMIN: PLACED -> SHIPPED -> DELIVERED. */
    @Transactional
    public OrderView updateStatus(Long id, OrderStatus target) {
        CustomerOrder o = orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        boolean ok = (target == OrderStatus.SHIPPED && o.getStatus() == OrderStatus.PLACED)
                || (target == OrderStatus.DELIVERED && o.getStatus() == OrderStatus.SHIPPED);
        if (!ok) {
            throw ApiException.conflict("Cannot move an order from " + o.getStatus() + " to " + target);
        }
        o.setStatus(target);
        return view(o, payments.forOrder(o.getId()));
    }

    @Transactional
    public CouponView createCoupon(CouponRequest req) {
        String code = req.code().trim().toUpperCase();
        if (coupons.findByCodeIgnoreCase(code).isPresent()) {
            throw ApiException.conflict("Coupon code already exists");
        }
        Coupon c = new Coupon();
        c.setCode(code);
        c.setPercentOff(req.percentOff());
        c.setExpiresAt(req.expiresAt());
        c = coupons.save(c);
        return new CouponView(c.getId(), c.getCode(), c.getPercentOff(), c.isActive(), c.getExpiresAt());
    }

    // ---- helpers ----

    private void restoreStock(CustomerOrder o) {
        List<Long> ids = o.getItems().stream().map(OrderItem::getProductId).distinct().toList();
        Map<Long, Product> byId = products.lockAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (OrderItem i : o.getItems()) {
            Product p = byId.get(i.getProductId());
            p.setStock(p.getStock() + i.getQuantity());
            p.setCopiesSold(Math.max(0, p.getCopiesSold() - i.getQuantity()));
        }
    }

    private CustomerOrder own(Long id) {
        return orders.findByIdAndUserId(id, CurrentUser.require().id())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private User loadUser(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.unauthorized("User not found"));
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private OrderView view(CustomerOrder o, Payment payment) {
        List<OrderItemView> items = new ArrayList<>();
        o.getItems().forEach(i -> items.add(new OrderItemView(i.getProductId(), i.getTitle(), i.getQuantity(),
                i.getUnitPrice(), i.getDeliveryDate())));
        AddressRequest addr = new AddressRequest(o.getFirstName(), o.getLastName(), o.getAddressLine(),
                o.getEmail(), o.getPhoneCountryCode(), o.getPhone(), o.getCity(), o.getPin(), o.getState(),
                o.getCountry(), null);
        String message = o.getStatus() == OrderStatus.CANCELLED ? "This order has been cancelled"
                : o.getStatus() == OrderStatus.RETURN_REQUESTED ? "A return has been requested" : SUCCESS_MESSAGE;
        return new OrderView(o.getId(), o.getStatus(), message, o.getPlacedAt(), o.getCancellableUntil(),
                o.isCancellable(), items, o.getSubtotal(), o.getTax(), o.getDeliveryCharge(), o.getDiscount(),
                o.getCouponCode(), o.getGiftPointsRedeemed(), o.getGiftPointsDiscount(), o.getGiftPointsEarned(),
                o.getTotalAmount(), o.getEstimatedDelivery(), addr, payment == null ? null : PaymentView.of(payment));
    }
}
