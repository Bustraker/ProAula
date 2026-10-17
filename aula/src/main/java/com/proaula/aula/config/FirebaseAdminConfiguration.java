package com.proaula.aula.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseAdminConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.firebase.admin-enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(
            @Value("${app.firebase.project-id}") String projectId,
            @Value("${app.firebase.credentials-file:}") String credentialsFile,
            ResourceLoader resourceLoader) throws IOException {
        GoogleCredentials credentials;
        if (credentialsFile.isBlank()) {
            credentials = GoogleCredentials.getApplicationDefault();
        } else {
            Resource resource = resourceLoader.getResource(credentialsFile);
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Firebase credentials file does not exist or is not readable.");
            }
            try (InputStream inputStream = resource.getInputStream()) {
                credentials = GoogleCredentials.fromStream(inputStream);
            }
        }

        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(projectId)
            .build();
        return FirebaseMessaging.getInstance(
            FirebaseApp.getApps().isEmpty() ? FirebaseApp.initializeApp(options) : FirebaseApp.getInstance());
    }
}
