package com.MisMascotas.backend.Configuracion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adopta únicamente los puntos de partida conocidos de QA, sin reiniciar datos. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
public class MigracionesQaConfig {

    @Bean
    public FlywayMigrationStrategy migracionVerificada(
            @Value("${mismascotas.migraciones.adoptar-base-qa:false}") boolean adoptarBaseQa)
            throws IOException {
        String consulta = recurso("db/qa/huella_esquema.sql");
        String huellaAnterior = recurso("db/qa/huella_base_v1.txt").trim();
        String huellaActual = recurso("db/qa/huella_base_v9.txt").trim();
        return flyway -> {
            if (flyway.getConfiguration().isBaselineOnMigrate()) {
                throw new IllegalStateException("No se permite baseline-on-migrate sin verificar el esquema");
            }
            JdbcTemplate sql = new JdbcTemplate(flyway.getConfiguration().getDataSource());
            boolean historial = Boolean.TRUE.equals(sql.queryForObject(
                    "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL", Boolean.class));
            boolean objetos = Boolean.TRUE.equals(sql.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'public' AND c.relkind IN ('r','p','v','m','S','f'))
                    """, Boolean.class));
            if (!historial && objetos && adoptarBaseQa) {
                String huella = sql.queryForObject(consulta, String.class);
                String version = huellaActual.equals(huella) ? "9" : huellaAnterior.equals(huella) ? "1" : null;
                if (!MigrationVersion.fromVersion("1").equals(flyway.getConfiguration().getBaselineVersion())
                        || version == null) {
                    throw new IllegalStateException(
                            "La base no coincide con los esquemas QA conocidos; revisar antes de migrar");
                }
                // La huella verifica estructura, no datos; usuarios y pruebas existentes permanecen.
                Flyway.configure().configuration(flyway.getConfiguration()).baselineVersion(version)
                        .baselineDescription("Base QA v" + version + " verificada").load().baseline();
            }
            // Sin adopción explícita, Flyway rechaza por defecto una base no vacía sin historial.
            flyway.migrate();
        };
    }

    private String recurso(String ruta) throws IOException {
        return new ClassPathResource(ruta).getContentAsString(StandardCharsets.UTF_8);
    }
}
