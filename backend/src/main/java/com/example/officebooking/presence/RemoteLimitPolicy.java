package com.example.officebooking.presence;

import com.example.officebooking.settings.LimitRule;
import java.util.ArrayList;
import java.util.List;

/** Чистая проверка лимитов удалённой работы. Правило null (или maxDays null) означает, что лимит отключён. */
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
        return violationsForLimits(weekRule == null ? null : weekRule.getMaxDays(), weekUsed, weekIsCurrent,
                monthRule == null ? null : monthRule.getMaxDays(), monthUsed, monthIsCurrent);
    }

    /** Та же проверка по числовым лимитам (null — лимит отключён); используется алгоритмом распределения. */
    public static List<String> violationsForLimits(Integer weekMax, long weekUsed, boolean weekIsCurrent,
                                          Integer monthMax, long monthUsed, boolean monthIsCurrent) {
        List<String> result = new ArrayList<>();
        if (weekMax != null && weekUsed >= weekMax) {
            result.add("Вы уже использовали лимит удалённой работы на "
                    + (weekIsCurrent ? "эту неделю" : "неделю выбранной даты")
                    + " (" + weekUsed + " из " + weekMax + ")");
        }
        if (monthMax != null && monthUsed >= monthMax) {
            result.add("Вы уже использовали лимит удалённой работы на "
                    + (monthIsCurrent ? "этот месяц" : "месяц выбранной даты")
                    + " (" + monthUsed + " из " + monthMax + ")");
        }
        return result;
    }
}
