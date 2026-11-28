package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.ApplyStatus;
import com.example.officebooking.allocation.AllocationTypes.Outcome;
import com.example.officebooking.allocation.AllocationTypes.PreferenceType;
import com.example.officebooking.allocation.AllocationTypes.RunStatus;
import com.example.officebooking.allocation.AllocationTypes.Source;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AllocationResultRepository extends JpaRepository<AllocationResult, Long> {

    @Query("select r from AllocationResult r join fetch r.user where r.run.id = :runId "
            + "order by r.resultDate asc, r.user.lastName asc, r.user.firstName asc, r.id asc")
    List<AllocationResult> findByRunWithUser(@Param("runId") Long runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AllocationResult r where r.id = :id")
    Optional<AllocationResult> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.id from AllocationResult r where r.run.id = :runId and r.applyStatus = :status "
            + "order by r.resultDate asc, r.id asc")
    List<Long> findIdsByRunAndApplyStatus(@Param("runId") Long runId, @Param("status") ApplyStatus status);

    /** Результаты сотрудника за период из запусков в заданных статусах (для статуса предпочтений). */
    @Query("select r from AllocationResult r join fetch r.run where r.user.id = :userId "
            + "and r.resultDate between :from and :to and r.run.status in (:statuses)")
    List<AllocationResult> findForUser(@Param("userId") Long userId, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to, @Param("statuses") Collection<RunStatus> statuses);

    /** Есть ли у сотрудника на дату результат запуска, который блокирует смену предпочтения. */
    @Query("select count(r) from AllocationResult r where r.user.id = :userId and r.resultDate = :date "
            + "and r.run.status in (:statuses)")
    long countForUserOnDate(@Param("userId") Long userId, @Param("date") LocalDate date,
                            @Param("statuses") Collection<RunStatus> statuses);

    /** История для ротации: [userId, outcome, applyStatus] по офисным предпочтениям в подтверждённых запусках. */
    @Query("select r.user.id, r.outcome, r.applyStatus from AllocationResult r "
            + "where r.run.status = :runStatus and r.preference = :pref and r.source <> :existing")
    List<Object[]> findFairnessHistory(@Param("runStatus") RunStatus runStatus, @Param("pref") PreferenceType pref,
                                       @Param("existing") Source existing);

    /** Агрегаты по запускам: [runId, outcome, applyStatus, count]. */
    @Query("select r.run.id, r.outcome, r.applyStatus, count(r) from AllocationResult r "
            + "where r.run.id in (:runIds) group by r.run.id, r.outcome, r.applyStatus")
    List<Object[]> aggregateByRuns(@Param("runIds") Collection<Long> runIds);

    long countByRun_IdAndResultDateAndOutcomeAndSourceNotAndIdNot(Long runId, LocalDate date, Outcome outcome,
                                                                  Source source, Long id);

    List<AllocationResult> findByRun_IdAndUser_IdAndOutcomeAndSourceNot(Long runId, Long userId, Outcome outcome,
                                                                       Source source);
}
