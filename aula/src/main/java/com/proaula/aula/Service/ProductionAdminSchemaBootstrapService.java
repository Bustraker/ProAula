package com.proaula.aula.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

@Component
@Order(0)
@Profile("prod")
public class ProductionAdminSchemaBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionAdminSchemaBootstrapService.class);
    private static final String[] PRODUCTION_SCHEMA_MIGRATIONS = {
        "classpath:db/seed/prod-admin-schema-bootstrap.sql",
        "classpath:db/migration/mysql/V20261017_01__admin_registration_approval.sql",
        "classpath:db/migration/mysql/V20261020_01__admin_request_review_and_code_delivery.sql",
        "classpath:db/migration/mysql/V20261030_01__create_favorite_trips.sql",
        "classpath:db/migration/mysql/V20261101_01__align_favorite_creation_timestamp.sql",
        "classpath:db/migration/mysql/V20261106_01__enhance_notificacion_table.sql"
    };

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;

    public ProductionAdminSchemaBootstrapService(DataSource dataSource, ResourceLoader resourceLoader) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String migration : PRODUCTION_SCHEMA_MIGRATIONS) {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            populator.setContinueOnError(false);
            populator.addScript(resourceLoader.getResource(migration));
            DatabasePopulatorUtils.execute(populator, dataSource);
        }
        log.info("Se verificaron y alinearon los esquemas de ADMIN, favoritos, trayectos y notificaciones.");
    }
}
