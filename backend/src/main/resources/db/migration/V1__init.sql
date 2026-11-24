-- Пользователи
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('EMPLOYEE', 'MANAGER')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- Единая таблица присутствия: OFFICE (бронирование места) и REMOTE (удалённый день).
-- Один источник правды позволяет гарантировать «не более одной активной записи
-- пользователя на дату» частичным уникальным индексом на уровне БД.
CREATE TABLE presence_records (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users (id),
    presence_date         DATE        NOT NULL,
    type                  VARCHAR(10) NOT NULL CHECK (type IN ('OFFICE', 'REMOTE')),
    status                VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at            TIMESTAMPTZ NOT NULL,
    cancelled_at          TIMESTAMPTZ,
    cancelled_by_user_id  BIGINT REFERENCES users (id)
);
CREATE UNIQUE INDEX uq_presence_active_user_date
    ON presence_records (user_id, presence_date) WHERE status = 'ACTIVE';
CREATE INDEX ix_presence_date_type_status ON presence_records (presence_date, type, status);
CREATE INDEX ix_presence_user_date ON presence_records (user_id, presence_date);
CREATE INDEX ix_presence_status ON presence_records (status);

-- Настройки приложения (единственная строка, id = 1)
CREATE TABLE app_settings (
    id                           INTEGER PRIMARY KEY CHECK (id = 1),
    office_capacity              INTEGER      NOT NULL CHECK (office_capacity > 0),
    allow_same_day_booking       BOOLEAN      NOT NULL,
    cancellation_deadline_hours  INTEGER      NOT NULL CHECK (cancellation_deadline_hours >= 0),
    working_days                 VARCHAR(100) NOT NULL,
    timezone                     VARCHAR(64)  NOT NULL,
    updated_at                   TIMESTAMPTZ  NOT NULL,
    updated_by_user_id           BIGINT REFERENCES users (id)
);

-- Правила лимитов удалённой работы: по одному на тип периода
CREATE TABLE limit_rules (
    id                  BIGSERIAL PRIMARY KEY,
    period_type         VARCHAR(10) NOT NULL UNIQUE CHECK (period_type IN ('WEEKLY', 'MONTHLY')),
    max_days            INTEGER     NOT NULL CHECK (max_days >= 0),
    enabled             BOOLEAN     NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    updated_by_user_id  BIGINT REFERENCES users (id)
);

-- Аудит
CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT REFERENCES users (id),
    action         VARCHAR(50)  NOT NULL,
    entity_type    VARCHAR(50)  NOT NULL,
    entity_id      VARCHAR(50),
    details        TEXT,
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_audit_created_at ON audit_log (created_at);
CREATE INDEX ix_audit_actor ON audit_log (actor_user_id);
