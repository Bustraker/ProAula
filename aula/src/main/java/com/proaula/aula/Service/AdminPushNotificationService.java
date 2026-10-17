package com.proaula.aula.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.proaula.aula.Entity.AdminPushSubscription;
import com.proaula.aula.Repository.AdminPushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AdminPushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminPushNotificationService.class);
    private final AdminPushSubscriptionRepository subscriptionRepository;
    private final ObjectProvider<FirebaseMessaging> firebaseMessaging;
    private final String approverEmail;
    private final String publicBaseUrl;

    public AdminPushNotificationService(
            AdminPushSubscriptionRepository subscriptionRepository,
            ObjectProvider<FirebaseMessaging> firebaseMessaging,
            @Value("${app.admin.approver-email:}") String approverEmail,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this.subscriptionRepository = subscriptionRepository;
        this.firebaseMessaging = firebaseMessaging;
        this.approverEmail = approverEmail;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public void registerDevice(String email, String token) {
        if (token == null || token.isBlank() || token.length() > 4096) {
            throw new IllegalArgumentException("El token de notificación no es válido.");
        }
        String tokenHash = hash(token);
        AdminPushSubscription subscription = subscriptionRepository.findByTokenHash(tokenHash)
            .orElseGet(AdminPushSubscription::new);
        subscription.setAdminEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
        subscription.setDeviceToken(token);
        subscription.setTokenHash(tokenHash);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void removeDevice(String email, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        subscriptionRepository.findByTokenHash(hash(token))
            .filter(subscription -> subscription.getAdminEmail().equalsIgnoreCase(email))
            .ifPresent(subscriptionRepository::delete);
    }

    public void notifyPendingRequest(long requestId) {
        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
        if (messaging == null) {
            log.warn("Firebase push is not configured; admin request {} was sent by email only.", requestId);
            return;
        }

        String link = publicBaseUrl.replaceAll("/+$", "") + "/admin/solicitudes-codigo";
        for (AdminPushSubscription subscription : subscriptionRepository.findAllByAdminEmailIgnoreCase(approverEmail)) {
            Message message = Message.builder()
                .setToken(subscription.getDeviceToken())
                .setNotification(Notification.builder()
                    .setTitle("Solicitud de acceso ADMIN")
                    .setBody("Hay una solicitud nueva para revisar.")
                    .build())
                .putData("link", link)
                .setWebpushConfig(WebpushConfig.builder()
                    .setFcmOptions(WebpushFcmOptions.builder().setLink(link).build())
                    .build())
                .build();
            try {
                messaging.send(message);
            } catch (FirebaseMessagingException exception) {
                log.error("Could not deliver Firebase push for admin request {} (error code {}).",
                    requestId, exception.getMessagingErrorCode(), exception);
                if (exception.getMessagingErrorCode() == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED
                        || exception.getMessagingErrorCode() == com.google.firebase.messaging.MessagingErrorCode.INVALID_ARGUMENT) {
                    subscriptionRepository.delete(subscription);
                }
            }
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
