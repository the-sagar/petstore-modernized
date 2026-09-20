package com.mdb.petstore.admin.api;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import com.mdb.petstore.admin.client.AdminOrderClient;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class AdminStatisticsPageController {
    private final AdminOrderClient client;
    public AdminStatisticsPageController(AdminOrderClient client) { this.client = client; }

    @GetMapping("/admin/statistics")
    public String statistics(@RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate, Model model, HttpServletResponse response) {
        if (startDate == null && endDate == null) {
            var today = LocalDate.now(ZoneOffset.UTC);
            startDate = today.minusDays(29).toString();
            endDate = today.toString();
        }
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        try {
            if (startDate == null || endDate == null || !startDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    || !endDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                throw new IllegalArgumentException();
            model.addAttribute("statistics", client.statistics(LocalDate.parse(startDate), LocalDate.parse(endDate)));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            response.setStatus(400);
            model.addAttribute("error", "Enter both dates as valid YYYY-MM-DD dates.");
        } catch (ResponseStatusException exception) {
            response.setStatus(exception.getStatusCode().value());
            model.addAttribute("error", exception.getStatusCode().value() == 400
                    ? "Start date must be on or before end date, using years 0001–9999."
                    : "Statistics are temporarily unavailable. Please try again.");
        }
        return "admin-statistics";
    }
}
