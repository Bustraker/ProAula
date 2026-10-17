package com.proaula.aula.Repository;

import com.proaula.aula.Entity.AdminCodeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AdminCodeRequestRepository extends JpaRepository<AdminCodeRequest, Long> {
    boolean existsByEmailIgnoreCaseAndStatus(String email, AdminCodeRequest.Status status);
    List<AdminCodeRequest> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AdminCodeRequest request where request.id = :id")
    Optional<AdminCodeRequest> findByIdForUpdate(@Param("id") long id);
}
