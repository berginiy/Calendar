package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.ApplyStatus;
import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.Source;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.presence.PresenceService;
import com.example.officebooking.presence.PresenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Применяет один результат распределения в собственной транзакции. Отдельный бин нужен, чтобы сработал
 * прокси @Transactional (вызов из того же класса транзакцию бы не открыл).
 *
 * Каждая запись создаётся через те же проверки, что и обычная (вместимость, лимиты, дубли, блокировки). Отказ по
 * бизнес-правилам не прерывает остальное: результат помечается SKIPPED с причиной. Уже существующие записи
 * этот класс никогда не изменяет.
 */
@Service
public class AllocationApplier {
    private static final Logger log = LoggerFactory.getLogger(AllocationApplier.class);

    private final AllocationResultRepository results;
    private final PresenceService presenceService;

    public AllocationApplier(AllocationResultRepository results, PresenceService presenceService) {
        this.results = results;
        this.presenceService = presenceService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyOne(Long resultId, Long actorId) {
        AllocationResult r = results.findByIdForUpdate(resultId).orElse(null);
        if (r == null || r.getApplyStatus() != ApplyStatus.PENDING) {
            return; // уже обработан (в том числе параллельным подтверждением) — повторно не применяем
        }
        if (r.getOutcome() == Outcome.NONE || r.getSource() == Source.EXISTING) {
            r.setApplyStatus(ApplyStatus.SKIPPED);
            r.setApplyMessage(r.getSource() == Source.EXISTING ? "Запись уже существует" : "Назначение не требуется");
            return;
        }
        PresenceType type = r.getOutcome() == Outcome.OFFICE ? PresenceType.OFFICE : PresenceType.REMOTE;
        try {
            // createForAllocation выполняется в своей транзакции: при отказе откатывается только она
            presenceService.createForAllocation(r.getUser().getId(), r.getResultDate(), type, actorId, r.getRun().getId());
            r.setApplyStatus(ApplyStatus.APPLIED);
            r.setApplyMessage(null);
        } catch (ApiException e) {
            r.setApplyStatus(ApplyStatus.SKIPPED);
            r.setApplyMessage(truncate(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Не удалось применить результат распределения {}", resultId, e);
            r.setApplyStatus(ApplyStatus.FAILED);
            r.setApplyMessage("Техническая ошибка при применении");
        }
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 480 ? s.substring(0, 480) + "…" : s);
    }
}
