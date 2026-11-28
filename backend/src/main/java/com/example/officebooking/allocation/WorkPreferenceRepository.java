package com.example.officebooking.allocation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkPreferenceRepository extends JpaRepository<WorkPreference, Long> {
    Optional<WorkPreference> findByUser_IdAndPrefDate(Long userId, LocalDate date);

    List<WorkPreference> findByUser_IdAndPrefDateBetween(Long userId, LocalDate from, LocalDate to);

    @Query("select p from WorkPreference p join fetch p.user where p.active = true and p.prefDate between :from and :to")
    List<WorkPreference> findActiveInRangeWithUser(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Сводка для менеджера: [дата, предпочтение, количество] только по активным пользователям. */
    @Query("select p.prefDate, p.preference, count(p) from WorkPreference p "
            + "where p.active = true and p.user.enabled = true and p.prefDate between :from and :to "
            + "group by p.prefDate, p.preference")
    List<Object[]> countByDateAndPreference(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
