package com.example.officebooking.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    long countByEnabledTrue();

    List<User> findAllByEnabledTrueOrderByLastNameAscFirstNameAsc();

    List<User> findAllByOrderByLastNameAscFirstNameAsc();
}
