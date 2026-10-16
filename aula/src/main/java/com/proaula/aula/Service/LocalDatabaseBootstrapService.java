package com.proaula.aula.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

@Component
@Order(0)
@Profile({"local", "validation"})
public class LocalDatabaseBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDatabaseBootstrapService.class);
    private static final String REFERENCE_DATA_SCRIPT =
        "classpath:db/seed/local-reference-data.sql";
    private static final String MYSQL_ANALYTICS_SCRIPT =
        "classpath:db/seed/mysql-analytics-schema.sql";

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;
    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    public LocalDatabaseBootstrapService(DataSource dataSource,
                                         ResourceLoader resourceLoader,
                                         Environment environment,
                                         JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String databaseProduct = getDatabaseProduct();
        if ("MySQL".equalsIgnoreCase(databaseProduct)
                && environment.acceptsProfiles(Profiles.of("local"))) {
            executeScript(MYSQL_ANALYTICS_SCRIPT);
            ensureSyntheticDemandSourceValue();
        }

        executeScript(REFERENCE_DATA_SCRIPT);
        log.info("Datos de referencia locales cargados desde SQL.");
    }

    private void ensureSyntheticDemandSourceValue() {
        String columnType = jdbcTemplate.queryForObject(
            "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            String.class, "demanda_historica", "fuente_datos");
        if (columnType == null) {
            throw new IllegalStateException(
                "No se encontró demanda_historica.fuente_datos para preparar los datos sintéticos locales.");
        }
        if (!needsSyntheticDemandEnumMigration(columnType)) {
            return;
        }

        String expandedColumnType = appendSyntheticDemandEnumValue(columnType);
        jdbcTemplate.execute("ALTER TABLE `demanda_historica` MODIFY COLUMN `fuente_datos` "
            + expandedColumnType + " NOT NULL DEFAULT 'CONSOLIDADO'");
        log.info("Se habilitó la fuente SINTETICO en demanda_historica.fuente_datos.");
    }

    static boolean needsSyntheticDemandEnumMigration(String columnType) {
        String normalizedType = columnType.toLowerCase(java.util.Locale.ROOT);
        return normalizedType.startsWith("enum(") && !normalizedType.contains("'sintetico'");
    }

    static String appendSyntheticDemandEnumValue(String columnType) {
        if (!needsSyntheticDemandEnumMigration(columnType)) {
            return columnType;
        }

        int closingParenthesis = columnType.lastIndexOf(')');
        if (closingParenthesis < "enum(".length()) {
            throw new IllegalArgumentException("Definición ENUM inválida para demanda_historica.fuente_datos.");
        }
        return columnType.substring(0, closingParenthesis) + ",'SINTETICO'"
            + columnType.substring(closingParenthesis);
    }

    private String getDatabaseProduct() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "No se pudo identificar la base de datos para inicializar datos locales.", exception);
        }
    }

    private void executeScript(String scriptLocation) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.addScript(resourceLoader.getResource(scriptLocation));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
