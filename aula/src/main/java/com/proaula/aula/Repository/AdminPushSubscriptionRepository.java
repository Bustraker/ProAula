package com.proaula.aula.Repository;

import com.proaula.aula.Entity.AdminPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminPushSubscriptionRepository extends JpaRepository<AdminPushSubscription, Long> {
    Optional<AdminPushSubscription> findByTokenHash(String tokenHash);
    List<AdminPushSubscription> findAllByAdminEmailIgnoreCase(String adminEmail);
    void deleteByTokenHash(String tokenHash);
}
