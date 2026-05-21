package com.proaula.aula.Service;

import java.util.Collections;
import java.util.List;

import com.proaula.aula.Entity.Viaje;
import com.proaula.aula.Repository.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ViajeService {
    @Autowired
    private ViajeRepository viajeRepository;

    public List<Viaje> getViajesByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return viajeRepository.findByUsuarioUsernameIgnoreCase(username);
    }

    public Viaje getViajeDetalle(Long id, String username) {
        if (id == null || username == null || username.trim().isEmpty()) {
            return null;
        }
        return viajeRepository.findByIdAndUsuarioUsernameIgnoreCase(id, username).orElse(null);
    }

    public Viaje saveViaje(Viaje viaje) {
        return viajeRepository.save(viaje);
    }
}