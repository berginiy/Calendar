package com.example.officebooking.presence;

import com.example.officebooking.presence.PresenceDtos.DateRequest;
import com.example.officebooking.presence.PresenceDtos.PresenceDto;
import com.example.officebooking.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PresenceController {
    private final PresenceService service;

    public PresenceController(PresenceService service) {
        this.service = service;
    }

    @PostMapping("/bookings")
    public ResponseEntity<PresenceDto> createBooking(@AuthenticationPrincipal UserPrincipal me,
                                                     @Valid @RequestBody DateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.id(), req.date(), PresenceType.OFFICE));
    }

    @GetMapping("/bookings/my")
    public List<PresenceDto> myBookings(@AuthenticationPrincipal UserPrincipal me,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.myRecords(me.id(), PresenceType.OFFICE, from, to);
    }

    @DeleteMapping("/bookings/{id}")
    public PresenceDto cancelBooking(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.cancel(me, id, PresenceType.OFFICE);
    }

    @PostMapping("/remote-days")
    public ResponseEntity<PresenceDto> createRemoteDay(@AuthenticationPrincipal UserPrincipal me,
                                                       @Valid @RequestBody DateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.id(), req.date(), PresenceType.REMOTE));
    }

    @GetMapping("/remote-days/my")
    public List<PresenceDto> myRemoteDays(@AuthenticationPrincipal UserPrincipal me,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.myRecords(me.id(), PresenceType.REMOTE, from, to);
    }

    @DeleteMapping("/remote-days/{id}")
    public PresenceDto cancelRemoteDay(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.cancel(me, id, PresenceType.REMOTE);
    }
}
