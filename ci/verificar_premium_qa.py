"""Casos Premium del backend empaquetado; solo datos y tokens sintéticos de CI."""
import base64
import hashlib
import hmac
import json
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def verificar_premium(sql, base_url, secreto_ci):
    if urllib.parse.urlparse(base_url).hostname != "127.0.0.1":
        raise ValueError("La verificación Premium solo admite el backend Docker local de CI")
    cuentas = []

    def token(id_usuario, email):
        def codificar(valor):
            return base64.urlsafe_b64encode(valor).rstrip(b"=")
        ahora = int(time.time())
        datos = {"sub": email, "idUsuario": id_usuario, "iat": ahora, "exp": ahora + 600}
        partes = [codificar(json.dumps(valor).encode()) for valor in ({"alg": "HS256", "typ": "JWT"}, datos)]
        cuerpo = b".".join(partes)
        firma = hmac.new(secreto_ci.encode(), cuerpo, hashlib.sha256).digest()
        return (cuerpo + b"." + codificar(firma)).decode()

    def http(ruta, credencial, metodo="GET", cuerpo=None):
        peticion = urllib.request.Request(base_url + ruta, method=metodo,
            data=json.dumps(cuerpo).encode() if cuerpo is not None else None,
            headers={"Authorization": "Bearer " + credencial, "Content-Type": "application/json"})
        try:
            respuesta = urllib.request.urlopen(peticion, timeout=15)
        except urllib.error.HTTPError as error:
            respuesta = error
        with respuesta:
            return respuesta.code, json.load(respuesta)

    # Indicador guardado, tipo de evidencia, plan, beneficio esperado, cobertura esperada.
    casos = [
        (False, "ninguna", "PREMIUM_MENSUAL", False, False),
        (True, "ninguna", "PREMIUM_MENSUAL", True, False),
        (False, "vigente", "PREMIUM_MENSUAL", True, True),
        (False, "vigente", "PREMIUM_ANUAL", True, True),
        (False, "cancelada", "PREMIUM_MENSUAL", True, True),
        (False, "pausada", "PREMIUM_MENSUAL", True, True),
        (True, "vencida", "PREMIUM_MENSUAL", False, False),
        (True, "sin_fecha", "PREMIUM_MENSUAL", False, False),
        (False, "sin_pago", "PREMIUM_MENSUAL", False, False),
    ]
    try:
        for indicador, caso, plan, esperado, cobertura in casos:
            id_usuario, id_suscripcion = str(uuid.uuid4()), str(uuid.uuid4())
            email = id_usuario + "@example.test"
            cuentas.append(id_usuario)
            sql(f"""INSERT INTO usuario(id_usuario,nombre,email,password_hash,es_premium)
                VALUES ('{id_usuario}','Premium CI','{email}','hash-ficticio-no-utilizable',{str(indicador).lower()});
                INSERT INTO mascota(id_mascota,propietario_id,nombre,especie,fecha_nacimiento)
                SELECT md5('{id_usuario}' || n::text)::uuid,'{id_usuario}','Mascota CI ' || n,'Perro','2020-01-01'
                FROM generate_series(1,5) n;""")
            if caso != "ninguna":
                estado = "CANCELADA" if caso == "cancelada" else "ACTIVA"
                remoto = {"cancelada": "cancelled", "pausada": "paused"}.get(caso, "authorized")
                fecha = "NULL" if caso == "sin_fecha" else (
                    "CURRENT_TIMESTAMP - INTERVAL '1 minute'" if caso == "vencida" else "CURRENT_TIMESTAMP + INTERVAL '1 hour'")
                estado_pago = "RECHAZADO" if caso == "sin_pago" else "APROBADO"
                monto = 40000 if plan == "PREMIUM_ANUAL" else 4000
                sql(f"""INSERT INTO suscripcion(id_suscripcion,usuario_id,tipo_plan,estado_id,estado_mercadopago,fecha_vencimiento)
                    VALUES ('{id_suscripcion}','{id_usuario}','{plan}',
                        (SELECT id_estado FROM estados WHERE entidad='SUSCRIPCION' AND nombre='{estado}'),'{remoto}',{fecha});
                    INSERT INTO pago(id_pago,suscripcion_id,monto,fecha_pago,estado_id)
                    VALUES ('{uuid.uuid4()}','{id_suscripcion}',{monto},CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        (SELECT id_estado FROM estados WHERE entidad='PAGO' AND nombre='{estado_pago}'));""")
                if caso == "cancelada":
                    sql(f"""INSERT INTO suscripcion(id_suscripcion,usuario_id,tipo_plan,estado_id)
                        VALUES ('{uuid.uuid4()}','{id_usuario}','PREMIUM_ANUAL',
                            (SELECT id_estado FROM estados WHERE entidad='SUSCRIPCION' AND nombre='PENDIENTE'));""")

            credencial = token(id_usuario, email)
            codigo, perfil = http("/usuario/me", credencial)
            assert codigo == 200 and perfil["idUsuario"] == id_usuario and perfil["esPremium"] == esperado, caso
            assert set(perfil) == {"idUsuario", "nombre", "email", "esPremium"}, caso
            codigo, consulta = http("/suscripciones/me", credencial)
            assert codigo == 200 and consulta["esPremium"] == esperado, caso
            assert consulta["coberturaPagadaVigente"] == cobertura, caso
            assert (consulta["fechaFinCobertura"] is not None) == cobertura, caso
            if caso != "ninguna":
                assert consulta["suscripcion"]["idSuscripcion"] == id_suscripcion, caso
                assert consulta["suscripcion"]["coberturaVigente"] == cobertura, caso
            if caso == "cancelada":
                assert consulta["suscripcion"]["estado"] == "CANCELADA", caso
                assert consulta["intentoPendiente"]["estado"] == "PENDIENTE", caso
            if caso == "sin_fecha":
                assert consulta["suscripcion"]["requiereConciliacion"], caso
            assert sql(f"SELECT es_premium FROM usuario WHERE id_usuario='{id_usuario}'") == ("t" if indicador else "f"), caso
            codigo, alta = http("/mascotas", credencial, "POST", {
                "nombre": "Sexta CI", "especie": "Perro", "fechaNacimiento": "2020-01-01", "fechaAproximada": False})
            assert codigo == (201 if esperado else 409), (caso, codigo)
            if esperado:
                assert alta["propietarioId"] == id_usuario, caso
            else:
                assert alta["codigo"] == "LIMITE_MASCOTAS_ALCANZADO", caso

        codigo, aislada = http("/suscripciones/me?usuarioId=" + cuentas[1], token(cuentas[0], cuentas[0] + "@example.test"))
        assert codigo == 200 and not aislada["esPremium"] and aislada["suscripcion"] is None
        return {"casos": len(casos), "perfil_suscripcion_y_cupo_coherentes": True,
                "aislamiento_entre_cuentas": True, "sin_cobros_reales": True}
    finally:
        for id_usuario in cuentas:
            sql(f"""DELETE FROM log_auditoria WHERE actor_id='{id_usuario}';
                DELETE FROM evento_auditoria_pendiente WHERE actor_id='{id_usuario}';
                DELETE FROM mascota WHERE propietario_id='{id_usuario}';
                DELETE FROM pago WHERE suscripcion_id IN (SELECT id_suscripcion FROM suscripcion WHERE usuario_id='{id_usuario}');
                DELETE FROM suscripcion WHERE usuario_id='{id_usuario}';
                DELETE FROM usuario WHERE id_usuario='{id_usuario}';""")
