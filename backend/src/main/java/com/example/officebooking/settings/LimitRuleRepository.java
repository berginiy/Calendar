package com.example.officebooking.settings;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitRuleRepository extends JpaRepository<LimitRule, Long> {
    Optional<LimitRule> findByPeriodType(PeriodType periodType);
}
