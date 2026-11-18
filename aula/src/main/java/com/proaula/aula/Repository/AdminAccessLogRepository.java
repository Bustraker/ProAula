package com.proaula.aula.Repository;

import com.proaula.aula.Entity.AdminAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {
}
