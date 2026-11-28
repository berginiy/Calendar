package com.example.officebooking.task;

import com.example.officebooking.audit.AuditService;
import com.example.officebooking.common.ApiException;
import com.example.officebooking.common.PageResponse;
import com.example.officebooking.common.PeriodValidator;
import com.example.officebooking.notification.NotificationService;
import com.example.officebooking.task.TaskDtos.*;
import com.example.officebooking.user.User;
import com.example.officebooking.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ежедневные задачи.
 * Права: сотрудник работает только с задачами, где он исполнитель. Менять статус он может у любой своей задачи,
 * а редактировать и удалять — только созданные им самим (задачи, назначенные руководителем, он не переписывает).
 * Руководитель управляет задачами любых сотрудников через /api/manager/tasks; эти действия пишутся в аудит.
 */
@Service
public class TaskService {
    private static final LocalDate MIN_DATE = LocalDate.of(2020, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DailyTaskRepository repo;
    private final UserRepository users;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public TaskService(DailyTaskRepository repo, UserRepository users, AuditService audit,
                       NotificationService notifications, Clock clock) {
        this.repo = repo;
        this.users = users;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------- личные задачи ----------------

    @Transactional(readOnly = true)
    public DayTasksResponse day(Long userId, LocalDate date) {
        List<TaskDto> tasks = repo.findForUserOnDate(userId, date).stream().map(t -> toDto(t, userId, false)).toList();
        return new DayTasksResponse(date, progressOf(tasks), tasks);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> range(Long userId, LocalDate from, LocalDate to) {
        PeriodValidator.validate(from, to);
        return repo.findForUserInRange(userId, from, to).stream().map(t -> toDto(t, userId, false)).toList();
    }

    @Transactional
    public TaskDto create(Long userId, TaskRequest req) {
        validateDate(req.taskDate());
        User me = findUser(userId);
        DailyTask t = new DailyTask();
        t.setAssignee(me);
        t.setCreatedBy(me);
        fill(t, req.title(), req.description(), req.taskDate(), req.dueTime(), req.priority());
        t.setStatus(TaskStatus.TODO);
        t.setCreatedAt(Instant.now(clock));
        t.setUpdatedAt(t.getCreatedAt());
        repo.save(t);
        return toDto(t, userId, false);
    }

    @Transactional
    public TaskDto update(Long userId, Long id, TaskRequest req) {
        DailyTask t = ownTask(userId, id);
        if (!t.getCreatedBy().getId().equals(userId)) {
            throw ApiException.forbidden("Задачу, назначенную руководителем, можно только отмечать выполненной");
        }
        validateDate(req.taskDate());
        fill(t, req.title(), req.description(), req.taskDate(), req.dueTime(), req.priority());
        t.setUpdatedAt(Instant.now(clock));
        return toDto(t, userId, false);
    }

    @Transactional
    public TaskDto changeStatus(Long userId, Long id, TaskStatus status) {
        DailyTask t = ownTask(userId, id);
        t.setStatus(status);
        t.setUpdatedAt(Instant.now(clock));
        return toDto(t, userId, false);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        DailyTask t = ownTask(userId, id);
        if (!t.getCreatedBy().getId().equals(userId)) {
            throw ApiException.forbidden("Задачу, назначенную руководителем, удалить нельзя");
        }
        repo.delete(t);
    }

    // ---------------- управление руководителем ----------------

    /** Фильтры: либо date, либо from/to (если ничего не задано — сегодня). */
    @Transactional(readOnly = true)
    public ManagerTasksResponse managerList(LocalDate date, LocalDate from, LocalDate to, Long assigneeId,
                                            TaskStatus status, TaskPriority priority, int page, int size) {
        LocalDate f = date != null ? date : from;
        LocalDate t = date != null ? date : to;
        if (f == null && t == null) {
            f = t = LocalDate.now(clock);
        }
        PeriodValidator.validate(f, t);
        final LocalDate fromD = f;
        final LocalDate toD = t;

        Specification<DailyTask> base = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("assignee");
                root.fetch("createdBy");
            }
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.between(root.get("taskDate"), fromD, toD));
            if (assigneeId != null) ps.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            if (priority != null) ps.add(cb.equal(root.get("priority"), priority));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        // прогресс считается по всем задачам с учётом фильтров, но без фильтра по статусу
        Progress progress = Progress.of(
                repo.count(base.and(statusIs(TaskStatus.TODO))),
                repo.count(base.and(statusIs(TaskStatus.IN_PROGRESS))),
                repo.count(base.and(statusIs(TaskStatus.DONE))));

        Specification<DailyTask> listSpec = status == null ? base : base.and(statusIs(status));
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by("taskDate", "dueTime", "id"));
        var result = repo.findAll(listSpec, pageable).map(x -> toDto(x, null, true));
        return new ManagerTasksResponse(progress, PageResponse.of(result));
    }

