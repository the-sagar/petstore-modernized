package com.mdb.petstore.orderprocessing.order.admin.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import com.mdb.petstore.orderprocessing.order.admin.dto.AdminStatisticsResponse;
import com.mdb.petstore.orderprocessing.order.model.Order;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(AdminStatisticsService.class);
    private final MongoTemplate mongo;

    public AdminStatisticsService(MongoTemplate mongo) { this.mongo = mongo; }

    public AdminStatisticsResponse statistics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)
                || startDate.getYear() < 1 || endDate.getYear() > 9999) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide valid start and end dates with start date on or before end date");
        }
        log.info("Admin statistics requested: startDate={}, endDate={}", startDate, endDate);
        var start = Date.from(startDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        var endExclusive = Date.from(endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        // Legacy Admin sales statistics aggregated PurchaseOrder line items by order
        // date without workflow-status filtering. This implementation preserves that behavior for parity.
        // Multiply persisted Decimal128 unitPrice by quantity inside Mongo; never convert money to double.
        var pipeline = Aggregation.newAggregation(
                context -> new Document("$match", new Document("createdAt", new Document("$gte", start).append("$lt", endExclusive))),
                context -> new Document("$unwind", "$lineItems"),
                context -> new Document("$group", new Document("_id", "$lineItems.categoryId")
                        .append("revenue", new Document("$sum", new Document("$multiply", List.of("$lineItems.quantity", "$lineItems.unitPrice"))))
                        .append("unitsSold", new Document("$sum", new Document("$toLong", "$lineItems.quantity")))),
                context -> new Document("$project", new Document("_id", 0).append("categoryId", "$_id")
                        .append("revenue", 1).append("unitsSold", 1)),
                context -> new Document("$sort", new Document("categoryId", 1)));
        var categories = mongo.aggregate(pipeline, Order.class, AdminStatisticsResponse.Category.class).getMappedResults();
        var totalRevenue = categories.stream().map(AdminStatisticsResponse.Category::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalUnits = categories.stream().mapToLong(AdminStatisticsResponse.Category::unitsSold).sum();
        log.info("Admin statistics generated: categories={}, units={}", categories.size(), totalUnits);
        return new AdminStatisticsResponse(startDate, endDate, totalRevenue, totalUnits, categories);
    }
}
