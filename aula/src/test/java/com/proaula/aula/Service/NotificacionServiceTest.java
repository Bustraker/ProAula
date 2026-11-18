package com.proaula.aula.Service;

import com.proaula.aula.Entity.Notificacion;
import com.proaula.aula.Entity.Usuario;
import com.proaula.aula.Repository.NotificacionRepository;
import com.proaula.aula.dto.NotificacionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;

    @Mock
    private UserPresenceService userPresenceService;

    @Mock
    private NotificationEmailService notificationEmailService;

    private NotificacionService notificacionService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        notificacionService = new NotificacionService(
                notificacionRepository,
                userPresenceService,
                notificationEmailService
        );

        usuario = new Usuario();
        usuario.setId(100L);
        usuario.setUsername("juan_pasajero");
        usuario.setEmail("juan@example.com");
        usuario.setNombres("Juan");
        usuario.setApellidos("Perez");
    }

    @Test
    void whenUserIsOnline_deliversInAppAndDoesNotSendEmail() {
        when(userPresenceService.estaEnLinea(100L)).thenReturn(true);
        when(notificacionRepository.save(any(Notificacion.class))).thenAnswer(invocation -> {
            Notificacion n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notificacion resultado = notificacionService.enviarNotificacion(
                usuario,
                "Bus en camino",
                "El bus T103 llegará en 4 minutos",
                "LLEGADA"
        );

        assertThat(resultado).isNotNull();
        assertThat(resultado.getCanalEnvio()).isEqualTo("IN_APP");
        verify(notificationEmailService, never()).enviarNotificacionEmail(any(), any(), any(), any(), any());
        verify(notificacionRepository).save(any(Notificacion.class));
    }

    @Test
    void whenUserIsOffline_deliversByEmailAndSavesNotification() {
        when(userPresenceService.estaEnLinea(100L)).thenReturn(false);
        when(notificacionRepository.save(any(Notificacion.class))).thenAnswer(invocation -> {
            Notificacion n = invocation.getArgument(0);
            n.setId(2L);
            return n;
        });

        Notificacion resultado = notificacionService.enviarNotificacion(
                usuario,
                "Incidente en tu ruta",
                "Desvío en Avenida Pedro de Heredia",
                "INCIDENTE"
        );

        assertThat(resultado).isNotNull();
        assertThat(resultado.getCanalEnvio()).isEqualTo("EMAIL");
        verify(notificationEmailService).enviarNotificacionEmail(
                eq("juan@example.com"),
                eq("Incidente en tu ruta"),
                eq("Desvío en Avenida Pedro de Heredia"),
                eq("INCIDENTE"),
                eq("Juan")
        );
        verify(notificacionRepository).save(any(Notificacion.class));
    }

    @Test
    void marcarComoLeida_updatesNotification() {
        Notificacion notif = new Notificacion();
        notif.setId(10L);
        notif.setUsuario(usuario);
        notif.setLeido(false);

        when(notificacionRepository.findByIdAndUsuario_Id(10L, 100L))
                .thenReturn(Optional.of(notif));

        notificacionService.marcarComoLeida(10L, 100L);

        assertThat(notif.getLeido()).isTrue();
        assertThat(notif.getFechaLeido()).isNotNull();
        verify(notificacionRepository).save(notif);
    }

    @Test
    void userPresenceService_tracksPresenceCorrectly() {
        UserPresenceService presence = new UserPresenceService();
        assertThat(presence.estaEnLinea(1L)).isFalse();

        presence.registrarActividad(1L);
        assertThat(presence.estaEnLinea(1L)).isTrue();

        presence.registrarDesconexion(1L);
        assertThat(presence.estaEnLinea(1L)).isFalse();
    }
}