    @Transactional
    public TaskDto managerCreate(Long actorId, ManagerTaskRequest req) {
        validateDate(req.taskDate());
        User assignee = activeAssignee(req.assigneeId());
        DailyTask t = new DailyTask();
        t.setAssignee(assignee);
        t.setCreatedBy(findUser(actorId));
        fill(t, req.title(), req.description(), req.taskDate(), req.dueTime(), req.priority());
        t.setStatus(req.status() == null ? TaskStatus.TODO : req.status());
        t.setCreatedAt(Instant.now(clock));
        t.setUpdatedAt(t.getCreatedAt());
        repo.save(t);
        audit.log(actorId, "TASK_CREATED_BY_MANAGER", "DailyTask", t.getId(), describe(t));
        notifyAssignee(actorId, t, "Новая задача");
        return toDto(t, null, true);
    }

    @Transactional
    public TaskDto managerUpdate(Long actorId, Long id, ManagerTaskRequest req) {
        validateDate(req.taskDate());
        DailyTask t = repo.findByIdWithUsers(id).orElseThrow(() -> ApiException.notFound("Задача не найдена"));
        String before = describe(t);
        boolean reassigned = !t.getAssignee().getId().equals(req.assigneeId());
        if (reassigned) {
            t.setAssignee(activeAssignee(req.assigneeId()));
        }
        fill(t, req.title(), req.description(), req.taskDate(), req.dueTime(), req.priority());
        if (req.status() != null) {
            t.setStatus(req.status());
        }
        t.setUpdatedAt(Instant.now(clock));
        audit.log(actorId, reassigned ? "TASK_REASSIGNED_BY_MANAGER" : "TASK_UPDATED_BY_MANAGER", "DailyTask", id,
                "before: " + before + " | after: " + describe(t));
        if (reassigned) {
            notifyAssignee(actorId, t, "Вам назначена задача");
        }
        return toDto(t, null, true);
    }

    @Transactional
    public void managerDelete(Long actorId, Long id) {
        DailyTask t = repo.findByIdWithUsers(id).orElseThrow(() -> ApiException.notFound("Задача не найдена"));
        audit.log(actorId, "TASK_DELETED_BY_MANAGER", "DailyTask", id, describe(t));
        repo.delete(t);
    }

    // ---------------- вспомогательное ----------------

    private DailyTask ownTask(Long userId, Long id) {
        DailyTask t = repo.findByIdWithUsers(id).orElseThrow(() -> ApiException.notFound("Задача не найдена"));
        if (!t.getAssignee().getId().equals(userId)) {
            throw ApiException.forbidden("Нельзя управлять чужими задачами");
        }
        return t;
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    private User activeAssignee(Long id) {
        User u = findUser(id);
        if (!u.isEnabled()) {
            throw ApiException.conflict("ASSIGNEE_DISABLED", "Нельзя назначить задачу отключённому сотруднику");
        }
        return u;
    }

    private static void fill(DailyTask t, String title, String description, LocalDate date, java.time.LocalTime time,
                             TaskPriority priority) {
        t.setTitle(title.trim());
        t.setDescription(description == null || description.isBlank() ? null : description.trim());
        t.setTaskDate(date);
        t.setDueTime(time);
        t.setPriority(priority == null ? TaskPriority.NORMAL : priority);
    }

    private static void validateDate(LocalDate d) {
        if (d.isBefore(MIN_DATE) || d.isAfter(MAX_DATE)) {
            throw ApiException.badRequest("INVALID_DATE", "Дата задачи вне допустимого диапазона");
        }
    }

    private static Specification<DailyTask> statusIs(TaskStatus s) {
        return (root, query, cb) -> cb.equal(root.get("status"), s);
    }

    private static Progress progressOf(List<TaskDto> tasks) {
        long todo = tasks.stream().filter(t -> t.status() == TaskStatus.TODO).count();
        long prog = tasks.stream().filter(t -> t.status() == TaskStatus.IN_PROGRESS).count();
        long done = tasks.stream().filter(t -> t.status() == TaskStatus.DONE).count();
        return Progress.of(todo, prog, done);
    }

    private static String describe(DailyTask t) {
        String title = t.getTitle().length() > 80 ? t.getTitle().substring(0, 80) + "…" : t.getTitle();
        return "assignee=" + t.getAssignee().getId() + ", date=" + t.getTaskDate() + ", status=" + t.getStatus()
                + ", priority=" + t.getPriority() + ", title=" + title;
    }

    private void notifyAssignee(Long actorId, DailyTask t, String title) {
        if (!t.getAssignee().getId().equals(actorId)) {
            notifications.notify(t.getAssignee().getId(), "TASK_ASSIGNED", title,
                    "«" + t.getTitle() + "» на " + t.getTaskDate().format(DMY));
        }
    }

    /** viewerId == null — просмотр руководителем (все права). */
    private TaskDto toDto(DailyTask t, Long viewerId, boolean manager) {
        boolean creator = viewerId != null && t.getCreatedBy().getId().equals(viewerId);
        boolean edit = manager || creator;
        return new TaskDto(t.getId(), t.getTitle(), t.getDescription(), t.getAssignee().getId(),
                t.getAssignee().fullName(), t.getCreatedBy().getId(), t.getCreatedBy().fullName(), t.getTaskDate(),
                t.getDueTime(), t.getPriority(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt(), edit, edit, true);
    }
}
