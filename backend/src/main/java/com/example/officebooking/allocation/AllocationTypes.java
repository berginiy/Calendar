package com.example.officebooking.allocation;

/** Перечисления модуля умного распределения. */
public final class AllocationTypes {
    private AllocationTypes() {}

    /** Предпочтение сотрудника на дату. */
    public enum PreferenceType { OFFICE, REMOTE, FLEXIBLE }

    /** Политика разрешения конфликтов, когда желающих в офис больше, чем мест. */
    public enum Policy {
        /** Ротация: приоритет у тех, чьи офисные предпочтения реже удовлетворялись раньше. */
        ROTATION,
        /** По порядку первой подачи предпочтения (обновление предпочтения преимущества не даёт). */
        SUBMISSION_ORDER
    }

    /** DRAFT — предварительный результат; APPLYING — идёт применение; CONFIRMED — подтверждён; DISCARDED — отклонён. */
    public enum RunStatus { DRAFT, APPLYING, CONFIRMED, DISCARDED }

    public enum Outcome { OFFICE, REMOTE, NONE }

    /** AUTO — рассчитано алгоритмом; EXISTING — уже была запись (не меняется); MANUAL — правка руководителя. */
    public enum Source { AUTO, EXISTING, MANUAL }

    public enum ApplyStatus { PENDING, APPLIED, SKIPPED, FAILED }
}
