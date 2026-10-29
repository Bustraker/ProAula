package com.proaula.aula.Service;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.IncidenteRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.IncidenteReportRequest;
import com.proaula.aula.exception.AulaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IncidenteService {

    private final IncidenteRepository incidenteRepository;
    private final RutaRepository rutaRepository;
    private final UsuarioRepository usuarioRepository;

    public IncidenteService(IncidenteRepository incidenteRepository,
                            RutaRepository rutaRepository,
                            UsuarioRepository usuarioRepository) {
        this.incidenteRepository = incidenteRepository;
        this.rutaRepository = rutaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<Ruta> getRutas() {
        return rutaRepository.findAll();
    }

    @Transactional
    public Incidente reportar(IncidenteReportRequest solicitud, String username) {
        Usuario usuario = usuarioRepository.findByUsername(username);
        if (usuario == null) {
            throw new AulaException("No se encontró el usuario autenticado.", "USER_NOT_FOUND");
        }

        Ruta ruta = rutaRepository.findById(solicitud.getRutaId())
                .orElseThrow(() -> new AulaException("La ruta seleccionada no existe.", "ROUTE_NOT_FOUND"));

        Incidente incidente = new Incidente();
        incidente.setUsuario(usuario);
        incidente.setRuta(ruta);
        incidente.setTipo(solicitud.getTipo().name());
        incidente.setTitulo(tituloPara(solicitud.getTipo()));
        incidente.setDescripcion(solicitud.getDescripcion().trim());
        return incidenteRepository.saveAndFlush(incidente);
    }

    private String tituloPara(IncidenteReportRequest.TipoIncidente tipo) {
        return switch (tipo) {
            case RETRASO -> "Retraso reportado";
            case ACCIDENTE -> "Accidente reportado";
            case FALLA_SERVICIO -> "Falla del bus o del servicio";
            case RUTA_DESVIADA -> "Desvío de ruta reportado";
            case OTRO -> "Incidente reportado";
        };
    }
}
