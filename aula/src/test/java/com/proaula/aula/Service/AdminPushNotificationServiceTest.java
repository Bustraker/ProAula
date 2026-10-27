package com.proaula.aula.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.proaula.aula.Entity.AdminPushSubscription;
import com.proaula.aula.Repository.AdminPushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPushNotificationServiceTest {

    @Test
    void sendsPendingRequestNotificationToEveryRegisteredDevice() throws Exception {
        AdminPushSubscriptionRepository repository = mock(AdminPushSubscriptionRepository.class);
        ObjectProvider<FirebaseMessaging> messagingProvider = mock(ObjectProvider.class);
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        AdminPushSubscription firstDevice = subscription("first-device-token");
        AdminPushSubscription secondDevice = subscription("second-device-token");
        when(messagingProvider.getIfAvailable()).thenReturn(messaging);
        when(repository.findAllByAdminEmailIgnoreCase("approver@example.com"))
            .thenReturn(List.of(firstDevice, secondDevice));
        when(messaging.send(any(Message.class))).thenReturn("message-id");

        AdminPushNotificationService service = new AdminPushNotificationService(
            repository, messagingProvider, "approver@example.com", "https://bustraker.example", "vapid-key");

        assertThat(service.isPushConfigured()).isTrue();
        assertThat(service.notifyPendingRequest(
            42L, "Andrea", "Perez", "applicant@example.com")).isTrue();
        org.mockito.ArgumentCaptor<Message> messageCaptor =
            org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messaging, org.mockito.Mockito.times(2)).send(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).hasSize(2);
        assertThat(AdminPushNotificationService.requestNotificationTitle())
            .isEqualTo("Nueva solicitud de acceso ADMIN");
        assertThat(AdminPushNotificationService.requestNotificationBody(
                "Andrea", "Perez", "applicant@example.com"))
            .contains("Nombres: Andrea", "Apellidos: Perez", "Correo: applicant@example.com")
            .doesNotContain("password", "contraseña");
    }

    @Test
    void pushIsNotReportedAsConfiguredWithoutServerMessagingOrVapidKey() {
        AdminPushSubscriptionRepository repository = mock(AdminPushSubscriptionRepository.class);
        ObjectProvider<FirebaseMessaging> messagingProvider = mock(ObjectProvider.class);
        when(messagingProvider.getIfAvailable()).thenReturn(null);

        AdminPushNotificationService missingServerConfig = new AdminPushNotificationService(
            repository, messagingProvider, "approver@example.com", "https://bustraker.example", "vapid-key");
        AdminPushNotificationService missingVapidKey = new AdminPushNotificationService(
            repository, messagingProvider, "approver@example.com", "https://bustraker.example", "");

        assertThat(missingServerConfig.isPushConfigured()).isFalse();
        assertThat(missingVapidKey.isPushConfigured()).isFalse();
    }

    private AdminPushSubscription subscription(String token) {
        AdminPushSubscription subscription = new AdminPushSubscription();
        subscription.setAdminEmail("approver@example.com");
        subscription.setDeviceToken(token);
        return subscription;
    }
}
