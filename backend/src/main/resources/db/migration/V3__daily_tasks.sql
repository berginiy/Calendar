-- Этап A: ежедневные задачи (Daily To-Do)
CREATE TABLE daily_tasks (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    assignee_id   BIGINT       NOT NULL REFERENCES users (id),
    created_by_id BIGINT       NOT NULL REFERENCES users (id),
    task_date     DATE         NOT NULL,
    due_time      TIME,
    priority      VARCHAR(10)  NOT NULL CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    status        VARCHAR(12)  NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_tasks_assignee_date ON daily_tasks (assignee_id, task_date);
CREATE INDEX ix_tasks_date_status ON daily_tasks (task_date, status);
