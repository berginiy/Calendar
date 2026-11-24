package com.example.officebooking.employee;

import com.example.officebooking.common.PageResponse;
import com.example.officebooking.employee.EmployeeDtos.*;
import com.example.officebooking.security.UserPrincipal;
import com.example.officebooking.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Только для MANAGER (защищено маской /api/manager/** в SecurityConfig). */
@RestController
@RequestMapping("/api/manager/employees")
public class EmployeeController {
    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EmployeeDto> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Role role,
                                          @RequestParam(required = false) Boolean enabled,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.list(q, role, enabled, page, size);
    }

    @PostMapping
    public ResponseEntity<CredentialsResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                      @Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(me.id(), request));
    }

    @PutMapping("/{id}")
    public EmployeeDto update(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                              @Valid @RequestBody UpdateEmployeeRequest request) {
        return service.update(me.id(), id, request);
    }

    @PostMapping("/{id}/deactivate")
    public EmployeeDto deactivate(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id,
                                  @RequestBody(required = false) DeactivateRequest request) {
        boolean cancel = request == null || request.cancelFutureRecords() == null || request.cancelFutureRecords();
        return service.deactivate(me.id(), id, cancel);
    }

    @PostMapping("/{id}/activate")
    public EmployeeDto activate(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.activate(me.id(), id);
    }

    @PostMapping("/{id}/reset-password")
    public PasswordResetResponse resetPassword(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return service.resetPassword(me.id(), id);
    }
}
