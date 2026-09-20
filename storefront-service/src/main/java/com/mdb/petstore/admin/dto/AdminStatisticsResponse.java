package com.mdb.petstore.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdminStatisticsResponse(LocalDate startDate, LocalDate endDate, BigDecimal totalRevenue,
        long totalUnitsSold, List<Category> categories) {
    /** Presentation-only offset for the revenue donut, expressed in unrounded revenue units. */
    public String revenueSliceOffset(int categoryIndex) {
        return categories.subList(0, categoryIndex).stream().map(Category::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add).negate().toPlainString();
    }

    public record Category(String categoryId, BigDecimal revenue, long unitsSold) {
        public BigDecimal revenuePercentage(BigDecimal total) {
            return total.signum() == 0 ? BigDecimal.ZERO : revenue.multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, java.math.RoundingMode.HALF_UP);
        }
        public BigDecimal unitsPercentage(long total) {
            return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(unitsSold).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
        }
    }
}
