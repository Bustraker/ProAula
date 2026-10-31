package com.proaula.aula.Service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.dto.CoordenadasParadaRequest;

@Service
public class ParadaCoordenadasService {
    private final ParadaRepository paradaRepository;

    public ParadaCoordenadasService(ParadaRepository paradaRepository) {
        this.paradaRepository = paradaRepository;
    }

    @Transactional(readOnly = true)
    public List<Parada> listarParadas() {
        return paradaRepository.findAllWithRutaOrderByRutaAndOrden();
    }

    @Transactional
    public Optional<Parada> actualizar(Long paradaId, CoordenadasParadaRequest coordenadas) {
        return paradaRepository.findById(paradaId).map(parada -> {
            parada.setLatitud(coordenadas.latitud());
            parada.setLongitud(coordenadas.longitud());
            return paradaRepository.saveAndFlush(parada);
        });
    }
}
