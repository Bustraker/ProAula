package com.proaula.aula.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.proaula.aula.Entity.Horario;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.HorarioRepository;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.exception.RutaFueraServicioException;

@ExtendWith(MockitoExtension.class)
class RutaServiceTest {

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private HorarioRepository horarioRepository;

    @InjectMocks
    private RutaService rutaService;

    @Test
    void getRutasVerificadasExcluyeRutasNoVerificadasYAntiguas() {
        Ruta verificada = new Ruta();
        verificada.setVerificada(true);
        Ruta noVerificada = new Ruta();
        noVerificada.setVerificada(false);
        Ruta antigua = new Ruta();
        antigua.setVerificada(null);
        when(rutaRepository.findAllByVerificadaTrue()).thenReturn(List.of(verificada));

        assertThat(rutaService.getRutasVerificadas()).containsExactly(verificada);
    }

    @Test
    void calcularHoraLlegadaEstimadaRechazaRutaSinHorariosActivos() {
        Ruta ruta = rutaActiva();
        when(rutaRepository.findById(1L)).thenReturn(Optional.of(ruta));
        when(horarioRepository.findByRutaIdAndEstadoOrderByHoraSalidaAsc(1L, Horario.Estado.ACTIVO))
            .thenReturn(List.of());

        assertThatThrownBy(() -> rutaService.calcularHoraLlegadaEstimada(
                1L, LocalDateTime.of(2026, 10, 29, 9, 0)))
            .isInstanceOf(RutaFueraServicioException.class)
            .hasMessage("La ruta no tiene horarios de servicio activos configurados.");
    }

    @Test
    void calcularHoraLlegadaEstimadaRechazaSalidaFueraDeLaVentanaDeServicio() {
        Horario horario = horarioActivo(LocalTime.of(8, 0), LocalTime.of(17, 0));
        configurarRutaConHorario(horario);

        assertThatThrownBy(() -> rutaService.calcularHoraLlegadaEstimada(
                1L, LocalDateTime.of(2026, 10, 29, 7, 59)))
            .isInstanceOf(RutaFueraServicioException.class)
            .hasMessage("La ruta está fuera de servicio en la fecha y hora indicadas.");
    }

    @Test
    void calcularHoraLlegadaEstimadaRechazaUnaRutaInactiva() {
        Ruta ruta = rutaActiva();
        ruta.setEstado(Ruta.Estado.INACTIVA);
        when(rutaRepository.findById(1L)).thenReturn(Optional.of(ruta));

        assertThatThrownBy(() -> rutaService.calcularHoraLlegadaEstimada(
                1L, LocalDateTime.of(2026, 10, 29, 9, 0)))
            .isInstanceOf(RutaFueraServicioException.class)
            .hasMessage("La ruta está fuera de servicio.");
    }

    @Test
    void calcularHoraLlegadaEstimadaRetornaLaLlegadaCuandoLaRutaEstaEnServicio() {
        Horario horario = horarioActivo(LocalTime.of(8, 0), LocalTime.of(17, 0));
        configurarRutaConHorario(horario);
        LocalDateTime salida = LocalDateTime.of(2026, 10, 29, 9, 0);

        assertThat(rutaService.calcularHoraLlegadaEstimada(1L, salida))
            .isEqualTo(salida.plusMinutes(45));
    }

    @Test
    void calcularHoraLlegadaEstimadaReconoceUnHorarioQueCruzaLaMedianoche() {
        Horario horario = horarioActivo(LocalTime.of(23, 0), LocalTime.of(1, 0));
        horario.setDiasSemana("JUEVES");
        configurarRutaConHorario(horario);
        LocalDateTime salida = LocalDateTime.of(2026, 10, 30, 0, 30);

        assertThat(rutaService.calcularHoraLlegadaEstimada(1L, salida))
            .isEqualTo(salida.plusMinutes(45));
    }

    private Ruta rutaActiva() {
        Ruta ruta = new Ruta();
        ruta.setId(1L);
        ruta.setEstado(Ruta.Estado.ACTIVA);
        ruta.setTiempoEstimadoMinutos(45);
        return ruta;
    }

    private Horario horarioActivo(LocalTime salida, LocalTime llegada) {
        Horario horario = new Horario();
        horario.setHoraSalida(salida);
        horario.setHoraLlegada(llegada);
        horario.setDiasSemana("LUNES,MARTES,MIERCOLES,JUEVES,VIERNES");
        horario.setTipoDia(Horario.TipoDia.HABIL);
        horario.setEstado(Horario.Estado.ACTIVO);
        return horario;
    }

    private void configurarRutaConHorario(Horario horario) {
        Ruta ruta = rutaActiva();
        when(rutaRepository.findById(1L)).thenReturn(Optional.of(ruta));
        when(horarioRepository.findByRutaIdAndEstadoOrderByHoraSalidaAsc(1L, Horario.Estado.ACTIVO))
            .thenReturn(List.of(horario));
    }
}