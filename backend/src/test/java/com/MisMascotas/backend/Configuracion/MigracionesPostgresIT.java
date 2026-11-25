package com.MisMascotas.backend.Configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Cada caso crea y elimina únicamente su propia base en PostgreSQL temporal de CI. */
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_MIGRACIONES_IT", matches = "true")
class MigracionesPostgresIT {
    private JdbcTemplate administrador;
    private JdbcTemplate sql;
    private String base;
    private String url;

    @BeforeEach
    void crearBaseAislada() {
        String principal = System.getenv("QA_COBRO_DB_URL");
        assertThat(principal).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/qa_notificaciones_mercado_pago");
        assertThat(System.getenv("QA_COBRO_DB_USERNAME")).isEqualTo("qa_ci");
        administrador = new JdbcTemplate(fuente(principal));
        base = "qa_flyway_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(base).matches("qa_flyway_[0-9a-f]{32}");
        administrador.execute("CREATE DATABASE " + base);
        url = principal.substring(0, principal.lastIndexOf('/') + 1) + base;
        sql = new JdbcTemplate(fuente(url));
    }

    @AfterEach
    void retirarBaseAislada() {
        if (administrador != null && base != null) {
            assertThat(base).matches("qa_flyway_[0-9a-f]{32}");
            administrador.execute("DROP DATABASE " + base + " WITH (FORCE)");
        }
    }

