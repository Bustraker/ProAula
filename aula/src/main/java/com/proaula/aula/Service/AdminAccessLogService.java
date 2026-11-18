package com.proaula.aula.Service;

import com.proaula.aula.Entity.AdminAccessLog;
import com.proaula.aula.Entity.AdminCode;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.AdminAccessLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminAccessLogService {

    private final AdminAccessLogRepository accessLogRepository;

    public AdminAccessLogService(AdminAccessLogRepository accessLogRepository) {
        this.accessLogRepository = accessLogRepository;
    }

    @Transactional
    public AdminAccessLog recordSuccessfulLogin(Usuario user, AdminCode code) {
        if (user == null || user.getId() == null || code == null || code.getId() == null) {
            throw new IllegalArgumentException("La cuenta y el código ADMIN deben estar guardados.");
        }

        AdminAccessLog accessLog = new AdminAccessLog();
        accessLog.setAdminUserId(user.getId());
        accessLog.setUsername(user.getUsername());
        accessLog.setNombres(user.getNombres());
        accessLog.setApellidos(user.getApellidos());
        accessLog.setEmail(user.getEmail());
        accessLog.setAdminCodeId(code.getId());
        accessLog.setCodeOwnerEmail(code.getEmail());
        accessLog.setAccessedAt(Instant.now());
        return accessLogRepository.saveAndFlush(accessLog);
    }

    @Transactional(readOnly = true)
    public Page<AdminAccessLog> findAccessLogs(Pageable pageable) {
        return accessLogRepository.findAll(pageable);
    }
}
