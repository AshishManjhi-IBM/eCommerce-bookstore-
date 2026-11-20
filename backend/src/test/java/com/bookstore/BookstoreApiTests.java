package com.bookstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bookstore.order.CustomerOrder;
import com.bookstore.order.OrderRepository;
import com.bookstore.payment.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** End to end journey through the API against H2 (PostgreSQL mode) with the real Flyway schema. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookstoreApiTests {

    private static final String VALID_CARD = "4111111111111111";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    OrderRepository orderRepo;
    @Autowired
    PaymentRepository paymentRepo;

    // ---------- helpers ----------

    JsonNode call(MockHttpServletRequestBuilder rb, int expected) throws Exception {
        String body = mvc.perform(rb).andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }

    MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder rb, String body) {
        return rb.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder rb, String token) {
        return token == null ? rb : rb.header("Authorization", "Bearer " + token);
    }

    String registerAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.com";
        String reg = "{\"firstName\":\"Asha\",\"lastName\":\"Rao\",\"email\":\"" + email
                + "\",\"password\":\"Password1\"}";
        call(withBody(post("/api/auth/register"), reg), 201);
        return call(withBody(post("/api/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"Password1\"}"),
                200).get("token").asText();
    }

    String adminToken() throws Exception {
        return call(withBody(post("/api/auth/login"),
                "{\"email\":\"admin@bookstore.com\",\"password\":\"Admin@123\"}"), 200).get("token").asText();
    }

    long productId(String title) throws Exception {
        for (JsonNode p : call(get("/api/products").param("q", title).param("size", "50"), 200).get("content")) {
            if (p.get("title").asText().equals(title)) {
                return p.get("id").asLong();
            }
        }
        throw new AssertionError("Seed book not found: " + title);
    }

    JsonNode addToBasket(String token, String guestToken, long productId, int qty) throws Exception {
        MockHttpServletRequestBuilder rb = auth(
                withBody(post("/api/basket/items"), "{\"productId\":" + productId + ",\"quantity\":" + qty + "}"),
                token);
        if (guestToken != null) {
            rb.header("X-Guest-Token", guestToken);
        }
        return call(rb, 200);
    }

    String checkoutBody(String payment, String extra) {
        return "{\"address\":{\"firstName\":\"Asha\",\"lastName\":\"Rao\",\"addressLine\":\"12 MG Road\","
                + "\"email\":\"asha@test.com\",\"phoneCountryCode\":\"+91\",\"phone\":\"9876543210\","
                + "\"city\":\"Bengaluru\",\"pin\":\"560001\",\"state\":\"Karnataka\",\"country\":\"India\"},"
                + "\"payment\":" + payment + extra + "}";
    }

    String card(String number) {
        return "{\"method\":\"CREDIT_CARD\",\"cardHolder\":\"Asha Rao\",\"cardNumber\":\"" + number
                + "\",\"expiry\":\"12/45\",\"cvv\":\"123\"}";
    }

    // ---------- tests ----------

    @Test
    void guestCanBrowseAndSeesDeliveryDateButNotInactiveBooks() throws Exception {
        JsonNode page = call(get("/api/products").param("sort", "price_asc"), 200);
        assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(10);
        assertThat(page.get("content").get(0).get("deliveryDate").asText()).isNotBlank();

        long dune = productId("Dune");
        JsonNode detail = call(get("/api/products/" + dune), 200);
        assertThat(detail.get("publisher").asText()).isEqualTo("Penguin Books");
        assertThat(detail.get("writer").get("name").asText()).isEqualTo("Frank Herbert");
        assertThat(call(get("/api/products/" + dune + "/related"), 200).isArray()).isTrue();

        assertThat(call(get("/api/categories"), 200)).hasSize(19);
        call(get("/api/home"), 200).get("bestsellers");
    }

    @Test
    void filtersAndSearchWork() throws Exception {
        JsonNode mystery = call(get("/api/products").param("q", "poirot"), 200);
        assertThat(mystery.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode ebooks = call(get("/api/products").param("format", "EBOOK"), 200);
        ebooks.get("content").forEach(p -> assertThat(p.get("format").asText()).isEqualTo("EBOOK"));
        JsonNode cheap = call(get("/api/products").param("maxPrice", "300"), 200);
        cheap.get("content").forEach(p -> assertThat(p.get("price").asDouble()).isLessThanOrEqualTo(300));
    }

    @Test
    void entitlementHidesInactiveProductsFromCustomersButNotAdmin() throws Exception {
        String admin = adminToken();
        JsonNode cat = call(get("/api/categories"), 200).get(0);
        JsonNode brand = call(get("/api/brands"), 200).get(0);
        JsonNode writer = call(auth(withBody(post("/api/writers"), "{\"name\":\"New Writer\",\"bio\":\"bio\"}"),
                admin), 201);
        String body = "{\"title\":\"Hidden Book\",\"language\":\"English\",\"format\":\"PAPERBACK\",\"price\":100,"
                + "\"stock\":5,\"active\":false,\"categoryId\":" + cat.get("id") + ",\"brandId\":" + brand.get("id")
                + ",\"writerId\":" + writer.get("id") + "}";
        JsonNode created = call(auth(withBody(post("/api/products"), body), admin), 201);
        long id = created.get("id").asLong();

        call(get("/api/products/" + id), 404);
        call(auth(get("/api/products/" + id), registerAndLogin()), 404);
        call(auth(get("/api/products/" + id), admin), 200);
        assertThat(call(auth(get("/api/products").param("q", "Hidden Book"), admin), 200).get("totalElements")
                .asInt()).isEqualTo(1);
        assertThat(call(get("/api/products").param("q", "Hidden Book"), 200).get("totalElements").asInt())
                .isZero();
    }

    @Test
    void adminEndpointsRejectCustomersAndAnonymous() throws Exception {
        String customer = registerAndLogin();
        call(auth(withBody(post("/api/stores"), "{\"name\":\"X\"}"), customer), 403);
        call(withBody(post("/api/stores"), "{\"name\":\"X\"}"), 401);
        call(auth(withBody(post("/api/stores"), "{\"name\":\"Second Store\"}"), adminToken()), 201);
        call(auth(withBody(post("/api/coupons"), "{\"code\":\"X\",\"percentOff\":5}"), customer), 403);
    }

    @Test
    void authValidatesAndRejectsBadCredentials() throws Exception {
        call(withBody(post("/api/auth/login"), "{\"email\":\"nobody@test.com\",\"password\":\"Password1\"}"), 401);
        call(withBody(post("/api/auth/register"),
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"not-an-email\",\"password\":\"short\"}"), 400);
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        String reg = "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email + "\",\"password\":\"Password1\"}";
        call(withBody(post("/api/auth/register"), reg), 201);
        call(withBody(post("/api/auth/register"), reg), 409);
        call(get("/api/orders"), 401);
        call(get("/api/members/me").header("Authorization", "Bearer garbage"), 401);
    }

    @Test
    void guestBasketMergesOnLoginAndCheckoutRequiresLogin() throws Exception {
        long dune = productId("Dune");
        JsonNode guestBasket = addToBasket(null, null, dune, 1);
        String guestToken = guestBasket.get("guestToken").asText();
        assertThat(guestToken).isNotBlank();
        addToBasket(null, guestToken, dune, 1);
        assertThat(call(get("/api/basket").header("X-Guest-Token", guestToken), 200).get("itemCount").asInt())
                .isEqualTo(2);

        call(withBody(post("/api/orders"), checkoutBody(card(VALID_CARD), "")), 401);

        String email = "merge-" + UUID.randomUUID() + "@test.com";
        call(withBody(post("/api/auth/register"),
                "{\"firstName\":\"M\",\"lastName\":\"G\",\"email\":\"" + email + "\",\"password\":\"Password1\"}"),
                201);
        String token = call(withBody(post("/api/auth/login"),
                "{\"email\":\"" + email + "\",\"password\":\"Password1\"}").header("X-Guest-Token", guestToken), 200)
                .get("token").asText();
        JsonNode basket = call(auth(get("/api/basket"), token), 200);
        assertThat(basket.get("itemCount").asInt()).isEqualTo(2);
        assertThat(basket.get("guestToken").isNull()).isTrue();
    }

    @Test
    void fullPurchaseJourneyThenCancelWithinWindow() throws Exception {
        String token = registerAndLogin();
        long hobbit = productId("The Hobbit");
        int stockBefore = call(get("/api/products/" + hobbit), 200).get("stock").asInt();

        // quantity +/-
        addToBasket(token, null, hobbit, 1);
        JsonNode updated = call(auth(withBody(put("/api/basket/items/" + hobbit), "{\"quantity\":2}"), token), 200);
        assertThat(updated.get("itemCount").asInt()).isEqualTo(2);
        assertThat(updated.get("subtotal").asDouble()).isEqualTo(1198.0);

        // quote: 1198 + 5% tax, free delivery over threshold
        JsonNode quote = call(auth(withBody(post("/api/checkout/quote"), "{\"state\":\"Karnataka\"}"), token), 200);
        assertThat(quote.get("tax").asDouble()).isEqualTo(59.9);
        assertThat(quote.get("deliveryCharge").asDouble()).isZero();
        assertThat(quote.get("totalAmount").asDouble()).isEqualTo(1257.9);
        assertThat(quote.get("giftPointsToEarn").asInt()).isEqualTo(12);

        // pay
        JsonNode order = call(auth(withBody(post("/api/orders"), checkoutBody(card(VALID_CARD), "")), token), 201);
        long orderId = order.get("id").asLong();
        assertThat(order.get("status").asText()).isEqualTo("PLACED");
        assertThat(order.get("message").asText()).isEqualTo("Your purchase of the following reads is successful");
        assertThat(order.get("cancellable").asBoolean()).isTrue();
        assertThat(order.get("payment").get("cardLast4").asText()).isEqualTo("1111");
        assertThat(order.toString()).doesNotContain(VALID_CARD).doesNotContain("\"cvv\"");
        assertThat(order.get("giftPointsEarned").asInt()).isEqualTo(12);

        // card data is never stored
        var stored = paymentRepo.findByOrderId(orderId).orElseThrow();
        assertThat(stored.getCardLast4()).isEqualTo("1111");
        assertThat(stored.toString() + stored.getTransactionRef()).doesNotContain(VALID_CARD);

        // basket emptied, stock reduced, points earned, history contains the order
        assertThat(call(auth(get("/api/basket"), token), 200).get("itemCount").asInt()).isZero();
        assertThat(call(get("/api/products/" + hobbit), 200).get("stock").asInt()).isEqualTo(stockBefore - 2);
        assertThat(call(auth(get("/api/members/me"), token), 200).get("giftPoints").asInt()).isEqualTo(12);
        assertThat(call(auth(get("/api/orders"), token), 200)).hasSize(1);

        // recommendations exclude the purchased book
        for (JsonNode r : call(auth(get("/api/recommendations"), token), 200)) {
            assertThat(r.get("id").asLong()).isNotEqualTo(hobbit);
        }

        // someone else cannot see or cancel it
        String other = registerAndLogin();
        call(auth(get("/api/orders/" + orderId), other), 404);
        call(auth(post("/api/orders/" + orderId + "/cancel"), other), 404);

        // cancel within 48h: stock, points and payment are reversed
        JsonNode cancelled = call(auth(post("/api/orders/" + orderId + "/cancel"), token), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("payment").get("status").asText()).isEqualTo("REFUNDED");
        assertThat(call(get("/api/products/" + hobbit), 200).get("stock").asInt()).isEqualTo(stockBefore);
        assertThat(call(auth(get("/api/members/me"), token), 200).get("giftPoints").asInt()).isZero();
        call(auth(post("/api/orders/" + orderId + "/cancel"), token), 409);
    }

    @Test
    void cancelIsRejectedAfter48Hours() throws Exception {
        String token = registerAndLogin();
        addToBasket(token, null, productId("Cosmos"), 1);
        long orderId = call(auth(withBody(post("/api/orders"), checkoutBody(card(VALID_CARD), "")), token), 201)
                .get("id").asLong();

        CustomerOrder o = orderRepo.findById(orderId).orElseThrow();
        o.setPlacedAt(Instant.now().minus(49, ChronoUnit.HOURS));
        o.setCancellableUntil(Instant.now().minus(1, ChronoUnit.HOURS));
        orderRepo.save(o);

        call(auth(post("/api/orders/" + orderId + "/cancel"), token), 409);
        assertThat(call(auth(get("/api/orders/" + orderId), token), 200).get("cancellable").asBoolean()).isFalse();
    }

    @Test
    void declinedPaymentCreatesNoOrderAndKeepsBasketAndStock() throws Exception {
        String token = registerAndLogin();
        long id = productId("Emma");
        int stockBefore = call(get("/api/products/" + id), 200).get("stock").asInt();
        addToBasket(token, null, id, 1);

        call(auth(withBody(post("/api/orders"), checkoutBody(card("4000000000000002"), "")), token), 402);
        call(auth(withBody(post("/api/orders"), checkoutBody(card("1234567890123456"), "")), token), 402);

        assertThat(call(auth(get("/api/orders"), token), 200)).isEmpty();
        assertThat(call(auth(get("/api/basket"), token), 200).get("itemCount").asInt()).isEqualTo(1);
        assertThat(call(get("/api/products/" + id), 200).get("stock").asInt()).isEqualTo(stockBefore);
    }

    @Test
    void couponGiftPointsAndWalletAndUpi() throws Exception {
        String admin = adminToken();
        String code = "SAVE" + (int) (Math.random() * 100000);
        call(auth(withBody(post("/api/coupons"), "{\"code\":\"" + code + "\",\"percentOff\":20}"), admin), 201);

        String token = registerAndLogin();
        addToBasket(token, null, productId("Pride and Prejudice"), 2); // 598

        JsonNode quote = call(auth(withBody(post("/api/checkout/quote"),
                "{\"couponCode\":\"" + code + "\",\"state\":\"Karnataka\"}"), token), 200);
        assertThat(quote.get("discount").asDouble()).isEqualTo(119.6);
        call(auth(withBody(post("/api/checkout/quote"), "{\"couponCode\":\"NOPE\"}"), token), 400);
        call(auth(withBody(post("/api/checkout/quote"), "{\"giftPointsToRedeem\":5}"), token), 400);

        // wallet payment needs balance
        String walletPay = "{\"method\":\"WALLET\"}";
        call(auth(withBody(post("/api/orders"), checkoutBody(walletPay, ",\"couponCode\":\"" + code + "\"")),
                token), 402);
        call(auth(withBody(post("/api/members/me/wallet/topup"), "{\"amount\":2000}"), token), 200);
        JsonNode paid = call(auth(withBody(post("/api/orders"),
                checkoutBody(walletPay, ",\"couponCode\":\"" + code + "\"")), token), 201);
        assertThat(paid.get("couponCode").asText()).isEqualTo(code);
        double total = paid.get("totalAmount").asDouble();
        assertThat(call(auth(get("/api/members/me"), token), 200).get("walletBalance").asDouble())
                .isEqualTo(2000 - total);

        // redeem the earned gift points on a UPI order
        int points = call(auth(get("/api/members/me"), token), 200).get("giftPoints").asInt();
        assertThat(points).isGreaterThan(0);
        addToBasket(token, null, productId("Atomic Habits"), 1);
        JsonNode upi = call(auth(withBody(post("/api/orders"), checkoutBody(
                "{\"method\":\"UPI\",\"upiId\":\"asha@okbank\"}", ",\"giftPointsToRedeem\":" + points)), token), 201);
        assertThat(upi.get("giftPointsRedeemed").asInt()).isEqualTo(points);
        assertThat(upi.get("giftPointsDiscount").asDouble()).isEqualTo((double) points);
        assertThat(upi.get("payment").get("method").asText()).isEqualTo("UPI");

        // cancelling the wallet order refunds the wallet
        double before = call(auth(get("/api/members/me"), token), 200).get("walletBalance").asDouble();
        call(auth(post("/api/orders/" + paid.get("id").asLong() + "/cancel"), token), 200);
        assertThat(call(auth(get("/api/members/me"), token), 200).get("walletBalance").asDouble())
                .isEqualTo(before + total);
    }

    @Test
    void savedAddressBuyAgainAndReviews() throws Exception {
        String token = registerAndLogin();
        JsonNode addr = call(auth(withBody(post("/api/addresses"),
                "{\"firstName\":\"Asha\",\"lastName\":\"Rao\",\"addressLine\":\"1 Lake View\","
                        + "\"email\":\"a@test.com\",\"phone\":\"9000000000\",\"city\":\"Pune\",\"pin\":\"411001\","
                        + "\"state\":\"Maharashtra\",\"country\":\"India\",\"isDefault\":true}"),
                token), 201);
        assertThat(addr.get("phoneCountryCode").asText()).isEqualTo("+91");
        long book = productId("And Then There Were None");
        addToBasket(token, null, book, 1);
        JsonNode order = call(auth(withBody(post("/api/orders"), "{\"addressId\":" + addr.get("id")
                + ",\"payment\":" + card(VALID_CARD) + "}"), token), 201);
        assertThat(order.get("shippingAddress").get("city").asText()).isEqualTo("Pune");

        JsonNode again = call(auth(post("/api/orders/" + order.get("id").asLong() + "/buy-again"), token), 200);
        assertThat(again.get("itemCount").asInt()).isEqualTo(1);

        // reviews: rating bounds and 100 character limit, average feeds the product
        String url = "/api/products/" + book + "/reviews";
        call(auth(withBody(post(url), "{\"rating\":6}"), token), 400);
        call(auth(withBody(post(url), "{\"rating\":4,\"comment\":\"" + "x".repeat(101) + "\"}"), token), 400);
        call(auth(withBody(post(url), "{\"rating\":4,\"comment\":\"Great read\"}"), token), 201);
        call(auth(withBody(post(url), "{\"rating\":2,\"comment\":\"Changed my mind\"}"), registerAndLogin()), 201);
        JsonNode detail = call(get("/api/products/" + book), 200);
        assertThat(detail.get("ratingCount").asInt()).isEqualTo(2);
        assertThat(detail.get("rating").asDouble()).isEqualTo(3.0);
        assertThat(call(get(url), 200)).hasSize(2);
        call(withBody(post(url), "{\"rating\":5}"), 401);
    }

    @Test
    void wishlistIsPerUserAndIdempotent() throws Exception {
        String token = registerAndLogin();
        long id = productId("Cosmos");
        call(auth(post("/api/wishlist/" + id), token), 204);
        call(auth(post("/api/wishlist/" + id), token), 204);
        assertThat(call(auth(get("/api/wishlist"), token), 200)).hasSize(1);
        assertThat(call(auth(get("/api/wishlist"), registerAndLogin()), 200)).isEmpty();
        call(auth(delete("/api/wishlist/" + id), token), 204);
        assertThat(call(auth(get("/api/wishlist"), token), 200)).isEmpty();
        call(get("/api/wishlist"), 401);
    }

    @Test
    void stockAndEmptyBasketAreEnforced() throws Exception {
        String token = registerAndLogin();
        call(auth(withBody(post("/api/orders"), checkoutBody(card(VALID_CARD), "")), token), 400);
        addToBasketExpecting(token, productId("Cosmos"), 100000, 400);
        call(auth(withBody(post("/api/basket/items"), "{\"productId\":99999}"), token), 404);
    }

    void addToBasketExpecting(String token, long productId, int qty, int expected) throws Exception {
        call(auth(withBody(post("/api/basket/items"), "{\"productId\":" + productId + ",\"quantity\":" + qty + "}"),
                token), expected);
    }

    @Test
    void adminMovesOrderThroughShippingAndCustomerCanReturn() throws Exception {
        String token = registerAndLogin();
        addToBasket(token, null, productId("Dune"), 1);
        long orderId = call(auth(withBody(post("/api/orders"), checkoutBody(card(VALID_CARD), "")), token), 201)
                .get("id").asLong();
        String admin = adminToken();

        call(auth(post("/api/orders/" + orderId + "/return"), token), 409);
        call(auth(withBody(put("/api/orders/" + orderId + "/status"), "{\"status\":\"DELIVERED\"}"), admin), 409);
        call(auth(withBody(put("/api/orders/" + orderId + "/status"), "{\"status\":\"SHIPPED\"}"), token), 403);
        call(auth(withBody(put("/api/orders/" + orderId + "/status"), "{\"status\":\"SHIPPED\"}"), admin), 200);

        // shipped orders can no longer be cancelled or returned
        call(auth(post("/api/orders/" + orderId + "/cancel"), token), 409);
        call(auth(post("/api/orders/" + orderId + "/return"), token), 409);

        call(auth(withBody(put("/api/orders/" + orderId + "/status"), "{\"status\":\"DELIVERED\"}"), admin), 200);
        JsonNode returned = call(auth(post("/api/orders/" + orderId + "/return"), token), 200);
        assertThat(returned.get("status").asText()).isEqualTo("RETURN_REQUESTED");
        assertThat(returned.get("payment").get("status").asText()).isEqualTo("REFUNDED");
    }

    @Test
    void shippingEstimateUsesProductDaysThenRegionFallback() throws Exception {
        long withDays = productId("Emma"); // 4 days on the product
        long noDays = productId("Dune"); // falls back to the region
        String emma = call(get("/api/shipping/estimate").param("productId", "" + withDays).param("state", "Karnataka"),
                200).get("deliveryDate").asText();
        String dune = call(get("/api/shipping/estimate").param("productId", "" + noDays).param("state", "Karnataka"),
                200).get("deliveryDate").asText();
        assertThat(emma).isEqualTo(java.time.LocalDate.now().plusDays(4).toString());
        assertThat(dune).isEqualTo(java.time.LocalDate.now().plusDays(2).toString());
        assertThat(call(get("/api/shipping/rates"), 200).size()).isGreaterThanOrEqualTo(2);
    }
}
