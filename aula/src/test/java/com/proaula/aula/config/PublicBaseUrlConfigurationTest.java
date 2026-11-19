package com.proaula.aula.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class PublicBaseUrlConfigurationTest {

    @Test
    void usesRenderExternalUrlInsteadOfLocalOverride() {
        Map<String, Object> renderProperties = Map.of(
            "RENDER_EXTERNAL_URL", "https://proaula.onrender.com",
            "APP_PUBLIC_BASE_URL", "http://localhost:5679");

        assertThat(resolvePublicBaseUrl("application.yml", renderProperties))
            .isEqualTo("https://proaula.onrender.com");
        assertThat(resolvePublicBaseUrl("application-prod.yml", renderProperties))
            .isEqualTo("https://proaula.onrender.com");
    }

    @Test
    void keepsLocalhostAsTheFallbackForLocalDevelopment() {
        assertThat(resolvePublicBaseUrl("application.yml", Map.of()))
            .isEqualTo("http://localhost:5679");
    }

    private String resolvePublicBaseUrl(String configFile, Map<String, Object> environmentProperties) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(configFile));
        Properties applicationProperties = yaml.getObject();
        assertThat(applicationProperties).isNotNull();

        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(new MapPropertySource("environment", environmentProperties));
        propertySources.addLast(new PropertiesPropertySource("application", applicationProperties));
        return new PropertySourcesPropertyResolver(propertySources).getProperty("app.public-base-url");
    }
}
