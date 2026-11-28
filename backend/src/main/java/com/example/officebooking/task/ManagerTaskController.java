package com.example.officebooking.task;

import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.task.TaskDtos.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Только для MANAGER (защищено маской /api/manager/** в SecurityConfig). Все изменения пишутся в аудит. */
@RestController
@RequestMapping("/api/manager/tasks")
public class ManagerTaskController {
    private static final String DATE = "yyyy-MM-dd";
    private final TaskService service;

    public ManagerTaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public ManagerTasksResponse list(@RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate date,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(pattern = DATE) LocalDate to,
                                     @RequestParam(required = false) Long assigneeId,
                                     @RequestParam(required = false) TaskStatus status,
                                     @RequestParam(required = false) TaskPriority priority,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return service.managerList(date, from, to, assigneeId, status, priority, page, size);
    }

    @PostMapping
    public ResponseEntity<TaskDto> create(@AuthenticationPrincipal UserPrincipal me,
                                          @Valid @RequestBody ManagerTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.managerCreate(me.id(), request));
    }

    @PutMapping("/{id}")
    public TaskDto update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                          @Valid @RequestBody ManagerTaskRequest request) {
        return service.managerUpdate(me.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        service.managerDelete(me.id(), id);
    }
}
