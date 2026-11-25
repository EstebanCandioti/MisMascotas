"""Verifica el seed, el arranque y la persistencia de las imágenes QA en volúmenes aislados."""
from pathlib import Path
import argparse
import json
import subprocess
import time
import uuid
from verificar_premium_qa import verificar_premium


def ejecutar(*args, entrada=None):
    resultado = subprocess.run(args, input=entrada.encode() if entrada is not None else None, capture_output=True)
    if resultado.returncode:
        raise RuntimeError(resultado.stderr.decode(errors="replace")[-2000:])
    return resultado.stdout.decode().strip()


def verificar(imagen_db, imagen_backend):
    raiz = Path(__file__).resolve().parent.parent
    token = uuid.uuid4().hex
    prefijo = "mismascotas-ci-base-" + token
    db = prefijo + "-db"
    backend = prefijo + "-backend"
    volumen = prefijo + "-datos"
    etiqueta = "ar.mismascotas.verificacion"
    red_creada = volumen_creado = False
    contenedores = set()

    def retirar_contenedor(nombre):
        if nombre not in contenedores:
            return
        etiquetas = json.loads(ejecutar("docker", "inspect", "--format", "{{json .Config.Labels}}", nombre))
        if etiquetas.get(etiqueta) != token:
            raise RuntimeError("No se elimina un contenedor ajeno a esta verificación")
        ejecutar("docker", "rm", "--force", nombre)
        contenedores.remove(nombre)

    def sql(consulta):
        return ejecutar("docker", "exec", "-i", db, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
                        "-U", "qa_ci", "-d", "qa_imagen", entrada=consulta)

    def iniciar_db():
        ejecutar("docker", "run", "-d", "--name", db, "--network", prefijo, "--label", etiqueta + "=" + token,
                 "--mount", "type=volume,src=" + volumen + ",dst=/var/lib/postgresql/data",
                 "--env", "POSTGRES_DB=qa_imagen", "--env", "POSTGRES_USER=qa_ci",
                 "--env", "POSTGRES_PASSWORD=clave-ficticia-exclusiva-ci", imagen_db)
        contenedores.add(db)
        limite = time.monotonic() + 60
        while time.monotonic() < limite:
            prueba = subprocess.run(["docker", "exec", db, "sh", "-c",
                'test "$(cat "$PGDATA/.mismascotas-qa-seed" 2>/dev/null)" = qa-base-v2 && pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], capture_output=True)
            if prueba.returncode == 0:
                return
            time.sleep(1)
        raise RuntimeError("No se completó el seed QA: " + ejecutar("docker", "logs", "--tail", "20", db))

    try:
        ejecutar("docker", "network", "create", "--label", etiqueta + "=" + token, prefijo)
        red_creada = True
        ejecutar("docker", "volume", "create", "--label", etiqueta + "=" + token, volumen)
        volumen_creado = True
        iniciar_db()
        cantidades = json.loads(sql("""SELECT json_build_object(
            'usuarios',(SELECT count(*) FROM usuario),'mascotas',(SELECT count(*) FROM mascota),
            'eventos_clinicos',(SELECT count(*) FROM evento_clinico),'recordatorios',(SELECT count(*) FROM recordatorio))"""))
        assert cantidades == dict(usuarios=6, mascotas=15, eventos_clinicos=20, recordatorios=15), cantidades
        assert sql("SELECT count(*) FROM estados") == "15"
        assert sql("SELECT to_regclass('public.flyway_schema_history') IS NULL") == "t"
        assert sql("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('pago','suscripcion') AND column_name='estado'") == "0"
        consulta = (raiz / "backend/src/main/resources/db/qa/huella_esquema.sql").read_text(encoding="utf-8")
        assert sql(consulta) == (raiz / "backend/src/main/resources/db/qa/huella_base_v9.txt").read_text().strip()

        secreto_ci = "secreto-ficticio-ci-con-mas-de-treinta-y-dos-caracteres"
        args = ["docker", "run", "-d", "--name", backend, "--network", prefijo, "--label", etiqueta + "=" + token,
                "--publish", "127.0.0.1::8080"]
        for clave, valor in {
            "DB_URL": "jdbc:postgresql://" + db + ":5432/qa_imagen", "DB_USERNAME": "qa_ci",
            "DB_PASSWORD": "clave-ficticia-exclusiva-ci",
            "JWT_SECRET": secreto_ci,
            "JAVA_TOOL_OPTIONS": "-Duser.timezone=UTC", "MAIL_HOST": "127.0.0.1", "MAIL_PORT": "9",
            "MAIL_SMTP_AUTH": "false", "MAIL_SMTP_STARTTLS_ENABLE": "false", "MAIL_SMTP_STARTTLS_REQUIRED": "false",
            "FLYWAY_ENABLED": "true", "MIGRACIONES_ADOPTAR_BASE_QA": "true",
        }.items():
            args += ["--env", clave + "=" + valor]
        args += [imagen_backend, "--mercadopago.notificaciones.scheduler-habilitado=false",
                 "--auditoria.pendientes.procesamiento-habilitado=false"]
        ejecutar(*args)
        contenedores.add(backend)
        limite = time.monotonic() + 60
        while time.monotonic() < limite:
            r = subprocess.run(["docker", "exec", backend, "sh", "-c",
                "wget -S --spider http://127.0.0.1:8080/ 2>&1"], capture_output=True)
            if b"HTTP/1.1 401" in r.stdout:
                break
            time.sleep(1)
        else:
            raise RuntimeError("El backend no arrancó: " + ejecutar("docker", "logs", "--tail", "25", backend))
        assert sql("SELECT type FROM flyway_schema_history WHERE version='9' AND success") == "BASELINE"
        assert sql("SELECT count(*) FROM flyway_schema_history WHERE version::int < 9") == "0"
        version_final = sql("SELECT max(version::int) FROM flyway_schema_history WHERE success")
        consulta_catalogo = "SELECT md5(jsonb_agg(to_jsonb(e) ORDER BY id_estado)::text) FROM estados e"
        catalogo_actualizado = sql(consulta_catalogo)
        puerto = ejecutar("docker", "port", backend, "8080/tcp").rsplit(":", 1)[1]
        premium = verificar_premium(sql, "http://127.0.0.1:" + puerto, secreto_ci)

        # Una actualización/recreación del contenedor debe conservar el volumen y sus cambios.
        id_ = str(uuid.uuid4())
        sql(f"INSERT INTO usuario(id_usuario,nombre,email,password_hash) VALUES ('{id_}','Persistencia QA','{id_}@example.test','hash-ficticio')")
        retirar_contenedor(backend)
        retirar_contenedor(db)
        iniciar_db()
        assert sql(f"SELECT count(*) FROM usuario WHERE id_usuario='{id_}'") == "1"
        assert sql("SELECT count(*) FROM usuario") == "7"
        assert sql(consulta_catalogo) == catalogo_actualizado
        assert sql("SELECT count(*) FROM flyway_schema_history WHERE version='9'") == "1"
        assert sql("SELECT max(version::int) FROM flyway_schema_history WHERE success") == version_final
        print(json.dumps(dict(seed=cantidades, version_base=9, version_final=int(version_final), baseline_sin_reaplicar_sql=True,
                              backend_http=401, datos_persisten_al_recrear=True, premium=premium)))
    finally:
        for nombre in (backend, db):
            retirar_contenedor(nombre)
        for tipo, nombre, creado in (("volume", volumen, volumen_creado), ("network", prefijo, red_creada)):
            if creado:
                etiquetas = json.loads(ejecutar("docker", tipo, "inspect", "--format", "{{json .Labels}}", nombre))
                if etiquetas.get(etiqueta) != token:
                    raise RuntimeError("No se elimina un recurso ajeno a esta verificación")
                ejecutar("docker", tipo, "rm", nombre)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("imagen_db")
    parser.add_argument("imagen_backend")
    argumentos = parser.parse_args()
    verificar(argumentos.imagen_db, argumentos.imagen_backend)
