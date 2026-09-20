package com.mdb.petstore.admin.api;

import java.time.LocalDate;
import com.mdb.petstore.admin.client.AdminOrderClient;
import com.mdb.petstore.admin.dto.AdminStatisticsResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {
    private final AdminOrderClient client;
    public AdminStatisticsController(AdminOrderClient client) { this.client = client; }

    @GetMapping
    public AdminStatisticsResponse statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return client.statistics(startDate, endDate);
    }
}
