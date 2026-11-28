package com.example.officebooking.allocation;

import com.example.officebooking.allocation.AllocationTypes.RunStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AllocationRunRepository extends JpaRepository<AllocationRun, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AllocationRun r where r.id = :id")
    Optional<AllocationRun> findByIdForUpdate(@Param("id") Long id);

    List<AllocationRun> findTop50ByOrderByIdDesc();

    @Query("select r from AllocationRun r where r.status in (:statuses) and r.rangeFrom <= :to and r.rangeTo >= :from")
    List<AllocationRun> findOverlapping(@Param("statuses") Collection<RunStatus> statuses,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);
}
