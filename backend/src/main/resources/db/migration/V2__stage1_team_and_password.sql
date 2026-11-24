-- Этап 1 дополнения к ТЗ.
-- 1) Принудительная смена временного пароля (создание сотрудника / сброс пароля менеджером).
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Видимость командного календаря для сотрудников. По умолчанию выключено:
--    основное ТЗ запрещает показывать сотрудникам персональные данные коллег.
ALTER TABLE app_settings ADD COLUMN team_visible_to_employees BOOLEAN NOT NULL DEFAULT FALSE;
