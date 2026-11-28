package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** Этапы B/C: предпочтения, умное распределение, подтверждение, уведомления. */
@AutoConfigureMockMvc
class AllocationIntegrationTest extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ---------- helpers ----------
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

    private void enableAllocation() throws Exception {
        mvc.perform(put("/api/manager/allocation/settings").header("Authorization", manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"policy\":\"ROTATION\",\"preferenceDeadlineHours\":24}"))
                .andExpect(status().isOk());
    }

    private void setCapacity(int n) throws Exception {
        mvc.perform(put("/api/manager/settings").header("Authorization", manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"officeCapacity\":" + n + ",\"allowSameDayBooking\":false,\"cancellationDeadlineHours\":0,"
                        + "\"workingDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"],"
                        + "\"teamVisibleToEmployees\":false}")).andExpect(status().isOk());
    }

    private MvcResult pref(String token, LocalDate d, String preference) throws Exception {
        return mvc.perform(post("/api/work-preferences").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + d + "\",\"preference\":\"" + preference + "\"}")).andReturn();
    }

    private JsonNode runAllocation(LocalDate from, LocalDate to) throws Exception {
        MvcResult r = mvc.perform(post("/api/manager/allocation/run").header("Authorization", manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return body(r);
    }

    private MvcResult confirm(long runId) throws Exception {
        return mvc.perform(post("/api/manager/allocation/" + runId + "/confirm").header("Authorization", manager())).andReturn();
    }

    private JsonNode resultOf(JsonNode detail, long userId, LocalDate date) {
        for (JsonNode r : detail.get("results")) {
            if (r.get("userId").asLong() == userId && r.get("date").asText().equals(date.toString())) return r;
        }
        throw new AssertionError("нет результата для пользователя " + userId + " на " + date);
    }

    private int activeRecords(LocalDate d, String type) {
        return jdbc.queryForObject("select count(*) from presence_records where presence_date = ? and type = ? and status = 'ACTIVE'",
                Integer.class, d, type);
    }

    private int notifications(long userId) {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'ALLOCATION_CONFIRMED'",
                Integer.class, userId);
    }

    private MvcResult book(String token, String url, LocalDate d) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + d + "\"}")).andReturn();
    }

    // ---------- предпочтения ----------
    @Test
    void featureIsDisabledByDefaultAndPreferencesAreRefused() throws Exception {
        MvcResult r = pref(token(newUser("Early", Role.EMPLOYEE)), futureMonday(), "OFFICE");
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(r).get("code").asText()).isEqualTo("FEATURE_DISABLED");
    }

    @Test
    void preferenceLifecycleKeepsFirstSubmissionTime() throws Exception {
        enableAllocation();
        User u = newUser("Pref", Role.EMPLOYEE);
        String t = token(u);
        LocalDate d = futureMonday();

        MvcResult created = pref(t, d, "OFFICE");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long id = body(created).get("preferenceId").asLong();
        assertThat(pref(t, d, "REMOTE").getResponse().getStatus()).isEqualTo(409); // дубль

        Object first = jdbc.queryForObject("select first_submitted_at from work_preferences where id = ?", Object.class, id);
        mvc.perform(put("/api/work-preferences/" + id).header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preference\":\"FLEXIBLE\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/work-preferences/" + id).header("Authorization", t)).andExpect(status().isOk());
        assertThat(pref(t, d, "OFFICE").getResponse().getStatus()).isEqualTo(201); // повторная подача после отзыва
        Object after = jdbc.queryForObject("select first_submitted_at from work_preferences where id = ?", Object.class, id);
        assertThat(after).isEqualTo(first); // обновление/отзыв/повторная подача преимущества не дают
        assertThat(jdbc.queryForObject("select count(*) from work_preferences where user_id = ?", Integer.class, u.getId()))
                .isEqualTo(1);
    }

    @Test
    void preferenceRulesForDatesAndDeadline() throws Exception {
        enableAllocation();
        String t = token(newUser("Dates", Role.EMPLOYEE));
        assertThat(pref(t, today().minusDays(1), "OFFICE").getResponse().getStatus()).isEqualTo(409);
        assertThat(pref(t, today(), "OFFICE").getResponse().getStatus()).isEqualTo(409);
        assertThat(pref(t, futureMonday().plusDays(5), "OFFICE").getResponse().getStatus()).isEqualTo(409); // суббота

        // срок 720 часов (30 суток): ближайшие даты уже «просрочены»
        mvc.perform(put("/api/manager/allocation/settings").header("Authorization", manager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true,\"policy\":\"ROTATION\",\"preferenceDeadlineHours\":720}")).andExpect(status().isOk());
        MvcResult late = pref(t, futureMonday(), "OFFICE");
        assertThat(late.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(late).get("message").asText()).contains("Срок изменения предпочтений истёк");
    }

    @Test
    void employeeCannotChangeSomeoneElsesPreference() throws Exception {
        enableAllocation();
        String owner = token(newUser("Own", Role.EMPLOYEE));
        String other = token(newUser("Oth", Role.EMPLOYEE));
        long id = body(pref(owner, futureMonday(), "OFFICE")).get("preferenceId").asLong();
        mvc.perform(put("/api/work-preferences/" + id).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"preference\":\"REMOTE\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/work-preferences/" + id).header("Authorization", other)).andExpect(status().isForbidden());
    }

    @Test
    void bulkSetsAndWithdrawsPreferences() throws Exception {
        enableAllocation();
        String t = token(newUser("Bulk", Role.EMPLOYEE));
        LocalDate mon = futureMonday();
        mvc.perform(post("/api/work-preferences/bulk").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"date\":\"" + mon + "\",\"preference\":\"OFFICE\"},{\"date\":\""
                        + mon.plusDays(1) + "\",\"preference\":\"REMOTE\"}]}")).andExpect(status().isOk());
        mvc.perform(post("/api/work-preferences/bulk").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"date\":\"" + mon + "\",\"preference\":null}]}")).andExpect(status().isOk());

        JsonNode days = body(mvc.perform(get("/api/work-preferences?from=" + mon + "&to=" + mon.plusDays(1))
                .header("Authorization", t)).andReturn()).get("days");
        assertThat(days.get(0).get("preference").isNull()).isTrue();
        assertThat(days.get(1).get("preference").asText()).isEqualTo("REMOTE");
        assertThat(days.get(1).get("status").asText()).isEqualTo("PENDING");
    }

    // ---------- запуск, подтверждение ----------
    @Test
    void runCreatesOnlyADraftAndConfirmationAppliesWithinCapacity() throws Exception {
        enableAllocation();
        setCapacity(2);
        LocalDate d = futureMonday();
        User a = newUser("Aa", Role.EMPLOYEE);
        User b = newUser("Bb", Role.EMPLOYEE);
        User c = newUser("Cc", Role.EMPLOYEE);
        for (User u : new User[]{a, b, c}) pref(token(u), d, "OFFICE");

        JsonNode run = runAllocation(d, d);
        long runId = run.get("run").get("id").asLong();
        assertThat(run.get("run").get("status").asText()).isEqualTo("DRAFT");
        assertThat(run.get("run").get("office").asInt()).isEqualTo(2);
        assertThat(run.get("run").get("none").asInt()).isEqualTo(1);
        assertThat(activeRecords(d, "OFFICE")).isZero(); // черновик реальных записей не создаёт

        MvcResult confirmed = confirm(runId);
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(confirmed).get("run").get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(activeRecords(d, "OFFICE")).isEqualTo(2);
        for (User u : new User[]{a, b, c}) assertThat(notifications(u.getId())).isEqualTo(1);

        // отказанному сотруднику понятно почему
        JsonNode denied = body(mvc.perform(get("/api/work-preferences?from=" + d + "&to=" + d)
                .header("Authorization", token(c))).andReturn()).get("days").get(0);
        assertThat(denied.get("status").asText()).isEqualTo("NOT_SATISFIED");
        assertThat(denied.get("confirmed").asBoolean()).isTrue();
        assertThat(denied.get("explanation").asText()).contains("не удовлетворено");
        JsonNode granted = body(mvc.perform(get("/api/work-preferences?from=" + d + "&to=" + d)
                .header("Authorization", token(a))).andReturn()).get("days").get(0);
        assertThat(granted.get("status").asText()).isEqualTo("SATISFIED");
    }

    @Test
    void secondConfirmationIsRejectedAndChangesNothing() throws Exception {
        enableAllocation();
        LocalDate d = futureMonday();
        pref(token(newUser("One", Role.EMPLOYEE)), d, "OFFICE");
        long runId = runAllocation(d, d).get("run").get("id").asLong();
        assertThat(confirm(runId).getResponse().getStatus()).isEqualTo(200);
        assertThat(confirm(runId).getResponse().getStatus()).isEqualTo(409);
        assertThat(activeRecords(d, "OFFICE")).isEqualTo(1);
    }

    @Test
    void rotationFavoursTheEmployeeDeniedInThePreviousConfirmedRun() throws Exception {
        enableAllocation();
        setCapacity(2);
        LocalDate d1 = futureMonday();
        LocalDate d2 = d1.plusDays(1);
        User a = newUser("Ra", Role.EMPLOYEE);
        User b = newUser("Rb", Role.EMPLOYEE);
        User c = newUser("Rc", Role.EMPLOYEE);
        for (User u : new User[]{a, b, c}) pref(token(u), d1, "OFFICE");
        long run1 = runAllocation(d1, d1).get("run").get("id").asLong();
        confirm(run1);
        // кто-то из a/b/c остался без места в первый день — именно он должен выиграть второй день
        String deniedUser = jdbc.queryForObject("select user_id::text from allocation_results where run_id = ? and outcome = 'NONE'",
                String.class, run1);

        for (User u : new User[]{a, b, c}) pref(token(u), d2, "OFFICE");
        JsonNode second = runAllocation(d2, d2);
        assertThat(resultOf(second, Long.parseLong(deniedUser), d2).get("outcome").asText()).isEqualTo("OFFICE");
        assertThat(second.get("run").get("none").asInt()).isEqualTo(1);
    }

    @Test
    void existingRecordsAreKeptAndOccupySeats() throws Exception {
        enableAllocation();
        setCapacity(2);
        LocalDate d = futureMonday();
        User holder = newUser("Holder", Role.EMPLOYEE);
        User b = newUser("Bq", Role.EMPLOYEE);
        User c = newUser("Cq", Role.EMPLOYEE);
        assertThat(book(token(holder), "/api/bookings", d).getResponse().getStatus()).isEqualTo(201);
        pref(token(holder), d, "REMOTE"); // хочет удалённо, но запись уже есть — она не меняется
        pref(token(b), d, "OFFICE");
        pref(token(c), d, "OFFICE");

        JsonNode run = runAllocation(d, d);
        assertThat(resultOf(run, holder.getId(), d).get("source").asText()).isEqualTo("EXISTING");
        assertThat(run.get("run").get("office").asInt()).isEqualTo(2); // holder (существующая) + один новый
        confirm(run.get("run").get("id").asLong());
        assertThat(activeRecords(d, "OFFICE")).isEqualTo(2); // не больше вместимости
        assertThat(jdbc.queryForObject("select type from presence_records where user_id = ? and presence_date = ? and status = 'ACTIVE'",
                String.class, holder.getId(), d)).isEqualTo("OFFICE");
    }

    @Test
    void confirmationNeverOverbooksWhenTheSeatWasTakenAfterTheDraft() throws Exception {
        enableAllocation();
        setCapacity(1);
        LocalDate d = futureMonday();
        User a = newUser("Draft", Role.EMPLOYEE);
        User late = newUser("Late", Role.EMPLOYEE);
        pref(token(a), d, "OFFICE");
        JsonNode run = runAllocation(d, d);
        assertThat(resultOf(run, a.getId(), d).get("outcome").asText()).isEqualTo("OFFICE");

        // после создания черновика место занимает обычная запись
        assertThat(book(token(late), "/api/bookings", d).getResponse().getStatus()).isEqualTo(201);
        JsonNode confirmed = body(confirm(run.get("run").get("id").asLong()));

        assertThat(activeRecords(d, "OFFICE")).isEqualTo(1);
        JsonNode r = resultOf(confirmed, a.getId(), d);
        assertThat(r.get("applyStatus").asText()).isEqualTo("SKIPPED");
        assertThat(r.get("applyMessage").asText()).contains("мест нет");
    }

    @Test
    void remoteLimitIsEnforcedInAllocation() throws Exception {
        enableAllocation();
        LocalDate mon = futureMonday();
        User u = newUser("Remote", Role.EMPLOYEE);
        String t = token(u);
        pref(t, mon, "REMOTE");
        pref(t, mon.plusDays(1), "REMOTE");
        JsonNode run = runAllocation(mon, mon.plusDays(1));
        assertThat(resultOf(run, u.getId(), mon).get("outcome").asText()).isEqualTo("REMOTE");
        JsonNode tue = resultOf(run, u.getId(), mon.plusDays(1));
        assertThat(tue.get("outcome").asText()).isEqualTo("NONE");
        assertThat(tue.get("explanation").asText()).contains("лимит удалённой работы");
        confirm(run.get("run").get("id").asLong());
        assertThat(jdbc.queryForObject("select count(*) from presence_records where user_id = ? and type = 'REMOTE' and status = 'ACTIVE'",
                Integer.class, u.getId())).isEqualTo(1);
    }

    @Test
    void onlyOneOpenDraftPerPeriodAndDiscardCreatesNothing() throws Exception {
        enableAllocation();
        LocalDate d = futureMonday();
        pref(token(newUser("Dr", Role.EMPLOYEE)), d, "OFFICE");
        long runId = runAllocation(d, d).get("run").get("id").asLong();

        MvcResult again = mvc.perform(post("/api/manager/allocation/run").header("Authorization", manager())
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"" + d + "\",\"to\":\"" + d + "\"}")).andReturn();
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(again).get("code").asText()).isEqualTo("DRAFT_EXISTS");

        mvc.perform(post("/api/manager/allocation/" + runId + "/discard").header("Authorization", manager()))
                .andExpect(status().isOk());
        assertThat(activeRecords(d, "OFFICE")).isZero();
        assertThat(confirm(runId).getResponse().getStatus()).isEqualTo(409); // отклонённый подтвердить нельзя
        runAllocation(d, d); // после отклонения новый запуск возможен
    }

    @Test
    void manualAdjustmentRespectsCapacityRequiresReasonAndExistingIsImmutable() throws Exception {
        enableAllocation();
        setCapacity(1);
        LocalDate d = futureMonday();
        User a = newUser("Ma", Role.EMPLOYEE);
        User b = newUser("Mb", Role.EMPLOYEE);
        pref(token(a), d, "OFFICE");
        pref(token(b), d, "OFFICE");
        JsonNode run = runAllocation(d, d);
        long runId = run.get("run").get("id").asLong();
        JsonNode winner = resultOf(run, a.getId(), d).get("outcome").asText().equals("OFFICE")
                ? resultOf(run, a.getId(), d) : resultOf(run, b.getId(), d);
        JsonNode loser = winner.get("userId").asLong() == a.getId() ? resultOf(run, b.getId(), d) : resultOf(run, a.getId(), d);

        String m = manager();
        String url = "/api/manager/allocation/" + runId + "/results/";
        // место уже занято в черновике
        assertThat(mvc.perform(put(url + loser.get("id").asLong()).header("Authorization", m)
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"OFFICE\",\"reason\":\"нужен проектор\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
        // причина обязательна
        mvc.perform(put(url + loser.get("id").asLong()).header("Authorization", m).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"REMOTE\",\"reason\":\"  \"}")).andExpect(status().isBadRequest());
        // освобождаем место и отдаём другому
        mvc.perform(put(url + winner.get("id").asLong()).header("Authorization", m).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"NONE\",\"reason\":\"болеет\"}")).andExpect(status().isOk());
        mvc.perform(put(url + loser.get("id").asLong()).header("Authorization", m).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"OFFICE\",\"reason\":\"нужен проектор\"}")).andExpect(status().isOk());

        Integer adjusted = jdbc.queryForObject("select count(*) from audit_log where action = 'ALLOCATION_RESULT_ADJUSTED'", Integer.class);
        assertThat(adjusted).isEqualTo(2);

        // сотрудник видит только нейтральную формулировку, а не внутреннюю причину
        User loserUser = loser.get("userId").asLong() == a.getId() ? a : b;
        confirm(runId);
        JsonNode mine = body(mvc.perform(get("/api/work-preferences?from=" + d + "&to=" + d)
                .header("Authorization", token(loserUser))).andReturn()).get("days").get(0);
        assertThat(mine.get("explanation").asText()).doesNotContain("проектор");

        // подтверждённый запуск править нельзя
        assertThat(mvc.perform(put(url + loser.get("id").asLong()).header("Authorization", m)
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"NONE\",\"reason\":\"поздно\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void preferenceCannotBeChangedAfterConfirmation() throws Exception {
        enableAllocation();
        LocalDate d = futureMonday();
        User u = newUser("Locked", Role.EMPLOYEE);
        String t = token(u);
        long prefId = body(pref(t, d, "OFFICE")).get("preferenceId").asLong();
        confirm(runAllocation(d, d).get("run").get("id").asLong());
        MvcResult r = mvc.perform(put("/api/work-preferences/" + prefId).header("Authorization", t)
                .contentType(MediaType.APPLICATION_JSON).content("{\"preference\":\"REMOTE\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(r).get("message").asText()).contains("уже подтверждено");
    }

    // ---------- права доступа, уведомления, экспорт ----------
    @Test
    void employeeCannotUseManagerAllocationEndpoints() throws Exception {
        String t = token(newUser("Nope", Role.EMPLOYEE));
        LocalDate d = futureMonday();
        mvc.perform(get("/api/manager/allocation?from=" + d + "&to=" + d).header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/allocation/runs").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(post("/api/manager/allocation/run").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"" + d + "\",\"to\":\"" + d + "\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/manager/allocation/1/confirm").header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void notificationsArePrivateAndCanBeMarkedRead() throws Exception {
        enableAllocation();
        LocalDate d = futureMonday();
        User a = newUser("Na", Role.EMPLOYEE);
        User b = newUser("Nb", Role.EMPLOYEE);
        pref(token(a), d, "OFFICE");
        confirm(runAllocation(d, d).get("run").get("id").asLong());

        String ta = token(a);
        JsonNode list = body(mvc.perform(get("/api/notifications").header("Authorization", ta)).andReturn());
        assertThat(list.get("unread").asInt()).isEqualTo(1);
        long id = list.get("items").get(0).get("id").asLong();

        mvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", token(b))).andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", ta)).andExpect(status().isNoContent());
        assertThat(body(mvc.perform(get("/api/notifications").header("Authorization", ta)).andReturn()).get("unread").asInt()).isZero();
        assertThat(body(mvc.perform(get("/api/notifications").header("Authorization", token(b))).andReturn()).get("items")).isEmpty();
    }

    @Test
    void csvExportIsManagerOnlyAndWellFormed() throws Exception {
        enableAllocation();
        LocalDate d = futureMonday();
        pref(token(newUser("Csv", Role.EMPLOYEE)), d, "OFFICE");
        long runId = runAllocation(d, d).get("run").get("id").asLong();
        mvc.perform(get("/api/manager/allocation/" + runId + "/export.csv")
                .header("Authorization", token(newUser("Spy", Role.EMPLOYEE)))).andExpect(status().isForbidden());
        MvcResult csv = mvc.perform(get("/api/manager/allocation/" + runId + "/export.csv").header("Authorization", manager())).andReturn();
        assertThat(csv.getResponse().getStatus()).isEqualTo(200);
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        assertThat(csv.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Дата;Сотрудник");
    }

    @Test
    void overviewShowsPreferenceCountsAndCapacity() throws Exception {
        enableAllocation();
        LocalDate d = futureMonday();
        pref(token(newUser("Ov1", Role.EMPLOYEE)), d, "OFFICE");
        pref(token(newUser("Ov2", Role.EMPLOYEE)), d, "REMOTE");
        pref(token(newUser("Ov3", Role.EMPLOYEE)), d, "FLEXIBLE");
        JsonNode day = body(mvc.perform(get("/api/manager/allocation?from=" + d + "&to=" + d).header("Authorization", manager()))
                .andReturn()).get("days").get(0);
        assertThat(day.get("officePreferences").asInt()).isEqualTo(1);
        assertThat(day.get("remotePreferences").asInt()).isEqualTo(1);
        assertThat(day.get("flexiblePreferences").asInt()).isEqualTo(1);
        assertThat(day.get("capacity").asInt()).isEqualTo(10);
        assertThat(day.get("allocatable").asBoolean()).isTrue();
    }

    @Test
    void auditCoversAllocationLifecycle() throws Exception {
        enableAllocation();
        LocalDate d = futureMonday();
        pref(token(newUser("Aud", Role.EMPLOYEE)), d, "OFFICE");
        confirm(runAllocation(d, d).get("run").get("id").asLong());
        for (String action : new String[]{"ALLOCATION_SETTINGS_CHANGED", "ALLOCATION_RUN_CREATED", "ALLOCATION_CONFIRMED",
                "ALLOCATION_BOOKING_CREATED"}) {
            assertThat(jdbc.queryForObject("select count(*) from audit_log where action = ?", Integer.class, action))
                    .as(action).isGreaterThanOrEqualTo(1);
        }
    }
}
