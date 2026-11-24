package com.example.officebooking.presence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresenceRepository extends JpaRepository<PresenceRecord, Long>, JpaSpecificationExecutor<PresenceRecord> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PresenceRecord p where p.id = :id")
    Optional<PresenceRecord> findByIdForUpdate(@Param("id") Long id);

    Optional<PresenceRecord> findByUser_IdAndPresenceDateAndStatus(Long userId, LocalDate date, PresenceStatus status);

    long countByPresenceDateAndTypeAndStatus(LocalDate date, PresenceType type, PresenceStatus status);

    long countByUser_IdAndTypeAndStatusAndPresenceDateBetween(Long userId, PresenceType type, PresenceStatus status,
                                                              LocalDate from, LocalDate to);

    List<PresenceRecord> findByUser_IdAndTypeAndPresenceDateBetweenOrderByPresenceDateDescIdDesc(
            Long userId, PresenceType type, LocalDate from, LocalDate to);

    List<PresenceRecord> findByUser_IdAndStatusAndPresenceDateBetween(Long userId, PresenceStatus status,
                                                                      LocalDate from, LocalDate to);

    @Query("select p from PresenceRecord p join fetch p.user where p.presenceDate = :date and p.status = :status")
    List<PresenceRecord> findAllByDateAndStatusWithUser(@Param("date") LocalDate date,
                                                        @Param("status") PresenceStatus status);

    /** Количество записей заданного типа по датам: [LocalDate, Long]. */
    @Query("select p.presenceDate, count(p) from PresenceRecord p "
            + "where p.type = :type and p.status = :status and p.presenceDate between :from and :to "
            + "group by p.presenceDate")
    List<Object[]> countByDate(@Param("type") PresenceType type, @Param("status") PresenceStatus status,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Будущие даты, на которые занято больше мест, чем новая вместимость: [LocalDate, Long]. */
    @Query("select p.presenceDate, count(p) from PresenceRecord p "
            + "where p.type = :type and p.status = :status and p.presenceDate >= :from "
            + "group by p.presenceDate having count(p) > :capacity order by p.presenceDate")
    List<Object[]> findDatesOverCapacity(@Param("type") PresenceType type, @Param("status") PresenceStatus status,
                                         @Param("from") LocalDate from, @Param("capacity") long capacity);

    List<PresenceRecord> findByUser_IdAndTypeAndStatusAndPresenceDateBetween(Long userId, PresenceType type,
                                                                             PresenceStatus status, LocalDate from, LocalDate to);

    long countByStatusAndPresenceDateBetween(PresenceStatus status, LocalDate from, LocalDate to);

    /** Все записи периода с пользователями (для командного календаря). */
    @Query("select p from PresenceRecord p join fetch p.user where p.status = :status "
            + "and p.presenceDate between :from and :to")
    List<PresenceRecord> findAllByStatusInRangeWithUser(@Param("status") PresenceStatus status,
                                                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Активные записи пользователя начиная с даты (для деактивации учётной записи). */
    @Query("select p from PresenceRecord p where p.user.id = :userId and p.status = :status "
            + "and p.presenceDate >= :from")
    List<PresenceRecord> findActiveForUserFrom(@Param("userId") Long userId, @Param("status") PresenceStatus status,
                                               @Param("from") LocalDate from);

    /** Агрегаты по пользователям: [userId, type, status, count]. */
    @Query("select p.user.id, p.type, p.status, count(p) from PresenceRecord p "
            + "where p.presenceDate between :from and :to group by p.user.id, p.type, p.status")
    List<Object[]> aggregateByUser(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
