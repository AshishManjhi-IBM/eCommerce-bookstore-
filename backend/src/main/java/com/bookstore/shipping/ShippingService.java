package com.bookstore.shipping;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.bookstore.common.AppProperties;

/** Shipping service: flat rate per region, delivery date = today + days. */
@Service
public class ShippingService {

    static final String DEFAULT_REGION = "DEFAULT";

    private final ShippingRateRepository rates;
    private final AppProperties props;

    public ShippingService(ShippingRateRepository rates, AppProperties props) {
        this.rates = rates;
        this.props = props;
    }

    /** Rate for the state, falling back to the DEFAULT region. */
    public ShippingRate rateFor(String state) {
        if (state != null && !state.isBlank()) {
            var match = rates.findByRegionIgnoreCase(state.trim());
            if (match.isPresent()) {
                return match.get();
            }
        }
        return rates.findByRegionIgnoreCase(DEFAULT_REGION)
                .orElseThrow(() -> new IllegalStateException("DEFAULT shipping rate is missing"));
    }

    /** Product delivery days win; the region value is the fallback. */
    public LocalDate deliveryDate(Integer productDeliveryDays, String state) {
        int days = productDeliveryDays != null ? productDeliveryDays : rateFor(state).getDeliveryDays();
        return LocalDate.now().plusDays(days);
    }

    /** Zero (shown as "Free") when the subtotal reaches the free-shipping threshold. */
    public BigDecimal charge(BigDecimal subtotal, String state) {
        BigDecimal threshold = props.shipping().freeThreshold();
        if (threshold != null && threshold.signum() > 0 && subtotal.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return rateFor(state).getCharge();
    }
}
