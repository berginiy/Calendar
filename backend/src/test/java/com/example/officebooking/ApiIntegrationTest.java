package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.officebooking.user.Role;
import com.example.officebooking.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ApiIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ---------- helpers ----------
    private String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String tokenFor(User u) throws Exception {
        return "Bearer " + login(u.getEmail(), PASSWORD);
    }

    private String managerToken() throws Exception {
        return "Bearer " + login("manager@test.local", "Manager#12345");
    }

    private MvcResult postDate(String url, String token, LocalDate date) throws Exception {
        return mvc.perform(post(url)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\"}")).andReturn();
    }

    private long idOf(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    // ---------- auth ----------
    @Test
    void loginSucceedsAndMeDoesNotLeakPasswordHash() throws Exception {
        User u = newUser("Ivan", Role.EMPLOYEE);
        String token = tokenFor(u);
        MvcResult me = mvc.perform(get("/api/auth/me").header("Authorization", token)).andReturn();
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat(me.getResponse().getContentAsString()).doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    @Test
    void wrongPasswordGives401() throws Exception {
        User u = newUser("Petr", Role.EMPLOYEE);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + u.getEmail() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithoutTokenGives401() throws Exception {
        mvc.perform(get("/api/calendar?from=2030-01-01&to=2030-01-31")).andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotAccessManagerEndpoints() throws Exception {
        String t = tokenFor(newUser("Anna", Role.EMPLOYEE));
        mvc.perform(get("/api/manager/dashboard?date=" + futureMonday()).header("Authorization", t))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/settings").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(put("/api/manager/settings").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"officeCapacity\":50,\"allowSameDayBooking\":true,\"cancellationDeadlineHours\":0,\"workingDays\":[\"MONDAY\"]}"))
                .andExpect(status().isForbidden());
    }

    // ---------- bookings ----------
    @Test
    void bookingShowsUpInCalendarAndCountsSeats() throws Exception {
        User u = newUser("Olga", Role.EMPLOYEE);
        String t = tokenFor(u);
        LocalDate d = futureMonday();
        assertThat(postDate("/api/bookings", t, d).getResponse().getStatus()).isEqualTo(201);

        mvc.perform(get("/api/calendar?from=" + d + "&to=" + d).header("Authorization", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occupied").value(1))
                .andExpect(jsonPath("$[0].free").value(9))
                .andExpect(jsonPath("$[0].myStatus").value("OFFICE"));
    }

    @Test
    void duplicateBookingAndOfficePlusRemoteAreRejected() throws Exception {
        String t = tokenFor(newUser("Dmitry", Role.EMPLOYEE));
        LocalDate d = futureMonday();
        assertThat(postDate("/api/bookings", t, d).getResponse().getStatus()).isEqualTo(201);

        MvcResult dup = postDate("/api/bookings", t, d);
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(dup).get("code").asText()).isEqualTo("DUPLICATE");

        MvcResult remote = postDate("/api/remote-days", t, d);
        assertThat(remote.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(remote).get("code").asText()).isEqualTo("ALREADY_HAS_OTHER_STATUS");
    }

    @Test
    void bookingIsRejectedWhenOfficeIsFull() throws Exception {
        String manager = managerToken();
        mvc.perform(put("/api/manager/settings").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"officeCapacity\":2,\"allowSameDayBooking\":false,\"cancellationDeadlineHours\":0,"
                        + "\"workingDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"]}"))
                .andExpect(status().isOk());

        LocalDate d = futureMonday();
        assertThat(postDate("/api/bookings", tokenFor(newUser("U1", Role.EMPLOYEE)), d).getResponse().getStatus()).isEqualTo(201);
        assertThat(postDate("/api/bookings", tokenFor(newUser("U2", Role.EMPLOYEE)), d).getResponse().getStatus()).isEqualTo(201);
        MvcResult third = postDate("/api/bookings", tokenFor(newUser("U3", Role.EMPLOYEE)), d);
        assertThat(third.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(third).get("code").asText()).isEqualTo("NO_FREE_SEATS");
    }

    @Test
    void pastDateAndWeekendAreRejected() throws Exception {
        String t = tokenFor(newUser("Past", Role.EMPLOYEE));
        MvcResult past = postDate("/api/bookings", t, today().minusDays(1));
        assertThat(past.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(past).get("code").asText()).isEqualTo("DATE_NOT_AVAILABLE");

        LocalDate saturday = futureMonday().plusDays(5);
        assertThat(postDate("/api/bookings", t, saturday).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void invalidDateFormatGives400() throws Exception {
        String t = tokenFor(newUser("Bad", Role.EMPLOYEE));
        mvc.perform(post("/api/bookings").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"не-дата\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/bookings").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }

    // ---------- remote days & limits ----------
    @Test
    void weeklyLimitBlocksSecondRemoteDayAndCancelFreesIt() throws Exception {
        String t = tokenFor(newUser("Remote", Role.EMPLOYEE));
        LocalDate monday = futureMonday();
        MvcResult first = postDate("/api/remote-days", t, monday);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = postDate("/api/remote-days", t, monday.plusDays(1));
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(second).get("code").asText()).isEqualTo("REMOTE_LIMIT_EXCEEDED");

        mvc.perform(delete("/api/remote-days/" + idOf(first)).header("Authorization", t)).andExpect(status().isOk());
        assertThat(postDate("/api/remote-days", t, monday.plusDays(1)).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void monthlyLimitBlocksFifthRemoteDay() throws Exception {
        String manager = managerToken();
        mvc.perform(put("/api/manager/limit-rules").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"periodType\":\"WEEKLY\",\"maxDays\":5,\"enabled\":false},{\"periodType\":\"MONTHLY\",\"maxDays\":4,\"enabled\":true}]"))
                .andExpect(status().isOk());

        String t = tokenFor(newUser("Monthly", Role.EMPLOYEE));
        List<LocalDate> days = firstWeekdaysOfFutureMonth(5);
        for (int i = 0; i < 4; i++) {
            assertThat(postDate("/api/remote-days", t, days.get(i)).getResponse().getStatus()).isEqualTo(201);
        }
        MvcResult fifth = postDate("/api/remote-days", t, days.get(4));
        assertThat(fifth.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(fifth).get("message").asText()).contains("лимит удалённой работы");
    }

    @Test
    void negativeLimitIsRejected() throws Exception {
        mvc.perform(put("/api/manager/limit-rules").header("Authorization", managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"periodType\":\"WEEKLY\",\"maxDays\":-1,\"enabled\":true}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myStatsReportsUsageAndLimits() throws Exception {
        String t = tokenFor(newUser("Stats", Role.EMPLOYEE));
        mvc.perform(get("/api/my-stats").header("Authorization", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.week.max").value(1))
                .andExpect(jsonPath("$.week.used").value(0))
                .andExpect(jsonPath("$.month.max").value(4));
    }

    // ---------- cancellation ----------
    @Test
    void employeeCannotCancelSomeoneElsesBooking() throws Exception {
        String owner = tokenFor(newUser("Owner", Role.EMPLOYEE));
        String other = tokenFor(newUser("Other", Role.EMPLOYEE));
        long id = idOf(postDate("/api/bookings", owner, futureMonday()));
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", other)).andExpect(status().isForbidden());
    }

    @Test
    void secondCancelReturnsConflict() throws Exception {
        String t = tokenFor(newUser("Twice", Role.EMPLOYEE));
        long id = idOf(postDate("/api/bookings", t, futureMonday()));
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", t)).andExpect(status().isOk());
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", t)).andExpect(status().isConflict());
    }

    @Test
    void managerCancellationOfSomeoneElsesBookingIsAudited() throws Exception {
        String t = tokenFor(newUser("Audited", Role.EMPLOYEE));
        long id = idOf(postDate("/api/bookings", t, futureMonday()));
        mvc.perform(delete("/api/bookings/" + id).header("Authorization", managerToken())).andExpect(status().isOk());
        Integer n = jdbc.queryForObject("select count(*) from audit_log where action = 'ADMIN_CANCELLED_BOOKING'", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    // ---------- manager ----------
    @Test
    void capacityCannotBeReducedBelowExistingBookings() throws Exception {
        LocalDate d = futureMonday();
        for (String n : List.of("C1", "C2", "C3")) {
            assertThat(postDate("/api/bookings", tokenFor(newUser(n, Role.EMPLOYEE)), d).getResponse().getStatus()).isEqualTo(201);
        }
        mvc.perform(put("/api/manager/settings").header("Authorization", managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officeCapacity\":2,\"allowSameDayBooking\":false,\"cancellationDeadlineHours\":0,"
                        + "\"workingDays\":[\"MONDAY\",\"TUESDAY\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void zeroCapacityIsRejected() throws Exception {
        mvc.perform(put("/api/manager/settings").header("Authorization", managerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officeCapacity\":0,\"allowSameDayBooking\":false,\"cancellationDeadlineHours\":0,"
                        + "\"workingDays\":[\"MONDAY\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardCountsStatuses() throws Exception {
        User office = newUser("InOffice", Role.EMPLOYEE);
        User remote = newUser("AtHome", Role.EMPLOYEE);
        newUser("Unknown", Role.EMPLOYEE);
        LocalDate d = futureMonday();
        postDate("/api/bookings", tokenFor(office), d);
        postDate("/api/remote-days", tokenFor(remote), d);

        mvc.perform(get("/api/manager/dashboard?date=" + d).header("Authorization", managerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inOffice").value(1))
                .andExpect(jsonPath("$.remote").value(1))
                .andExpect(jsonPath("$.totalSeats").value(10))
                .andExpect(jsonPath("$.freeSeats").value(9));
    }

    @Test
    void flywayMigrationsWereApplied() {
        Integer n = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(n).isGreaterThanOrEqualTo(1);
    }
}
