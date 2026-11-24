package com.example.officebooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.officebooking.presence.RemoteLimitPolicy;
import com.example.officebooking.settings.LimitRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemoteLimitPolicyTest {

    private static LimitRule rule(int max) {
        LimitRule r = new LimitRule();
        r.setMaxDays(max);
        r.setEnabled(true);
        return r;
    }

    @Test
    void withinLimitsHasNoViolations() {
        assertThat(RemoteLimitPolicy.violations(rule(1), 0, true, rule(4), 3, true)).isEmpty();
    }

    @Test
    void weeklyLimitExceeded() {
        List<String> v = RemoteLimitPolicy.violations(rule(1), 1, true, rule(4), 1, true);
        assertThat(v).hasSize(1);
        assertThat(v.get(0)).contains("на эту неделю");
    }

    @Test
    void monthlyLimitExceeded() {
        List<String> v = RemoteLimitPolicy.violations(rule(1), 0, true, rule(4), 4, true);
        assertThat(v).hasSize(1);
        assertThat(v.get(0)).contains("на этот месяц");
    }

    @Test
    void bothReasonsAreReturned() {
        assertThat(RemoteLimitPolicy.violations(rule(1), 1, true, rule(4), 4, true)).hasSize(2);
    }

    @Test
    void disabledRuleIsIgnored() {
        assertThat(RemoteLimitPolicy.violations(null, 99, true, null, 99, true)).isEmpty();
    }

    @Test
    void zeroLimitForbidsAnyRemoteDay() {
        assertThat(RemoteLimitPolicy.violations(rule(0), 0, true, null, 0, true)).hasSize(1);
    }

    @Test
    void otherPeriodWordingIsUsedForNonCurrentPeriod() {
        List<String> v = RemoteLimitPolicy.violations(rule(1), 1, false, null, 0, false);
        assertThat(v.get(0)).contains("неделю выбранной даты");
    }
}
