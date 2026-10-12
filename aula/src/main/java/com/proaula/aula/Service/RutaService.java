package com.proaula.aula.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.RutaParada;
import com.proaula.aula.Entity.Parada;
import com.proaula.aula.exception.AulaException;
import com.proaula.aula.Repository.BusRepository;
import com.proaula.aula.Repository.ParadaRepository;
import com.proaula.aula.Repository.RutaParadaRepository;
import com.proaula.aula.Repository.HorarioRepository;
import com.proaula.aula.Repository.AsignacionRutaBusRepository;
import com.proaula.aula.Repository.ViajeRepository;
import com.proaula.aula.Repository.PasajeRepository;
import com.proaula.aula.Repository.RutaRepository;

@Service
public class RutaService {
    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private ParadaRepository paradaRepository;

    @Autowired
    private RutaParadaRepository rutaParadaRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    private AsignacionRutaBusRepository asignacionRutaBusRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private PasajeRepository pasajeRepository;

    @Transactional(readOnly = true)
    public List<Ruta> getAllRutas() {
        List<Ruta> rutas = rutaRepository.findAll();
        rutas.forEach(this::initializeDisplayCollections);
        return rutas;
    }

    @Transactional(readOnly = true)
    public List<Ruta> getRutasVerificadas() {
        List<Ruta> rutas = rutaRepository.findAllByVerificadaTrue();
        rutas.forEach(this::initializeDisplayCollections);
        return rutas;
    }

    @Transactional(readOnly = true)
    public Ruta getRutaById(Long id) {
        Ruta ruta = rutaRepository.findById(id).orElse(null);
        if (ruta != null) {
            initializeDisplayCollections(ruta);
        }
        return ruta;
    }

    @Transactional(readOnly = true)
    public List<RutaParada> getParadasPorSentido(Long rutaId, RutaParada.Sentido sentido) {
        return rutaParadaRepository.findByRutaIdAndSentidoOrderByOrdenAsc(rutaId, sentido);
    }

    @Transactional(readOnly = true)
    public List<Parada> getParadasPorRuta(Long rutaId) {
        return paradaRepository.findByRuta_IdOrderByOrdenAsc(rutaId);
    }

    public Ruta saveRuta(Ruta ruta) {
        return rutaRepository.save(ruta);
    }

    @Transactional
    public void deleteRuta(Long id) {
        if (id == null || !rutaRepository.existsById(id)) {
            return;
        }
        if (hasRelatedRecords(id)) {
            throw new AulaException(
                "No se puede eliminar la ruta porque tiene buses, paradas, horarios, asignaciones o historial asociado.",
                "RESOURCE_IN_USE");
        }
        rutaRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean hasRelatedRecords(Long id) {
        return id != null && (busRepository.existsByRuta_Id(id)
                || paradaRepository.existsByRuta_Id(id)
                || rutaParadaRepository.existsByRuta_Id(id)
                || horarioRepository.existsByRuta_Id(id)
                || asignacionRutaBusRepository.existsByRuta_Id(id)
                || viajeRepository.existsByRuta_Id(id)
                || pasajeRepository.existsByRuta_Id(id));
    }

    public long count() {
        return rutaRepository.count();
    }

    @Transactional(readOnly = true)
    public List<Ruta> findTop4ByOrderByNombre() {
        List<Ruta> rutas = rutaRepository.findFirst4ByOrderByNombreAsc();
        rutas.forEach(this::initializeDisplayCollections);
        return rutas;
    }

    @Transactional(readOnly = true)
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

    private void initializeDisplayCollections(Ruta ruta) {
        ruta.getBarrios().size();
        ruta.getBarriosOrdenados().size();
        ruta.getBuses().size();
        ruta.getParadas().size();
    }
}