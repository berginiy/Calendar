package com.example.officebooking.task;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyTaskRepository extends JpaRepository<DailyTask, Long>, JpaSpecificationExecutor<DailyTask> {

    @Query("select t from DailyTask t join fetch t.assignee join fetch t.createdBy "
            + "where t.assignee.id = :userId and t.taskDate = :date "
            + "order by t.dueTime asc nulls last, t.id asc")
    List<DailyTask> findForUserOnDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("select t from DailyTask t join fetch t.assignee join fetch t.createdBy "
            + "where t.assignee.id = :userId and t.taskDate between :from and :to "
            + "order by t.taskDate asc, t.dueTime asc nulls last, t.id asc")
    List<DailyTask> findForUserInRange(@Param("userId") Long userId, @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    @Query("select t from DailyTask t join fetch t.assignee join fetch t.createdBy where t.id = :id")
    Optional<DailyTask> findByIdWithUsers(@Param("id") Long id);
}
