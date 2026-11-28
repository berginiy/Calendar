package com.example.officebooking.task;

import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.task.TaskDtos.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Личные задачи текущего пользователя. Идентификатор исполнителя берётся из токена, а не из запроса. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final String DATE = "yyyy-MM-dd";
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public DayTasksResponse day(@AuthenticationPrincipal UserPrincipal me,
                                @RequestParam @DateTimeFormat(pattern = DATE) LocalDate date) {
        return service.day(me.id(), date);
    }

    @GetMapping("/range")
    public List<TaskDto> range(@AuthenticationPrincipal UserPrincipal me,
                               @RequestParam @DateTimeFormat(pattern = DATE) LocalDate from,
                               @RequestParam @DateTimeFormat(pattern = DATE) LocalDate to) {
        return service.range(me.id(), from, to);
    }

    @PostMapping
    public ResponseEntity<TaskDto> create(@AuthenticationPrincipal UserPrincipal me,
                                          @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.id(), request));
    }

    @PutMapping("/{id}")
    public TaskDto update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                          @Valid @RequestBody TaskRequest request) {
        return service.update(me.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public TaskDto status(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                          @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(me.id(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        service.delete(me.id(), id);
    }
}
