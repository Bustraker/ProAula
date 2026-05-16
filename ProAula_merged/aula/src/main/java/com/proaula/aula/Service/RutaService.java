package com.proaula.aula.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.RutaRepository;

@Service
public class RutaService {
    @Autowired
    private RutaRepository rutaRepository;

    public List<Ruta> getAllRutas() {
        return rutaRepository.findAll();
    }

    public Ruta getRutaById(Long id) {
        return rutaRepository.findById(id).orElse(null);
    }

    public Ruta saveRuta(Ruta ruta) {
        return rutaRepository.save(ruta);
    }

    public void deleteRuta(Long id) {
        rutaRepository.deleteById(id);
    }

    public long count() {
        return rutaRepository.count();
    }

    public List<Ruta> findTop4ByOrderByNombre() {
        return rutaRepository.findFirst4ByOrderByNombreAsc();
    }

    public long countActiveBuses() {
        return rutaRepository.findAll().stream()
            .mapToLong(ruta -> ruta.getBuses() != null ? ruta.getBuses().size() : 0)
            .sum();
    }

    public List<Ruta> findByBarrio(String barrio) {
        return rutaRepository.findAll().stream()
            .filter(ruta -> ruta.getBarrios() != null && ruta.getBarrios().contains(barrio))
            .collect(Collectors.toList());
    }

    public List<Ruta> findByNombreContaining(String nombre) {
        return rutaRepository.findAll().stream()
            .filter(ruta -> ruta.getNombre().toLowerCase().contains(nombre.toLowerCase()))
            .collect(Collectors.toList());
    }

    public Page<Ruta> findAllPaginated(Pageable pageable) {
        return rutaRepository.findAll(pageable);
    }

    public Optional<Ruta> findById(Long id) {
        return rutaRepository.findById(id);
    }
}