package com.mdb.petstore.orderprocessing.order;

import java.math.BigDecimal;
import com.mdb.petstore.orderprocessing.order.service.ApprovalPolicy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovalPolicyTests {
    @ParameterizedTest
    @CsvSource({"en-US,499.99,true", "en-US,500.00,false", "en-US,500.01,false",
            "ja-JP,49999.99,true", "ja-JP,50000,false", "ja-JP,50001,false",
            "zh-CN,1,false", "en-GB,1,false", "en-US,0,true", "en-US,-1,false"})
    void appliesOnlyVerifiedStrictThresholds(String locale, String total, boolean expected) {
        assertEquals(expected, new ApprovalPolicy().shouldAutomaticallyApprove(locale, new BigDecimal(total)));
    }
}
