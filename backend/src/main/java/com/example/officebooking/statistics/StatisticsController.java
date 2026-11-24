package com.example.officebooking.statistics;

import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.statistics.StatisticsService.MyStats;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatisticsController {
    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    /** Если период не указан, история строится за текущий календарный месяц. */
    @GetMapping("/api/my-stats")
    public MyStats myStats(@AuthenticationPrincipal UserPrincipal me,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.myStats(me.id(), from, to);
    }
}
