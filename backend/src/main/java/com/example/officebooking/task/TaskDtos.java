package com.example.officebooking.task;

import com.example.officebooking.common.PageResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class TaskDtos {
    private TaskDtos() {}

    /** canEdit / canDelete / canChangeStatus считает сервер, интерфейс не дублирует правила. */
    public record TaskDto(Long id, String title, String description, Long assigneeId, String assigneeName,
                          Long createdById, String createdByName, LocalDate taskDate, LocalTime dueTime,
                          TaskPriority priority, TaskStatus status, Instant createdAt, Instant updatedAt,
                          boolean canEdit, boolean canDelete, boolean canChangeStatus) {}

    /** Личная задача: исполнитель — всегда текущий пользователь. */
    public record TaskRequest(
            @NotBlank(message = "Укажите название задачи") @Size(max = 200, message = "Название не длиннее 200 символов") String title,
            @Size(max = 2000, message = "Описание не длиннее 2000 символов") String description,
            @NotNull(message = "Укажите дату") LocalDate taskDate,
            LocalTime dueTime,
            TaskPriority priority) {}

    public record ManagerTaskRequest(
            @NotNull(message = "Выберите сотрудника") Long assigneeId,
            @NotBlank(message = "Укажите название задачи") @Size(max = 200, message = "Название не длиннее 200 символов") String title,
            @Size(max = 2000, message = "Описание не длиннее 2000 символов") String description,
            @NotNull(message = "Укажите дату") LocalDate taskDate,
            LocalTime dueTime,
            TaskPriority priority,
            TaskStatus status) {}

    public record StatusRequest(@NotNull(message = "Укажите статус") TaskStatus status) {}

    public record Progress(long total, long done, long inProgress, long todo, int percent) {
        public static Progress of(long todo, long inProgress, long done) {
            long total = todo + inProgress + done;
            return new Progress(total, done, inProgress, todo, total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
        }
    }

    public record DayTasksResponse(LocalDate date, Progress progress, List<TaskDto> tasks) {}

    public record ManagerTasksResponse(Progress progress, PageResponse<TaskDto> tasks) {}
}