    @Test
    void baseVaciaSeInicializaYReiniciarNoDuplicaCatalogoNiBorraDatos() throws Exception {
        migrar(false);
        assertThat(versionesExitosas()).isEqualTo(3);
        assertThat(sql.queryForObject("SELECT script FROM flyway_schema_history WHERE version='9'", String.class))
                .isEqualTo("B9__esquema_actual.sql");
        assertThat(sql.queryForObject("SELECT count(*) FROM estados", Integer.class)).isEqualTo(16);
        UUID usuario = usuario();
        String huella = huella();
        migrar(false);
        assertThat(versionesExitosas()).isEqualTo(3);
        assertThat(sql.queryForObject("SELECT count(*) FROM estados", Integer.class)).isEqualTo(16);
        assertThat(sql.queryForObject("SELECT nombre FROM usuario WHERE id_usuario = ?", String.class, usuario))
                .isEqualTo("Usuario conservado");
        assertThat(huella()).isEqualTo(huella);
        assertThat(sql.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid='public.notificacion_mercado_pago'::regclass", Boolean.class)).isTrue();
    }

    @Test
    void entregaAnteriorConservaCuentasMascotasEventosRecordatoriosYPagos() throws Exception {
        esquemaAnterior();
        sql.execute(recurso("db/qa/catalogo_qa_anterior.sql"));
        String datos = recurso("db/qa/datos_qa_anteriores.sql").replaceAll("(?m)^\\\\.*$", "");
        sql.execute("SET mismascotas.entorno = 'qa-web';\n" + datos);
        Map<String, String> antes = datosQa();
        UUID usuario = sql.queryForObject("SELECT id_usuario FROM usuario ORDER BY id_usuario LIMIT 1", UUID.class);
        UUID suscripcion = suscripcion(usuario, "remoto-preservado");
        UUID pago = UUID.randomUUID();
        sql.update("""
                INSERT INTO pago (id_pago,suscripcion_id,estado_id,id_transaccion_mercadopago,monto,fecha_pago,estado)
                VALUES (?,?,(SELECT id_estado FROM estados WHERE entidad='PAGO' AND nombre='APROBADO'),
                    'pago-preservado',4000,'2026-10-25T12:00:00Z','APROBADO')
                """, pago, suscripcion);
        migrar(true);
        assertThat(datosQa()).isEqualTo(antes);
        assertThat(sql.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(6);
        assertThat(sql.queryForObject("SELECT count(*) FROM mascota", Integer.class)).isEqualTo(15);
        assertThat(sql.queryForObject("SELECT count(*) FROM evento_clinico", Integer.class)).isEqualTo(20);
        assertThat(sql.queryForObject("SELECT count(*) FROM recordatorio", Integer.class)).isEqualTo(15);
        assertThat(sql.queryForObject("SELECT id_transaccion_mercadopago FROM pago WHERE id_pago=?", String.class, pago))
                .isEqualTo("pago-preservado");
        assertThat(sql.queryForObject("SELECT fecha_vencimiento FROM suscripcion WHERE id_suscripcion=?", java.sql.Timestamp.class, suscripcion).toInstant())
                .isEqualTo(Instant.parse("2026-12-25T12:00:00Z"));
        assertThat(sql.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('pago','suscripcion') AND column_name='estado'", Integer.class)).isZero();
        String actualizada = huella();
        migrar(true);
        assertThat(datosQa()).isEqualTo(antes);
        assertThat(huella()).isEqualTo(actualizada);
        // Una instalación vacía y una actualizada deben terminar con idéntica estructura.
        sql.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        migrar(false);
        assertThat(huella()).isEqualTo(actualizada);
    }

    @Test
    void baseConEstructuraDesconocidaSeRechazaSinRegistrarBaseline() throws Exception {
        esquemaAnterior();
        sql.execute("ALTER TABLE usuario ADD COLUMN cambio_no_reconocido text");
        UUID id = usuario();
        assertThatThrownBy(() -> migrar(true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");
        assertThat(existeHistorial()).isFalse();
        assertThat(sql.queryForObject("SELECT count(*) FROM usuario WHERE id_usuario=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void unaBaseExistenteExigeHabilitarLaAdopcionExplicitamente() throws Exception {
        esquemaAnterior();
        assertThatThrownBy(() -> migrar(false)).isInstanceOf(FlywayException.class);
        assertThat(existeHistorial()).isFalse();
    }

    @Test
    void unEsquemaActualAplicadoManualmenteNoSeAsumeComoLaBaseAntigua() throws Exception {
        sql.execute(Files.readString(Path.of("../DB/01-init.sql")));
        assertThatThrownBy(() -> migrar(true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");
        assertThat(existeHistorial()).isFalse();
    }

    @Test
    void idsRemotosDuplicadosDetienenLaMigracionYPermitenRetomarlaTrasCorregirlos() throws Exception {
        esquemaAnterior();
        sql.execute(recurso("db/qa/catalogo_qa_anterior.sql"));
        UUID id = usuario();
        suscripcion(id, "duplicado");
        UUID segunda = suscripcion(id, "duplicado");
        assertThatThrownBy(() -> migrar(true)).isInstanceOf(FlywayException.class)
                .hasMessageContaining("duplicados");
        assertThat(sql.queryForObject("SELECT count(*) FROM suscripcion", Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname='uq_suscripcion_id_mercadopago'", Integer.class)).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='5'", Integer.class)).isZero();
        sql.update("UPDATE suscripcion SET id_suscripcion_mercadopago='corregido' WHERE id_suscripcion=?", segunda);
        migrar(true);
        assertThat(sql.queryForObject("SELECT count(*) FROM suscripcion", Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='9' AND success", Integer.class)).isEqualTo(1);
    }

    @Test
    void checksumInconsistenteSeRechazaSinRepararElHistorial() throws Exception {
        // Validar una migración versionada aplicada, no el snapshot inicial B9.
        esquemaAnterior();
        sql.execute(recurso("db/qa/catalogo_qa_anterior.sql"));
        migrar(true);
        sql.update("UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='9'");
        int modificado = sql.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='9'", Integer.class);
        assertThatThrownBy(() -> migrar(false)).isInstanceOf(FlywayException.class);
        assertThat(sql.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='9'", Integer.class)).isEqualTo(modificado);
    }

    @Test
    void baseActualInicializadaPorLaImagenRegistraElPuntoDePartidaYAplicaV10() throws Exception {
        sql.execute(recurso("db/migration/B9__esquema_actual.sql"));
        UUID id = usuario();
        String antes = huella();
        migrar(true);
        assertThat(sql.queryForObject("SELECT type FROM flyway_schema_history WHERE version='9'", String.class))
                .isEqualTo("BASELINE");
        assertThat(sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version::int < 9", Integer.class)).isZero();
        assertThat(huella()).isNotEqualTo(antes);
        assertThat(sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='10' AND success", Integer.class)).isEqualTo(1);
        String actualizada = huella();
        assertThat(sql.queryForObject("SELECT nombre FROM usuario WHERE id_usuario=?", String.class, id)).isEqualTo("Usuario conservado");
        migrar(true);
        assertThat(huella()).isEqualTo(actualizada);
        assertThat(sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='9'", Integer.class)).isEqualTo(1);
    }

    @Test
    void baseActualConCambiosDesconocidosTampocoSeAdopta() throws Exception {
        sql.execute(recurso("db/migration/B9__esquema_actual.sql"));
        sql.execute("ALTER TABLE usuario ADD COLUMN cambio_no_reconocido text");
        assertThatThrownBy(() -> migrar(true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no coincide");
        assertThat(existeHistorial()).isFalse();
    }

    @Test
    void unaActualizacionPosteriorSeAplicaSobreLaBaseSeedSinReiniciarla() throws Exception {
        sql.execute(recurso("db/migration/B9__esquema_actual.sql"));
        UUID id = usuario();
        Flyway futuro = Flyway.configure().dataSource(fuente(url)).defaultSchema("public")
                .cleanDisabled(true).locations("classpath:db/migration", "classpath:db/futuro").load();
        new MigracionesQaConfig().migracionVerificada(true).migrate(futuro);
        assertThat(sql.queryForObject("SELECT max(version::int) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(12);
        assertThat(sql.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario' AND column_name='detalle_qa_prueba'", Integer.class)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT nombre FROM usuario WHERE id_usuario=?", String.class, id)).isEqualTo("Usuario conservado");
    }

    @Test
    void v10ConservaVinculosNormalizaEmailsYCompletaSoloLosPlazosFaltantes() throws Exception {
        sql.execute(recurso("db/migration/B9__esquema_actual.sql"));
        var dueno = usuario(); var invitado = usuario();
        var invitacion = UUID.randomUUID(); var acceso = UUID.randomUUID();
        sql.update("""
                INSERT INTO invitacion(id_invitacion,propietario_id,email_invitado,perfil,estado_id,creado_en)
                VALUES (?,?,'  INVITADO@EXAMPLE.TEST  ','Familia',
                    (SELECT id_estado FROM estados WHERE entidad='INVITACION' AND nombre='PENDIENTE'),'2030-01-01T12:00:00Z')
                """, invitacion, dueno);
        sql.update("""
                INSERT INTO acceso_compartido(id_acceso,propietario_id,invitado_id,perfil,fecha_ingreso)
                VALUES (?,?,?,'Familia','2030-01-01T12:00:00Z')
                """, acceso, dueno, invitado);
        migrar(true);
        assertThat(sql.queryForObject("SELECT email_invitado FROM invitacion WHERE id_invitacion=?", String.class, invitacion))
                .isEqualTo("invitado@example.test");
        assertThat(sql.queryForObject("SELECT expira_en FROM invitacion WHERE id_invitacion=?", java.sql.Timestamp.class, invitacion).toInstant())
                .isEqualTo(Instant.parse("2030-01-03T12:00:00Z"));
        assertThat(sql.queryForObject("SELECT perfil FROM acceso_compartido WHERE id_acceso=?", String.class, acceso)).isEqualTo("Familia");
        assertThat(sql.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(2);
        var despues = huella(); migrar(false);
        assertThat(huella()).isEqualTo(despues);
        assertThat(sql.queryForObject("SELECT count(*) FROM estados WHERE entidad='INVITACION' AND nombre='RECHAZADA'", Integer.class)).isEqualTo(1);
    }

    @Test
    void accesosDuplicadosDetienenV10SinBorrarDatosYPermitenRetomarla() throws Exception {
        sql.execute(recurso("db/migration/B9__esquema_actual.sql"));
        // Simula datos inconsistentes en una V9 ya registrada cuyo índice fue retirado manualmente.
        Flyway.configure().dataSource(fuente(url)).defaultSchema("public").baselineVersion("9").load().baseline();
        sql.execute("ALTER TABLE acceso_compartido DROP CONSTRAINT acceso_compartido_propietario_invitado_key");
        var dueno = usuario(); var invitado = usuario();
        var primero = UUID.randomUUID(); var segundo = UUID.randomUUID();
        for (var id : java.util.List.of(primero, segundo))
            sql.update("INSERT INTO acceso_compartido(id_acceso,propietario_id,invitado_id) VALUES (?,?,?)", id, dueno, invitado);
        assertThatThrownBy(() -> migrar(false)).isInstanceOf(FlywayException.class).hasMessageContaining("duplicados");
        assertThat(sql.queryForObject("SELECT count(*) FROM acceso_compartido", Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM estados WHERE entidad='INVITACION' AND nombre='RECHAZADA'", Integer.class)).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='10'", Integer.class)).isZero();
        sql.update("UPDATE acceso_compartido SET activo=false WHERE id_acceso=?", segundo);
        migrar(false);
        assertThat(sql.queryForObject("SELECT count(*) FROM acceso_compartido", Integer.class)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM acceso_compartido WHERE activo", Integer.class)).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"email", "plazo"})
    void invitacionesInvalidasDetienenLaMigracionSinCambiarElRegistro(String caso) throws Exception {
        sql.execute(recurso("db/migration/B9__esquema_actual.sql"));
        var id = UUID.randomUUID(); var dueno = usuario();
        String email = "email".equals(caso) ? "  " : "invitado@example.test";
        sql.update("""
                INSERT INTO invitacion(id_invitacion,propietario_id,email_invitado,estado_id,creado_en,expira_en)
                VALUES (?,?,?,(SELECT id_estado FROM estados WHERE entidad='INVITACION' AND nombre='PENDIENTE'),
                    '2030-01-01T12:00:00Z',?)
                """, id, dueno, email, "plazo".equals(caso) ? java.sql.Timestamp.from(Instant.parse("2030-01-01T12:00:00Z")) : null);
        assertThatThrownBy(() -> migrar(true)).isInstanceOf(FlywayException.class).hasMessageContaining("plazo invalido");
        assertThat(sql.queryForObject("SELECT email_invitado FROM invitacion WHERE id_invitacion=?", String.class, id)).isEqualTo(email);
        assertThat(sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='10'", Integer.class)).isZero();
    }

    @Test
    void seedQaAdmiteV10SinRetirarElNuevoEstado() throws Exception {
        migrar(false);
        String seed = Files.readString(Path.of("../DB/qa/restablecer-seed.sql")).replaceAll("(?m)^\\\\.*$", "");
        sql.execute("SET mismascotas.entorno = 'qa-web';\n" + seed);
        assertThat(sql.queryForObject("SELECT count(*) FROM usuario", Integer.class)).isEqualTo(6);
        assertThat(sql.queryForObject("SELECT count(*) FROM mascota", Integer.class)).isEqualTo(15);
        assertThat(sql.queryForObject("SELECT count(*) FROM estados", Integer.class)).isEqualTo(16);
        assertThat(sql.queryForObject("SELECT count(*) FROM estados WHERE entidad='INVITACION' AND nombre='RECHAZADA'", Integer.class)).isEqualTo(1);
    }

    private void migrar(boolean adoptar) throws IOException {
        Flyway flyway = Flyway.configure().dataSource(fuente(url)).defaultSchema("public")
                .cleanDisabled(true).locations("classpath:db/migration").load();
        new MigracionesQaConfig().migracionVerificada(adoptar).migrate(flyway);
    }

    private DriverManagerDataSource fuente(String conexion) {
        return new DriverManagerDataSource(conexion, System.getenv("QA_COBRO_DB_USERNAME"),
                System.getenv("QA_COBRO_DB_PASSWORD"));
    }

    private void esquemaAnterior() throws IOException {
        sql.execute(recurso("db/migration/V1__esquema_base_qa.sql"));
    }

    private UUID usuario() {
        UUID id = UUID.randomUUID();
        sql.update("INSERT INTO usuario(id_usuario,nombre,email,password_hash) VALUES (?,'Usuario conservado',?,'hash-ficticio')", id, id + "@example.test");
        return id;
    }

    private UUID suscripcion(UUID usuario, String remoto) {
        UUID id = UUID.randomUUID();
        sql.update("""
                INSERT INTO suscripcion(id_suscripcion,usuario_id,tipo_plan,estado_id,estado,id_suscripcion_mercadopago,fecha_vencimiento)
                VALUES (?,?,'PREMIUM_MENSUAL',(SELECT id_estado FROM estados WHERE entidad='SUSCRIPCION' AND nombre='ACTIVA'),
                    'ACTIVA',?,'2026-12-25T12:00:00Z')
                """, id, usuario, remoto);
        return id;
    }

    private Map<String, String> datosQa() {
        Map<String, String> datos = new LinkedHashMap<>();
        for (var tabla : Map.of("usuario", "id_usuario", "mascota", "id_mascota",
                "evento_clinico", "id_evento", "recordatorio", "id_recordatorio").entrySet()) {
            datos.put(tabla.getKey(), sql.queryForObject("SELECT jsonb_agg(to_jsonb(t) ORDER BY "
                    + tabla.getValue() + ")::text FROM " + tabla.getKey() + " t", String.class));
        }
        return datos;
    }

    private int versionesExitosas() {
        return sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL AND type <> 'BASELINE'", Integer.class);
    }

    private boolean existeHistorial() {
        return Boolean.TRUE.equals(sql.queryForObject("SELECT to_regclass('public.flyway_schema_history') IS NOT NULL", Boolean.class));
    }

    private String huella() throws IOException {
        return sql.queryForObject(recurso("db/qa/huella_esquema.sql"), String.class);
    }

    private String recurso(String nombre) throws IOException {
        return new ClassPathResource(nombre).getContentAsString(StandardCharsets.UTF_8);
    }
}
