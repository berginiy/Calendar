-- Этапы B/C: предпочтения, умное распределение, внутренние уведомления.
-- Функция по умолчанию ВЫКЛЮЧЕНА: пока руководитель её не включит, поведение системы не меняется.

ALTER TABLE app_settings
    ADD COLUMN allocation_enabled        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN allocation_policy         VARCHAR(20) NOT NULL DEFAULT 'ROTATION'
        CHECK (allocation_policy IN ('ROTATION', 'SUBMISSION_ORDER')),
    ADD COLUMN preference_deadline_hours INTEGER     NOT NULL DEFAULT 24
        CHECK (preference_deadline_hours >= 0);

-- Предпочтение сотрудника на дату. Строка не удаляется при отзыве (active = false), чтобы повторная подача
-- не сбрасывала first_submitted_at и не давала преимущества в очереди.
CREATE TABLE work_preferences (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL REFERENCES users (id),
    pref_date          DATE        NOT NULL,
    preference         VARCHAR(10) NOT NULL CHECK (preference IN ('OFFICE', 'REMOTE', 'FLEXIBLE')),
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    first_submitted_at TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pref_user_date UNIQUE (user_id, pref_date)
);
CREATE INDEX ix_pref_date_active ON work_preferences (pref_date, active);

CREATE TABLE allocation_runs (
    id              BIGSERIAL PRIMARY KEY,
    range_from      DATE        NOT NULL,
    range_to        DATE        NOT NULL,
    status          VARCHAR(12) NOT NULL CHECK (status IN ('DRAFT', 'APPLYING', 'CONFIRMED', 'DISCARDED')),
    policy          VARCHAR(20) NOT NULL,
    capacity        INTEGER     NOT NULL,
    created_by_id   BIGINT      NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL,
    confirmed_by_id BIGINT REFERENCES users (id),
    confirmed_at    TIMESTAMPTZ
);
CREATE INDEX ix_alloc_runs_status ON allocation_runs (status);

CREATE TABLE allocation_results (
    id             BIGSERIAL PRIMARY KEY,
    run_id         BIGINT      NOT NULL REFERENCES allocation_runs (id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    result_date    DATE        NOT NULL,
    preference     VARCHAR(10) NOT NULL CHECK (preference IN ('OFFICE', 'REMOTE', 'FLEXIBLE')),
    outcome        VARCHAR(10) NOT NULL CHECK (outcome IN ('OFFICE', 'REMOTE', 'NONE')),
    source         VARCHAR(10) NOT NULL CHECK (source IN ('AUTO', 'EXISTING', 'MANUAL')),
    explanation    TEXT        NOT NULL,
    manual_reason  VARCHAR(500),
    apply_status   VARCHAR(10) NOT NULL CHECK (apply_status IN ('PENDING', 'APPLIED', 'SKIPPED', 'FAILED')),
    apply_message  VARCHAR(500),
    CONSTRAINT uq_alloc_result UNIQUE (run_id, user_id, result_date)
);
CREATE INDEX ix_alloc_results_user_date ON allocation_results (user_id, result_date);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users (id),
    type       VARCHAR(40)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    read_at    TIMESTAMPTZ
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
