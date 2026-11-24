package com.example.officebooking.analytics;

import com.example.officebooking.analytics.AnalyticsCalculator.Result;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Только для MANAGER (защищено маской /api/manager/** в SecurityConfig). */
@RestController
@RequestMapping("/api/manager/analytics")
public class AnalyticsController {
    private static final String DATE = "yyyy-MM-dd";
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public Result analytics(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to) {
        return service.analytics(from, to);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"officeflow-analytics-" + from + "_" + to + ".csv\"")
                .body(service.csv(from, to));
    }
}
