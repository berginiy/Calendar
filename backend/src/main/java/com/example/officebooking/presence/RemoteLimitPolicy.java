package com.example.officebooking.presence;

import com.example.officebooking.settings.LimitRule;
import java.util.ArrayList;
import java.util.List;

/** Чистая проверка лимитов удалённой работы. rule == null означает, что лимит отключён. */
public final class RemoteLimitPolicy {
    private RemoteLimitPolicy() {}

    /**
     * @param weekRule    активное недельное правило или null
     * @param weekUsed    активных удалённых дней в неделе выбранной даты
     * @param monthRule   активное месячное правило или null
     * @param monthUsed   активных удалённых дней в месяце выбранной даты
     * @return список причин отказа (пусто — лимиты не превышены)
     */
    public static List<String> violations(LimitRule weekRule, long weekUsed, boolean weekIsCurrent,
                                          LimitRule monthRule, long monthUsed, boolean monthIsCurrent) {
        List<String> result = new ArrayList<>();
        if (weekRule != null && weekUsed >= weekRule.getMaxDays()) {
            result.add("Вы уже использовали лимит удалённой работы на "
                    + (weekIsCurrent ? "эту неделю" : "неделю выбранной даты")
                    + " (" + weekUsed + " из " + weekRule.getMaxDays() + ")");
        }
        if (monthRule != null && monthUsed >= monthRule.getMaxDays()) {
            result.add("Вы уже использовали лимит удалённой работы на "
                    + (monthIsCurrent ? "этот месяц" : "месяц выбранной даты")
                    + " (" + monthUsed + " из " + monthRule.getMaxDays() + ")");
        }
        return result;
    }
}
