package com.mdb.petstore.orderprocessing.order.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ApprovalPolicy {
    private static final BigDecimal EN_US_LIMIT = new BigDecimal("500");
    private static final BigDecimal JA_JP_LIMIT = new BigDecimal("50000");

    public boolean shouldAutomaticallyApprove(String locale, BigDecimal total) {
        if (total == null || total.signum() < 0) {
            return false;
        }
        if ("en-US".equals(locale)) {
            return total.compareTo(EN_US_LIMIT) < 0;
        }
        if ("ja-JP".equals(locale)) {
            return total.compareTo(JA_JP_LIMIT) < 0;
        }
        return false;
    }
}
