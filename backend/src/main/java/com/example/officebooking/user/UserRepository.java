package com.example.officebooking.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    long countByEnabledTrue();

    long countByRoleAndEnabledTrue(Role role);

    List<User> findAllByEnabledTrueOrderByLastNameAscFirstNameAsc();

    List<User> findAllByOrderByLastNameAscFirstNameAsc();
}
