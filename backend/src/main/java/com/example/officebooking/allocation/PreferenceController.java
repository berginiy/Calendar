package com.example.officebooking.allocation;

import com.example.officebooking.allocation.PreferenceService.*;
import com.example.officebooking.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Предпочтения текущего пользователя: чужие предпочтения недоступны ни для чтения, ни для изменения. */
@RestController
@RequestMapping("/api/work-preferences")
public class PreferenceController {
    private static final String DATE = "yyyy-MM-dd";
    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public PreferencesResponse list(@AuthenticationPrincipal UserPrincipal me,
                                    @RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to) {
        return service.list(me.id(), from, to);
    }

    @PostMapping
    public ResponseEntity<PreferenceDay> create(@AuthenticationPrincipal UserPrincipal me,
                                                @Valid @RequestBody PreferenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(me.id(), request.date(), request.preference()));
    }

    @PostMapping("/bulk")
    public List<PreferenceDay> bulk(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody BulkRequest request) {
        return service.bulk(me.id(), request.items());
    }

    @PutMapping("/{id}")
    public PreferenceDay update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                @Valid @RequestBody UpdateRequest request) {
        return service.update(me.id(), id, request.preference());
    }

    @DeleteMapping("/{id}")
    public PreferenceDay withdraw(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.withdraw(me.id(), id);
    }
}
