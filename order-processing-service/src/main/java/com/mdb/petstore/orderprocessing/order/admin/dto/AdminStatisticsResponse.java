package com.mdb.petstore.orderprocessing.order.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminStatisticsResponse(LocalDate startDate, LocalDate endDate, BigDecimal totalRevenue,
        long totalUnitsSold, List<Category> categories) {
    public record Category(String categoryId, BigDecimal revenue, long unitsSold) {}
}
