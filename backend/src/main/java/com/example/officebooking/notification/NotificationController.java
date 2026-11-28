package com.example.officebooking.notification;

import com.example.officebooking.notification.NotificationService.NotificationsResponse;
import com.example.officebooking.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationsResponse list(@AuthenticationPrincipal UserPrincipal me) {
        return service.list(me.id());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        service.markRead(me.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal UserPrincipal me) {
        service.markAllRead(me.id());
    }
}
