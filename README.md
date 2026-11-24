# Сервис бронирования рабочих мест и учёта удалённой работы

Full-stack приложение: Java 17 / Spring Boot 3 / Spring Security (JWT) / Spring Data JPA / Flyway / PostgreSQL + React / TypeScript / Vite / Ant Design / TanStack Query.

Офис: 10 мест на 15 сотрудников. Сотрудник на каждую дату выбирает **офис** (занимает место) или **удалённо**. Руководитель видит загрузку, отчёты и управляет лимитами.

## Структура
```
backend/    Spring Boot (Maven), миграции в src/main/resources/db/migration
frontend/   React + TS (Vite)
docker-compose.yml   локальный PostgreSQL
.env.example         переменные окружения
```

## Быстрый старт

Требуется: JDK 17+, Maven 3.9+, Node 20+, Docker (для БД и интеграционных тестов).

```bash
# 1. Конфигурация
cp .env.example .env            # задайте DB_PASSWORD, JWT_SECRET, BOOTSTRAP_MANAGER_*
set -a; source .env; set +a     # переменные нужны backend-у (Windows: задайте их в IDE/терминале)

# 2. База данных
docker compose up -d

# 3. Backend (http://localhost:8080)
cd backend && mvn spring-boot:run

# 4. Frontend (http://localhost:5173), в другом терминале
cd frontend && npm install && npm run dev
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Первая учётная запись руководителя
При старте, если в БД нет ни одного `MANAGER`, он создаётся из `BOOTSTRAP_MANAGER_EMAIL` / `BOOTSTRAP_MANAGER_PASSWORD` (пароль ≥ 8 символов, хранится как BCrypt). После входа смените значения в `.env` на любые — повторно пользователь не создаётся.
Публичной регистрации нет. Для разработки `APP_DEMO_DATA=true` + `APP_DEMO_PASSWORD=...` создаёт 15 сотрудников `employee01@example.com … employee15@example.com`.

### Миграции БД
Схемой управляет только Flyway (`ddl-auto=validate`). Миграции применяются автоматически при старте. Новые изменения — новые файлы `V2__*.sql`, существующие не редактировать.

## Тесты и сборка
```bash
cd backend  && mvn test            # unit-тесты без Docker + интеграционные на PostgreSQL (Testcontainers, нужен Docker)
cd backend  && mvn package         # jar: target/office-booking-1.0.0.jar
cd frontend && npm test            # Vitest
cd frontend && npm run lint && npm run build
```
Для production: `java -jar` с теми же переменными окружения; статику `frontend/dist` отдаёт любой веб-сервер с проксированием `/api` на backend (или задайте `VITE_API_URL` при сборке и `CORS_ALLOWED_ORIGINS` на backend).

## Принятые архитектурные решения

**Одна таблица `presence_records` вместо двух (Booking/RemoteDay).** ТЗ разрешает общую таблицу. Тип записи — `OFFICE` или `REMOTE`. Частичный уникальный индекс `(user_id, presence_date) WHERE status='ACTIVE'` на уровне БД гарантирует «одна активная запись на пользователя и дату независимо от типа», то есть OFFICE и REMOTE взаимоисключающи. API при этом сохранён по ТЗ: `/api/bookings` и `/api/remote-days`.

**Защита от гонок (PostgreSQL, READ COMMITTED).** Все проверки и вставка идут в одной транзакции:
1. `pg_advisory_xact_lock(пользователь)` — сериализует запросы одного сотрудника (лимиты недели/месяца, дубликаты);
2. `FOR SHARE` на строке настроек — смена вместимости ждёт идущие бронирования, а новые ждут её;
3. для OFFICE — `pg_advisory_xact_lock(дата)` — сериализует бронирования на дату, поэтому `COUNT(*)` вместимости не устаревает между проверкой и вставкой;
4. уникальный индекс — последняя линия защиты (конфликт превращается в 409).
Порядок блокировок всегда один (пользователь → настройки → дата), взаимных блокировок нет. Блокировки снимаются автоматически в конце транзакции.

**Даты и время.** Все «сегодня/неделя/месяц» считаются по часам приложения в `Asia/Bishkek`. Неделя — пн–вс. Лимит считается по неделе/месяцу **выбранной даты**, а не только текущей. Записи в БД хранятся с `timestamptz` (UTC).

**Прочее**
- Нерабочие дни (по умолчанию сб–вс) запрещены и для бронирования, и для удалённых дней.
- Запись на сегодня — только при `allowSameDayBooking`.
- Срок отмены: крайний момент = начало выбранного дня минус `cancellationDeadlineHours`. При `0` — отмена до начала дня; в сам день отмена уже невозможна.
- Руководитель может отменить чужую запись без учёта срока отмены; действие пишется в аудит (`ADMIN_CANCELLED_*`). Свои записи он отменяет по общим правилам.
- Повторная отмена → `409 ALREADY_CANCELLED`. Чужая запись у сотрудника → `403`.
- Уменьшение вместимости ниже числа уже оформленных бронирований на любую будущую дату → `409`, ничего не отменяется автоматически.
- «Всего сотрудников» на дашборде = все включённые пользователи (включая руководителя).
- Токен хранится в `localStorage` (простота; риск при XSS). Срок жизни — `JWT_EXPIRATION_MINUTES`; refresh-токенов нет.
- Часовой пояс приложения задаётся `APP_TIMEZONE`; поле `timezone` в настройках только для чтения.

## API (префикс `/api`, подробности — в Swagger)
| Метод | Путь | Доступ |
|---|---|---|
| POST | `/auth/login`, GET `/auth/me` | все / авторизованные |
| GET | `/calendar?from&to` | авторизованные |
| POST/GET/DELETE | `/bookings`, `/bookings/my`, `/bookings/{id}` | авторизованные |
| POST/GET/DELETE | `/remote-days`, `/remote-days/my`, `/remote-days/{id}` | авторизованные |
| GET | `/my-stats?from&to` | авторизованные |
| GET | `/manager/dashboard`, `/manager/occupancy`, `/manager/users` | MANAGER |
| GET | `/manager/reports/remote-days`, `/bookings`, `/summary`, `/manager/audit` | MANAGER |
| GET/PUT | `/manager/settings`, `/manager/limit-rules` | MANAGER |

Ошибки: `{ "code", "message", "details", "timestamp" }`. Бизнес-конфликты — `409` (`NO_FREE_SEATS`, `DUPLICATE`, `ALREADY_HAS_OTHER_STATUS`, `REMOTE_LIMIT_EXCEEDED`, `DATE_NOT_AVAILABLE`, `CANCELLATION_NOT_ALLOWED`, `ALREADY_CANCELLED`, `CAPACITY_CONFLICT`).

## Известные ограничения
- Сущность Department не реализована (опциональна по ТЗ), фильтра по отделам нет.
- Экспорт отчётов в CSV не реализован.
- Нет ограничения частоты попыток входа (rate limiting) и смены пароля через интерфейс; пользователей создаёт только bootstrap/демо-режим (интерфейса управления пользователями нет).
- Frontend-бандл не разбит на чанки (≈1.4 МБ до gzip).
- Dockerfile для backend/frontend не добавлены.
