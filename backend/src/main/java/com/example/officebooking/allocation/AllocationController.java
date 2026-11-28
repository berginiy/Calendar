package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationDtos.*;
import com.example.officebooking.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Только для MANAGER (защищено маской /api/manager/** в SecurityConfig). */
@RestController
@RequestMapping("/api/manager/allocation")
public class AllocationController {
    private static final String DATE = "yyyy-MM-dd";
    private final AllocationService service;

    public AllocationController(AllocationService service) {
        this.service = service;
    }

    @GetMapping
    public Overview overview(@RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to) {
        return service.overview(from, to);
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    public SettingsDto updateSettings(@AuthenticationPrincipal UserPrincipal me,
                                      @Valid @RequestBody SettingsRequest request) {
        return service.updateSettings(me.id(), request);
    }

    @GetMapping("/runs")
    public List<RunDto> runs() {
        return service.runs();
    }

    @PostMapping("/run")
    public ResponseEntity<RunDetail> run(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody RunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.run(me.id(), request.from(), request.to()));
    }

    @GetMapping("/{runId}")
    public RunDetail get(@PathVariable Long runId) {
        return service.get(runId);
    }

    @PutMapping("/{runId}/results/{resultId}")
    public ResultDto adjust(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long runId,
                            @PathVariable Long resultId, @Valid @RequestBody AdjustRequest request) {
        return service.adjust(me.id(), runId, resultId, request.outcome(), request.reason());
    }

    @PostMapping("/{runId}/confirm")
    public RunDetail confirm(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long runId) {
        return service.confirm(me.id(), runId);
    }

    @PostMapping("/{runId}/discard")
    public RunDto discard(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long runId) {
        return service.discard(me.id(), runId);
    }

    @GetMapping("/{runId}/export.csv")
    public ResponseEntity<byte[]> export(@PathVariable Long runId) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"allocation-" + runId + ".csv\"")
                .body(service.csv(runId));
    }
}
