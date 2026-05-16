package com.proaula.aula.Service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proaula.aula.Entity.AdminCode;
import com.proaula.aula.Repository.AdminCodeRepository;

@Service
public class AdminCodeService {

    @Autowired
    private AdminCodeRepository adminCodeRepository;

    public boolean isValidAdminCode(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return false;
        }
        return adminCodeRepository.existsByCodigoIgnoreCase(codigo.trim());
    }

    public AdminCode findByCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return null;
        }
        return adminCodeRepository.findByCodigoIgnoreCase(codigo.trim()).orElse(null);
    }

    @Transactional
    public void ensureDefaultAdminCodes(List<String> defaultCodes) {
        if (defaultCodes == null || defaultCodes.isEmpty()) {
            return;
        }

        for (String codigo : defaultCodes) {
            if (codigo == null || codigo.trim().isEmpty()) {
                continue;
            }
            String trimmedCode = codigo.trim();
            if (!adminCodeRepository.existsByCodigoIgnoreCase(trimmedCode)) {
                AdminCode adminCode = new AdminCode(trimmedCode);
                adminCodeRepository.save(adminCode);
            }
        }
    }
}
