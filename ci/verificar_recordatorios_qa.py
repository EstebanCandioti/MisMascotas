"""Recurrencias del backend empaquetado con PostgreSQL y permisos compartidos de CI."""
from datetime import datetime, timedelta, timezone
import json
import uuid


def verificar_recordatorios(sql, http, token, cuentas, reiniciar_backend):
    propietario, invitado = cuentas[:2]
    mascota = sql(f"SELECT id_mascota FROM mascota WHERE propietario_id='{propietario}' ORDER BY id_mascota LIMIT 1")
    acceso = str(uuid.uuid4())
    series = []
    hoy = datetime.now(timezone(timedelta(hours=-3))).replace(hour=18, minute=0, second=0, microsecond=0)

    def instante(dias, hora=18):
        return (hoy + timedelta(days=dias)).replace(hour=hora).isoformat()

    def pedir(actor, metodo, ruta, esperado=200, cuerpo=None):
        codigo, datos = http(ruta, token(actor, actor + "@example.test"), metodo, cuerpo)
        assert codigo == esperado, (metodo, ruta, codigo, datos)
        return datos

    def crear(inicio, fin=None):
        datos = {"titulo": "Recurrencias CI", "tipo": "OTRO", "modalidad": "recurrente",
                 "intervaloValor": 1, "intervaloUnidad": "dias", "fechaHoraInicio": instante(inicio), "fechaFin": fin}
        resultado = pedir(propietario, "POST", f"/mascotas/{mascota}/recordatorios", 201, datos)
        id_ = resultado["idRecordatorio"]
        uuid.UUID(id_)
        series.append(id_)
        return id_, datos

    def fechas(id_):
        return json.loads(sql(f"""SELECT coalesce(json_agg(fecha_programada ORDER BY fecha_programada),'[]')
            FROM ocurrencia_recordatorio WHERE recordatorio_id='{id_}'"""))

    def pendientes_hoy(id_, actor=invitado):
        return [r for r in pedir(actor, "GET", "/recordatorios/hoy") if r["idRecordatorio"] == id_]

    def cambiar_perfil(perfil):
        pedir(propietario, "PATCH", f"/cuidado-compartido/accesos/{acceso}/perfil", cuerpo={"perfil": perfil})

    try:
        sql(f"""INSERT INTO acceso_compartido(id_acceso,propietario_id,invitado_id,perfil)
            VALUES ('{acceso}','{propietario}','{invitado}','Familia');""")
        finita, datos_finita = crear(-2, instante(1))
        assert len(fechas(finita)) == 4
        assert len(pendientes_hoy(finita)) == 1
        ocurrencia = pendientes_hoy(finita)[0]
        assert ocurrencia["puedeConfirmar"]
        ruta = f"/recordatorios/{finita}/confirmacion?idOcurrencia={ocurrencia['idOcurrencia']}"
        assert pedir(invitado, "PATCH", f"/recordatorios/{finita}/confirmacion", 400)["codigo"] == "ERROR_VALIDACION"
        for fecha in (-2, 1):
            selector = sql(f"""SELECT id_ocurrencia FROM ocurrencia_recordatorio WHERE recordatorio_id='{finita}'
                AND fecha_programada='{instante(fecha)}'::timestamptz""")
            pedir(invitado, "PATCH", f"/recordatorios/{finita}/confirmacion?idOcurrencia={selector}", 400)
        pedir(invitado, "PATCH", ruta)
        pedir(propietario, "PATCH", ruta, 400)
        pedir(invitado, "DELETE", ruta)
        cambiar_perfil("Cuidador")
        pedir(invitado, "PATCH", ruta)
        pedir(invitado, "DELETE", ruta, 403)
        pedir(invitado, "PUT", f"/recordatorios/{finita}", 403, datos_finita)
        pedir(propietario, "DELETE", ruta)

        indefinida, datos = crear(-11)
        assert len(fechas(indefinida)) == 12
        reiniciar_backend()
        assert len(fechas(indefinida)) == 24
        assert len(fechas(finita)) == 4
        reiniciar_backend()
        assert len(fechas(indefinida)) == 24
        assert sql(f"SELECT ultima_fecha_generada FROM recordatorio WHERE id_recordatorio='{indefinida}'") == (hoy + timedelta(days=12)).date().isoformat()
        pedir(propietario, "PATCH", f"/recordatorios/{indefinida}/estado?nuevoEstado=INACTIVO")
        assert len(fechas(indefinida)) == 11 and not pendientes_hoy(indefinida)
        reiniciar_backend()
        assert len(fechas(indefinida)) == 11
        pedir(propietario, "PATCH", f"/recordatorios/{indefinida}/estado?nuevoEstado=ACTIVO")
        assert len(fechas(indefinida)) == 23
        actual = pendientes_hoy(indefinida)[0]
        confirmar = f"/recordatorios/{indefinida}/confirmacion?idOcurrencia={actual['idOcurrencia']}"
        pedir(invitado, "PATCH", confirmar)
        guardada = pendientes_hoy(indefinida)[0]
        edicion = dict(datos, fechaHoraInicio=instante(1, 17), intervaloValor=2, fechaFin=instante(5, 17))
        pedir(propietario, "PUT", f"/recordatorios/{indefinida}", cuerpo=edicion)
        assert len(fechas(indefinida)) == 15
        conservada = pendientes_hoy(indefinida)[0]
        for campo in ("idOcurrencia", "fechaProgramada", "confirmadoPorId", "confirmadoEn"):
            assert conservada[campo] == guardada[campo], campo
        cambiar_perfil("Familia")
        pedir(invitado, "DELETE", confirmar)
        pedir(invitado, "PATCH", confirmar)
        pedir(propietario, "DELETE", f"/cuidado-compartido/accesos/{acceso}")
        assert not pendientes_hoy(indefinida)
        pedir(invitado, "PATCH", confirmar, 403)
        pedir(propietario, "DELETE", f"/recordatorios/{indefinida}", 204)
        assert sql(f"SELECT count(*) FROM ocurrencia_recordatorio WHERE recordatorio_id='{indefinida}'") == "15"
        return {"alta_finita_e_indefinida": True, "renovacion_al_arrancar": True,
                "reinicio_sin_duplicados": True, "confirmacion_por_ocurrencia": True,
                "permisos_familia_y_cuidador": True, "reactivacion_e_historial": True,
                "edicion_de_futuras": True, "revocacion_misma_sesion": True, "borrado_logico": True}
    finally:
        for id_ in series:
            sql(f"""DELETE FROM ocurrencia_recordatorio WHERE recordatorio_id='{id_}';
                DELETE FROM recordatorio WHERE id_recordatorio='{id_}' AND creado_por_id='{propietario}';""")
        sql(f"DELETE FROM acceso_compartido WHERE id_acceso='{acceso}'")
