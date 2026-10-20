package com.proaula.aula.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proaula.aula.Service.AdminPushNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class FirebaseWebController {

    private final ObjectMapper objectMapper;
    private final FirebaseWebConfig firebaseConfig;
    private final AdminPushNotificationService pushNotificationService;

    public FirebaseWebController(
            ObjectMapper objectMapper,
            AdminPushNotificationService pushNotificationService,
            @Value("${app.firebase.api-key:}") String apiKey,
            @Value("${app.firebase.auth-domain:}") String authDomain,
            @Value("${app.firebase.project-id:}") String projectId,
            @Value("${app.firebase.storage-bucket:}") String storageBucket,
            @Value("${app.firebase.messaging-sender-id:}") String messagingSenderId,
            @Value("${app.firebase.app-id:}") String appId,
            @Value("${app.firebase.measurement-id:}") String measurementId,
            @Value("${app.firebase.vapid-key:}") String vapidKey) {
        this.objectMapper = objectMapper;
        this.pushNotificationService = pushNotificationService;
        this.firebaseConfig = new FirebaseWebConfig(apiKey, authDomain, projectId, storageBucket,
            messagingSenderId, appId, measurementId, vapidKey);
    }

    @GetMapping("/api/firebase/config")
    public Map<String, Object> config() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("apiKey", firebaseConfig.apiKey());
        response.put("authDomain", firebaseConfig.authDomain());
        response.put("projectId", firebaseConfig.projectId());
        response.put("storageBucket", firebaseConfig.storageBucket());
        response.put("messagingSenderId", firebaseConfig.messagingSenderId());
        response.put("appId", firebaseConfig.appId());
        response.put("measurementId", firebaseConfig.measurementId());
        response.put("vapidKey", firebaseConfig.vapidKey());
        response.put("pushConfigured", pushNotificationService.isPushConfigured());
        return response;
    }

    @GetMapping(value = "/firebase-messaging-sw.js", produces = "application/javascript")
    public String serviceWorker() {
        try {
            String config = objectMapper.writeValueAsString(firebaseConfig.asFirebaseConfig());
            return """
                importScripts("https://www.gstatic.com/firebasejs/13.0.0/firebase-app-compat.js");
                importScripts("https://www.gstatic.com/firebasejs/13.0.0/firebase-messaging-compat.js");
                firebase.initializeApp(%s);
                const messaging = firebase.messaging();
                messaging.onBackgroundMessage(payload => {
                  const notification = payload.notification || {};
                  self.registration.showNotification(notification.title || "Bustraker", {
                    body: notification.body || "Hay una solicitud ADMIN para revisar.",
                    icon: "/images/logo.png",
                    data: payload.data || { link: "/admin/solicitudes-codigo" }
                  });
                });
                self.addEventListener("notificationclick", event => {
                  event.notification.close();
                  const target = event.notification.data && event.notification.data.link
                    ? event.notification.data.link : "/admin/solicitudes-codigo";
                  event.waitUntil(clients.openWindow(target));
                });
                """.formatted(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize public Firebase configuration.", exception);
        }
    }

    private record FirebaseWebConfig(
        String apiKey,
        String authDomain,
        String projectId,
        String storageBucket,
        String messagingSenderId,
        String appId,
        String measurementId,
        String vapidKey
    ) {
        Map<String, String> asFirebaseConfig() {
            Map<String, String> config = new LinkedHashMap<>();
            config.put("apiKey", apiKey);
            config.put("authDomain", authDomain);
            config.put("projectId", projectId);
            config.put("storageBucket", storageBucket);
            config.put("messagingSenderId", messagingSenderId);
            config.put("appId", appId);
            if (!measurementId.isBlank()) {
                config.put("measurementId", measurementId);
            }
            return config;
        }
    }
}
