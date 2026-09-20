package com.mdb.petstore.orderprocessing.order.admin.api;

import java.time.LocalDate;
import com.mdb.petstore.orderprocessing.order.admin.dto.AdminStatisticsResponse;
import com.mdb.petstore.orderprocessing.order.admin.service.AdminStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {
    private final AdminStatisticsService service;
    public AdminStatisticsController(AdminStatisticsService service) { this.service = service; }

    @GetMapping
    public AdminStatisticsResponse statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.statistics(startDate, endDate);
    }
}
