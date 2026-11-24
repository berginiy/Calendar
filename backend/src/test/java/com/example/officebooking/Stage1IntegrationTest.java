package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Интеграционные тесты этапа 1: командный календарь, управление сотрудниками, пароли, аналитика, причины в календаре. */
@AutoConfigureMockMvc
class Stage1IntegrationTest extends IntegrationTestBase {
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

    private String employeeToken(User u) throws Exception {
        return login(u.getEmail(), PASSWORD);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private MvcResult postDate(String url, String token, LocalDate d) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + d + "\"}")).andReturn();
    }

    private void setTeamVisible(boolean visible) throws Exception {
        mvc.perform(put("/api/manager/settings").header("Authorization", manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officeCapacity\":10,\"allowSameDayBooking\":false,\"cancellationDeadlineHours\":0,"
                        + "\"workingDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"],"
                        + "\"teamVisibleToEmployees\":" + visible + "}")).andExpect(status().isOk());
    }

    private MvcResult createEmployee(String mgr, String name, String role) throws Exception {
        return mvc.perform(post("/api/manager/employees").header("Authorization", mgr)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"" + name + "\",\"lastName\":\"Новый\",\"email\":\"it-" + name.toLowerCase()
                        + "@example.com\",\"role\":\"" + role + "\"}")).andReturn();
    }

    // ---------- командный календарь ----------
    @Test
    void employeeSeesOnlyCountersByDefault() throws Exception {
        User viewer = newUser("Viewer", Role.EMPLOYEE);
        User other = newUser("Other", Role.EMPLOYEE);
        LocalDate d = futureMonday();
        postDate("/api/bookings", employeeToken(other), d);

        MvcResult r = mvc.perform(get("/api/team?from=" + d + "&to=" + d)
                .header("Authorization", employeeToken(viewer))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode day = body(r).get("days").get(0);
        assertThat(body(r).get("visibility").asText()).isEqualTo("COUNTS_ONLY");
        assertThat(day.get("office").asInt()).isEqualTo(1);
        assertThat(day.get("members")).isEmpty();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("Other").doesNotContain("it-other");
    }

    @Test
    void managerSeesNamesAndStatuses() throws Exception {
        User e = newUser("Named", Role.EMPLOYEE);
        LocalDate d = futureMonday();
        postDate("/api/remote-days", employeeToken(e), d);

        MvcResult r = mvc.perform(get("/api/team?from=" + d + "&to=" + d).header("Authorization", manager())).andReturn();
        assertThat(body(r).get("visibility").asText()).isEqualTo("FULL");
        assertThat(r.getResponse().getContentAsString()).contains("Named").contains("REMOTE");
    }

    @Test
    void enabledSettingGivesEmployeesNamesButNeverEmails() throws Exception {
        setTeamVisible(true);
        User viewer = newUser("Seer", Role.EMPLOYEE);
        newUser("Colleague", Role.EMPLOYEE);
        LocalDate d = futureMonday();

        MvcResult r = mvc.perform(get("/api/team?from=" + d + "&to=" + d)
                .header("Authorization", employeeToken(viewer))).andReturn();
        String content = r.getResponse().getContentAsString();
        assertThat(body(r).get("visibility").asText()).isEqualTo("FULL");
        assertThat(content).contains("Colleague");
        assertThat(content).doesNotContain("@example.com");
    }

    @Test
    void teamPeriodLongerThanMonthIsRejected() throws Exception {
        mvc.perform(get("/api/team?from=" + futureMonday() + "&to=" + futureMonday().plusDays(40))
                .header("Authorization", manager())).andExpect(status().isBadRequest());
    }

    // ---------- причины в календаре ----------
    @Test
    void calendarExplainsRemoteLimitReason() throws Exception {
        User u = newUser("Limited", Role.EMPLOYEE);
        String t = employeeToken(u);
        LocalDate monday = futureMonday();
        postDate("/api/remote-days", t, monday);

        MvcResult r = mvc.perform(get("/api/calendar?from=" + monday + "&to=" + monday.plusDays(1))
                .header("Authorization", t)).andReturn();
        JsonNode tuesday = body(r).get(1);
        assertThat(tuesday.get("canRemote").asBoolean()).isFalse();
        assertThat(tuesday.get("remoteReason").asText()).contains("лимит");
        assertThat(tuesday.get("canBook").asBoolean()).isTrue();
        assertThat(tuesday.get("occupancyLevel").asText()).isEqualTo("FREE");
    }

    // ---------- сотрудники ----------
    @Test
    void employeeCannotUseEmployeeManagement() throws Exception {
        String t = employeeToken(newUser("Plain", Role.EMPLOYEE));
        mvc.perform(get("/api/manager/employees").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(post("/api/manager/employees").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"it-x@example.com\",\"role\":\"MANAGER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createdEmployeeMustChangeTemporaryPasswordBeforeAnythingElse() throws Exception {
        MvcResult created = createEmployee(manager(), "Fresh", "EMPLOYEE");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String temp = body(created).get("temporaryPassword").asText();
        assertThat(temp).hasSize(12);
        assertThat(created.getResponse().getContentAsString()).doesNotContain("passwordHash");

        String token = login("it-fresh@example.com", temp);
        MvcResult blocked = mvc.perform(get("/api/calendar?from=" + futureMonday() + "&to=" + futureMonday())
                .header("Authorization", token)).andReturn();
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/change-password").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + temp + "\",\"newPassword\":\"BrandNew#2026\"}"))
                .andExpect(status().isNoContent());

        String fresh = login("it-fresh@example.com", "BrandNew#2026");
        mvc.perform(get("/api/calendar?from=" + futureMonday() + "&to=" + futureMonday())
                .header("Authorization", fresh)).andExpect(status().isOk());
    }

    @Test
    void changePasswordRejectsWrongCurrentAndWeakNew() throws Exception {
        User u = newUser("Pwd", Role.EMPLOYEE);
        String t = employeeToken(u);
        mvc.perform(post("/api/auth/change-password").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"LongEnough#1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/change-password").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String m = manager();
        assertThat(createEmployee(m, "Dup", "EMPLOYEE").getResponse().getStatus()).isEqualTo(201);
        assertThat(createEmployee(m, "Dup", "EMPLOYEE").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void managerCannotDeactivateOrDemoteSelf() throws Exception {
        String m = manager();
        long myId = jdbc.queryForObject("select id from users where email = 'manager@test.local'", Long.class);
        mvc.perform(post("/api/manager/employees/" + myId + "/deactivate").header("Authorization", m))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/manager/employees/" + myId).header("Authorization", m)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Test\",\"lastName\":\"Manager\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivationCancelsFutureRecordsKeepsHistoryAndBlocksLogin() throws Exception {
        User u = newUser("Leaver", Role.EMPLOYEE);
        LocalDate d = futureMonday();
        assertThat(postDate("/api/bookings", employeeToken(u), d).getResponse().getStatus()).isEqualTo(201);

        mvc.perform(post("/api/manager/employees/" + u.getId() + "/deactivate").header("Authorization", manager()))
                .andExpect(status().isOk());

        Integer active = jdbc.queryForObject("select count(*) from presence_records where user_id = ? and status = 'ACTIVE'",
                Integer.class, u.getId());
        Integer total = jdbc.queryForObject("select count(*) from presence_records where user_id = ?",
                Integer.class, u.getId());
        assertThat(active).isZero();
        assertThat(total).isEqualTo(1); // история сохранена
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // вторая деактивация идемпотентна
        mvc.perform(post("/api/manager/employees/" + u.getId() + "/deactivate").header("Authorization", manager()))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordIssuesNewTemporaryPassword() throws Exception {
        User u = newUser("Forgot", Role.EMPLOYEE);
        MvcResult r = mvc.perform(post("/api/manager/employees/" + u.getId() + "/reset-password")
                .header("Authorization", manager())).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String temp = body(r).get("temporaryPassword").asText();
        String token = login(u.getEmail(), temp);
        MvcResult blocked = mvc.perform(get("/api/team?from=" + futureMonday() + "&to=" + futureMonday())
                .header("Authorization", token)).andReturn();
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        Integer leaked = jdbc.queryForObject("select count(*) from audit_log where details like ?", Integer.class,
                "%" + temp + "%");
        assertThat(leaked).isZero(); // временный пароль не попадает в аудит
    }

    @Test
    void employeeListSupportsSearchAndFilters() throws Exception {
        newUser("Searchable", Role.EMPLOYEE);
        MvcResult r = mvc.perform(get("/api/manager/employees?q=searchable&role=EMPLOYEE&enabled=true")
                .header("Authorization", manager())).andReturn();
        assertThat(body(r).get("totalElements").asInt()).isEqualTo(1);
        assertThat(r.getResponse().getContentAsString()).doesNotContain("passwordHash");
    }

    // ---------- аналитика ----------
    @Test
    void analyticsAndCsvAreManagerOnly() throws Exception {
        LocalDate d = futureMonday();
        String url = "?from=" + d + "&to=" + d.plusDays(6);
        String emp = employeeToken(newUser("Nosy", Role.EMPLOYEE));
        mvc.perform(get("/api/manager/analytics" + url).header("Authorization", emp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/analytics/export.csv" + url).header("Authorization", emp))
                .andExpect(status().isForbidden());

        postDate("/api/bookings", employeeToken(newUser("Planner", Role.EMPLOYEE)), d);
        MvcResult r = mvc.perform(get("/api/manager/analytics" + url).header("Authorization", manager())).andReturn();
        assertThat(body(r).get("basis").asText()).isEqualTo("PLANNED");
        assertThat(body(r).get("officeBookings").asInt()).isEqualTo(1);

        MvcResult csv = mvc.perform(get("/api/manager/analytics/export.csv" + url).header("Authorization", manager()))
                .andReturn();
        assertThat(csv.getResponse().getStatus()).isEqualTo(200);
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        assertThat(csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Дата;День недели");
    }
}
