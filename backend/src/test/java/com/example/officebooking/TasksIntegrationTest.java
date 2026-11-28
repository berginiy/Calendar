package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Этап A: ежедневные задачи. */
@AutoConfigureMockMvc
class TasksIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return "Bearer " + json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String manager() throws Exception {
        return login("manager@test.local", "Manager#12345");
    }

    private String token(User u) throws Exception {
        return login(u.getEmail(), PASSWORD);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult createOwn(String token, String title, LocalDate date) throws Exception {
        return mvc.perform(post("/api/tasks").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"taskDate\":\"" + date + "\",\"priority\":\"HIGH\"}")).andReturn();
    }

    private MvcResult createForEmployee(String mgr, long assigneeId, String title, LocalDate date) throws Exception {
        return mvc.perform(post("/api/manager/tasks").header("Authorization", mgr).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":" + assigneeId + ",\"title\":\"" + title + "\",\"taskDate\":\"" + date + "\"}"))
                .andReturn();
    }

    @Test
    void employeeCreatesTaskAndSeesProgress() throws Exception {
        String t = token(newUser("Todo", Role.EMPLOYEE));
        LocalDate d = futureMonday();
        MvcResult created = createOwn(t, "Подготовить отчёт", d);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        createOwn(t, "Позвонить клиенту", d);
        long id = body(created).get("id").asLong();

        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isOk());

        MvcResult day = mvc.perform(get("/api/tasks?date=" + d).header("Authorization", t)).andReturn();
        assertThat(body(day).get("progress").get("total").asInt()).isEqualTo(2);
        assertThat(body(day).get("progress").get("done").asInt()).isEqualTo(1);
        assertThat(body(day).get("progress").get("percent").asInt()).isEqualTo(50);
    }

    @Test
    void blankTitleIsRejected() throws Exception {
        String t = token(newUser("Blank", Role.EMPLOYEE));
        mvc.perform(post("/api/tasks").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \",\"taskDate\":\"" + futureMonday() + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void employeeCannotTouchSomeoneElsesTask() throws Exception {
        String owner = token(newUser("Owner", Role.EMPLOYEE));
        String other = token(newUser("Other", Role.EMPLOYEE));
        long id = body(createOwn(owner, "Личная", futureMonday())).get("id").asLong();

        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/tasks/" + id).header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Взлом\",\"taskDate\":\"" + futureMonday() + "\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tasks/" + id).header("Authorization", other)).andExpect(status().isForbidden());

        MvcResult othersDay = mvc.perform(get("/api/tasks?date=" + futureMonday()).header("Authorization", other)).andReturn();
        assertThat(body(othersDay).get("tasks")).isEmpty();
    }

    @Test
    void managerAssignedTaskCanOnlyChangeStatusForEmployee() throws Exception {
        User emp = newUser("Assigned", Role.EMPLOYEE);
        String t = token(emp);
        LocalDate d = futureMonday();
        MvcResult created = createForEmployee(manager(), emp.getId(), "Сверить договор", d);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long id = body(created).get("id").asLong();

        JsonNode mine = body(mvc.perform(get("/api/tasks?date=" + d).header("Authorization", t)).andReturn())
                .get("tasks").get(0);
        assertThat(mine.get("canEdit").asBoolean()).isFalse();
        assertThat(mine.get("canChangeStatus").asBoolean()).isTrue();

        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/tasks/" + id).header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Переименовал\",\"taskDate\":\"" + d + "\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tasks/" + id).header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void creatorCanEditAndDeleteOwnTask() throws Exception {
        String t = token(newUser("Creator", Role.EMPLOYEE));
        long id = body(createOwn(t, "Черновик", futureMonday())).get("id").asLong();
        mvc.perform(put("/api/tasks/" + id).header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Готовая\",\"taskDate\":\"" + futureMonday().plusDays(1) + "\",\"dueTime\":\"09:30\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/tasks/" + id).header("Authorization", t)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/tasks/" + id).header("Authorization", t)).andExpect(status().isNotFound());
    }

    @Test
    void employeeCannotUseManagerTaskEndpoints() throws Exception {
        User emp = newUser("Nosy", Role.EMPLOYEE);
        String t = token(emp);
        mvc.perform(get("/api/manager/tasks").header("Authorization", t)).andExpect(status().isForbidden());
        assertThat(createForEmployee(t, emp.getId(), "Себе", futureMonday()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void managerListFiltersAndAggregatesProgress() throws Exception {
        User a = newUser("Anna", Role.EMPLOYEE);
        User b = newUser("Boris", Role.EMPLOYEE);
        String m = manager();
        LocalDate d = futureMonday();
        long t1 = body(createForEmployee(m, a.getId(), "Задача А1", d)).get("id").asLong();
        createForEmployee(m, a.getId(), "Задача А2", d);
        createForEmployee(m, b.getId(), "Задача Б1", d);
        mvc.perform(patch("/api/tasks/" + t1 + "/status").header("Authorization", token(a))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}")).andExpect(status().isOk());

        JsonNode all = body(mvc.perform(get("/api/manager/tasks?date=" + d).header("Authorization", m)).andReturn());
        assertThat(all.get("progress").get("total").asInt()).isEqualTo(3);
        assertThat(all.get("progress").get("done").asInt()).isEqualTo(1);

        JsonNode onlyA = body(mvc.perform(get("/api/manager/tasks?date=" + d + "&assigneeId=" + a.getId())
                .header("Authorization", m)).andReturn());
        assertThat(onlyA.get("tasks").get("totalElements").asInt()).isEqualTo(2);

        JsonNode done = body(mvc.perform(get("/api/manager/tasks?date=" + d + "&status=DONE").header("Authorization", m))
                .andReturn());
        assertThat(done.get("tasks").get("totalElements").asInt()).isEqualTo(1);
        assertThat(done.get("progress").get("total").asInt()).isEqualTo(3); // прогресс не зависит от фильтра статуса
    }

    @Test
    void managerActionsAreAuditedAndNotifyAssignee() throws Exception {
        User emp = newUser("Notified", Role.EMPLOYEE);
        String m = manager();
        long id = body(createForEmployee(m, emp.getId(), "Важное", futureMonday())).get("id").asLong();
        mvc.perform(delete("/api/manager/tasks/" + id).header("Authorization", m)).andExpect(status().isNoContent());

        Integer created = jdbc.queryForObject("select count(*) from audit_log where action = 'TASK_CREATED_BY_MANAGER'", Integer.class);
        Integer deleted = jdbc.queryForObject("select count(*) from audit_log where action = 'TASK_DELETED_BY_MANAGER'", Integer.class);
        Integer notes = jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'TASK_ASSIGNED'",
                Integer.class, emp.getId());
        assertThat(created).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
        assertThat(notes).isEqualTo(1);
    }

    @Test
    void cannotAssignTaskToDisabledEmployee() throws Exception {
        User emp = newUser("Gone", Role.EMPLOYEE);
        jdbc.update("update users set enabled = false where id = ?", emp.getId());
        MvcResult r = createForEmployee(manager(), emp.getId(), "Некому", futureMonday());
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(r).get("code").asText()).isEqualTo("ASSIGNEE_DISABLED");
    }
}
