package com.example.officebooking.settings;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Integer> {

    /** FOR SHARE: бронирования не блокируют друг друга, но изменение настроек дожидается их завершения. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from AppSettings s where s.id = 1")
    Optional<AppSettings> findSharedLocked();

    /** FOR UPDATE: изменение настроек сериализуется с бронированиями. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppSettings s where s.id = 1")
    Optional<AppSettings> findExclusiveLocked();
}
