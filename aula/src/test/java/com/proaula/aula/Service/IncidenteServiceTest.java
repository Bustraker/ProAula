package com.proaula.aula.Service;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.IncidenteRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.Repository.UsuarioRepository;
import com.proaula.aula.dto.IncidentePanelResponse;
import com.proaula.aula.dto.IncidenteReportRequest;
import com.proaula.aula.exception.AulaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidenteServiceTest {

    @Mock
    private IncidenteRepository incidenteRepository;

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private IncidenteService incidenteService;

    @Test
    void reportarStoresTrimmedDescriptionAndAuthenticatedReporter() {
        Usuario usuario = new Usuario();
        usuario.setUsername("reporter");
        Ruta ruta = new Ruta();
        ruta.setId(42L);
        IncidenteReportRequest solicitud = new IncidenteReportRequest();
        solicitud.setTipo(IncidenteReportRequest.TipoIncidente.ACCIDENTE);
        solicitud.setRutaId(ruta.getId());
        solicitud.setDescripcion("  Colisión en la vía.  ");

        when(usuarioRepository.findByUsername("reporter")).thenReturn(usuario);
        when(rutaRepository.findById(ruta.getId())).thenReturn(Optional.of(ruta));
        when(incidenteRepository.saveAndFlush(any(Incidente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Incidente incidente = incidenteService.reportar(solicitud, "reporter");

        assertThat(incidente.getUsuario()).isSameAs(usuario);
        assertThat(incidente.getRuta()).isSameAs(ruta);
        assertThat(incidente.getTipo()).isEqualTo("ACCIDENTE");
        assertThat(incidente.getTitulo()).isEqualTo("Accidente reportado");
        assertThat(incidente.getDescripcion()).isEqualTo("Colisión en la vía.");
        assertThat(incidente.getEstado()).isEqualTo("PENDIENTE");
        verify(incidenteRepository).saveAndFlush(incidente);
    }

    @Test
    void listarNormalizesFiltersAndMapsIncidentForPanel() {
        Ruta ruta = new Ruta();
        ruta.setNombre("Centro - Manga");
        Incidente incidente = new Incidente();
        incidente.setRuta(ruta);
        incidente.setTipo("RETRASO");
        incidente.setDescripcion("Demora en el recorrido");
        when(incidenteRepository.findForPanel("RETRASO", "Centro")).thenReturn(List.of(incidente));

        List<IncidentePanelResponse> respuesta = incidenteService.listar(" RETRASO ", " Centro ");

        assertThat(respuesta).singleElement().satisfies(item -> {
            assertThat(item.ruta()).isEqualTo("Centro - Manga");
            assertThat(item.tipo()).isEqualTo("RETRASO");
            assertThat(item.descripcion()).isEqualTo("Demora en el recorrido");
        });
        verify(incidenteRepository).findForPanel("RETRASO", "Centro");
    }

    @Test
    void reportarFailsWhenAuthenticatedUserDoesNotExist() {
        when(usuarioRepository.findByUsername("missing")).thenReturn(null);

        assertThatThrownBy(() -> incidenteService.reportar(new IncidenteReportRequest(), "missing"))
                .isInstanceOf(AulaException.class)
                .hasMessage("No se encontró el usuario autenticado.");

        verify(rutaRepository, never()).findById(any());
        verify(incidenteRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportarFailsWhenRouteDoesNotExist() {
        IncidenteReportRequest solicitud = new IncidenteReportRequest();
        solicitud.setRutaId(404L);
        when(usuarioRepository.findByUsername("reporter")).thenReturn(new Usuario());
        when(rutaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidenteService.reportar(solicitud, "reporter"))
                .isInstanceOf(AulaException.class)
                .hasMessage("La ruta seleccionada no existe.");

        verify(incidenteRepository, never()).saveAndFlush(any());
    }

    @Test
    void cambiarEstadoNormalizesAndSavesAnAllowedStatus() {
        Incidente incidente = new Incidente();
        when(incidenteRepository.findById(12L)).thenReturn(Optional.of(incidente));
        when(incidenteRepository.saveAndFlush(incidente)).thenReturn(incidente);

        Incidente resultado = incidenteService.cambiarEstado(12L, " resuelto ");

        assertThat(resultado.getEstado()).isEqualTo("RESUELTO");
        verify(incidenteRepository).saveAndFlush(incidente);
    }

    @Test
    void cambiarEstadoRejectsAnUnsupportedStatusWithoutLoadingIncident() {
        assertThatThrownBy(() -> incidenteService.cambiarEstado(12L, "CANCELADO"))
                .isInstanceOf(AulaException.class)
                .hasMessage("El estado indicado no es válido.");

        verify(incidenteRepository, never()).findById(any());
        verify(incidenteRepository, never()).saveAndFlush(any());
    }

    @Test
    void cambiarEstadoRejectsMissingIncident() {
        when(incidenteRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidenteService.cambiarEstado(404L, "RESUELTO"))
                .isInstanceOf(AulaException.class)
                .hasMessage("No se encontró el incidente solicitado.");

        verify(incidenteRepository, never()).saveAndFlush(any());
    }

    @Test
    void cambiarEstadoRejectsInvalidIdAndBlankStatus() {
        assertThatThrownBy(() -> incidenteService.cambiarEstado(0L, "RESUELTO"))
                .isInstanceOf(AulaException.class)
                .hasMessage("El identificador del incidente no es válido.");
        assertThatThrownBy(() -> incidenteService.cambiarEstado(12L, " "))
                .isInstanceOf(AulaException.class)
                .hasMessage("Indica el estado del incidente.");

        verify(incidenteRepository, never()).findById(any());
        verify(incidenteRepository, never()).saveAndFlush(any());
    }
}
