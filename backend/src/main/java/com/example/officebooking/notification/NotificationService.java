package com.example.officebooking.notification;

import com.example.officebooking.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Внутренние уведомления. Пользователь видит и отмечает прочитанными только свои. */
@Service
public class NotificationService {
    public record NotificationDto(Long id, String type, String title, String message, Instant createdAt, boolean read) {}

    public record NotificationsResponse(long unread, List<NotificationDto> items) {}

    private final NotificationRepository repo;
    private final Clock clock;

    public NotificationService(NotificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** Создаёт уведомление в текущей транзакции (откатится вместе с бизнес-операцией). */
    @Transactional
    public void notify(Long userId, String type, String title, String message) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title.length() > 200 ? title.substring(0, 199) + "…" : title);
        n.setMessage(message.length() > 1000 ? message.substring(0, 999) + "…" : message);
        n.setCreatedAt(Instant.now(clock));
        repo.save(n);
    }

    @Transactional(readOnly = true)
    public NotificationsResponse list(Long userId) {
        List<NotificationDto> items = repo.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(n -> new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getCreatedAt(),
                        n.getReadAt() != null))
                .toList();
        return new NotificationsResponse(repo.countByUserIdAndReadAtIsNull(userId), items);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        if (!repo.existsByIdAndUserId(id, userId)) {
            throw ApiException.notFound("Уведомление не найдено");
        }
        repo.markRead(id, userId, Instant.now(clock));
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId, Instant.now(clock));
    }
}
