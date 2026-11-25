"""Gestión, visibilidad y correos del backend Docker con cuentas sintéticas y Mailpit."""
from datetime import datetime, timedelta, timezone
import json
import time
import urllib.request


def verificar_cuidado(sql, http, token, cuentas, mailpit_url):
    propietario, invitado = cuentas[:2]
    prefijo = "/cuidado-compartido"
    recordatorios_creados = []

    def recurso(actor, metodo, ruta, esperado=200, cuerpo=None):
        codigo, datos = http(ruta, token(actor, actor + "@example.test"), metodo, cuerpo)
        assert codigo == esperado, (metodo, ruta, codigo)
        return datos

    def preparar_recordatorios():
        mascota_id = sql(f"SELECT id_mascota FROM mascota WHERE propietario_id='{propietario}' AND fecha_eliminacion IS NULL ORDER BY id_mascota LIMIT 1")
        argentina = timezone(timedelta(hours=-3))
        hoy = datetime.now(argentina).replace(hour=17, minute=0, second=0, microsecond=0)
        registros = []
        for dias in [-1, 0, 1]:
            r = recurso(propietario, "POST", "/mascotas/" + mascota_id + "/recordatorios", 201,
                {"titulo": "Confirmacion diaria CI", "tipo": "OTRO", "modalidad": "unica",
                 "fechaHoraInicio": (hoy + timedelta(days=dias)).isoformat()})
            recordatorios_creados.append(r["idRecordatorio"])
            registros.append(r["idRecordatorio"])
        return registros

    def comprobar_fechas(registros):
        for id_, situacion in [(registros[0], "VENCIDO"), (registros[2], "PROXIMO")]:
            for actor in [propietario, invitado]:
                r = recurso(actor, "GET", "/recordatorios/" + id_)
                assert r["situacionTemporal"] == situacion and not r["puedeConfirmar"]
                error = recurso(actor, "PATCH", "/recordatorios/" + id_ + "/confirmacion", 400)
                assert error["codigo"] == "ERROR_NEGOCIO"

    def llamar(actor, metodo, ruta, esperado=200, cuerpo=None):
        codigo, datos = http(prefijo + ruta, token(actor, actor + "@example.test"), metodo, cuerpo)
        assert codigo == esperado, (metodo, ruta, codigo, datos.get("codigo"))
        return datos

    def crear(destino):
        return llamar(propietario, "POST", "/invitaciones", 201,
            {"emailInvitado": destino + "@example.test", "perfil": "Familia"})

    def accion(id_invitacion, accion):
        return "/invitaciones/" + id_invitacion + "/" + accion

    def mensajes():
        with urllib.request.urlopen(mailpit_url + "/api/v1/messages", timeout=3) as respuesta:
            lista = json.load(respuesta)["messages"]
        resultado = []
        for mensaje in lista:
            with urllib.request.urlopen(mailpit_url + "/api/v1/message/" + mensaje["ID"], timeout=3) as respuesta:
                resultado.append(json.load(respuesta))
        return resultado

    def esperar_correo(destino, id_invitacion, texto):
        limite = time.monotonic() + 15
        while time.monotonic() < limite:
            for mensaje in mensajes():
                if (destino + "@example.test" in [r["Address"] for r in mensaje["To"]]
                        and "invitacionId=" + id_invitacion in mensaje["Text"] and texto in mensaje["Text"]):
                    return mensaje
            time.sleep(0.2)
        raise AssertionError("No llegó el correo esperado a Mailpit para la invitación sintética")

    def mascotas_propietario_visibles(esperadas):
        codigo, datos = http("/mascotas", token(invitado, invitado + "@example.test"))
        assert codigo == 200
        visibles = {m["idMascota"] for m in datos}
        propias = set(sql(f"SELECT id_mascota FROM mascota WHERE propietario_id='{propietario}' AND fecha_eliminacion IS NULL").splitlines())
        assert propias.issubset(visibles) if esperadas else propias.isdisjoint(visibles)

    try:
        i = crear(invitado)
        aviso = esperar_correo(invitado, i["idInvitacion"], "aceptar o rechazar desde la web")
        assert "http://localhost:5173/cuidado-compartido?invitacionId=" + i["idInvitacion"] in aviso["Text"]
        assert "Abrir este enlace no concede acceso" in aviso["Text"]
        llamar(invitado, "GET", "/invitaciones/" + i["idInvitacion"])
        mascotas_propietario_visibles(False)
        assert datetime.fromisoformat(i["expiraEn"]) - datetime.fromisoformat(i["creadoEn"]) == timedelta(hours=48)
        llamar(propietario, "POST", accion(i["idInvitacion"], "aceptar"), 403)
        aceptada = llamar(invitado, "POST", accion(i["idInvitacion"], "aceptar"))
        acceso = aceptada["acceso"]["idAcceso"]
        esperar_correo(propietario, i["idInvitacion"], "acepto tu invitacion")
        mascotas_propietario_visibles(True)
        registros = preparar_recordatorios()
        comprobar_fechas(registros)  # Propietario y Familia: misma regla de confirmación.
        repetida = llamar(invitado, "POST", accion(i["idInvitacion"], "aceptar"))
        assert repetida["acceso"]["idAcceso"] == acceso
        llamar(invitado, "PATCH", "/accesos/" + acceso + "/perfil", 403, {"perfil": "Cuidador"})
        llamar(propietario, "PATCH", "/accesos/" + acceso + "/perfil", cuerpo={"perfil": "Cuidador"})
        assert llamar(invitado, "GET", "/accesos/" + acceso)["perfil"] == "Cuidador"
        comprobar_fechas(registros)  # Cuidador consulta cualquier fecha, sin confirmar otra fecha.
        confirmado = recurso(invitado, "PATCH", "/recordatorios/" + registros[1] + "/confirmacion")
        assert confirmado["confirmadoPorId"] == invitado and confirmado["situacionTemporal"] == "COMPLETADO"
        assert not confirmado["puedeConfirmar"]
        assert recurso(propietario, "GET", "/recordatorios/" + registros[1])["confirmadoPorId"] == invitado
        recurso(propietario, "PATCH", "/recordatorios/" + registros[1] + "/confirmacion", 400)
        assert llamar(invitado, "GET", "/me")["cupo"]["ocupados"] == 0
        assert not llamar(propietario, "DELETE", "/accesos/" + acceso)["activo"]
        mascotas_propietario_visibles(False)
        assert not llamar(invitado, "POST", accion(i["idInvitacion"], "aceptar"))["acceso"]["activo"]

        i = crear(invitado)
        llamar(invitado, "POST", accion(i["idInvitacion"], "rechazar"))
        esperar_correo(propietario, i["idInvitacion"], "rechazo tu invitacion")
        nueva = llamar(propietario, "POST", accion(i["idInvitacion"], "reenviar"), 201)
        esperar_correo(invitado, nueva["idInvitacion"], "aceptar o rechazar desde la web")
        assert nueva["idInvitacion"] != i["idInvitacion"]
        reemplazo = llamar(propietario, "POST", accion(nueva["idInvitacion"], "reenviar"), 201)
        llamar(invitado, "POST", accion(nueva["idInvitacion"], "aceptar"), 409)
        llamar(propietario, "POST", accion(reemplazo["idInvitacion"], "cancelar"))
        i = crear(invitado)
        acceso = llamar(invitado, "POST", accion(i["idInvitacion"], "aceptar"))["acceso"]["idAcceso"]
        assert not llamar(invitado, "POST", "/accesos/" + acceso + "/salir")["activo"]
        assert llamar(propietario, "GET", "/me")["cupo"]["ocupados"] == 0

        for destino in cuentas[1:4]:
            crear(destino)
        rechazada = llamar(propietario, "POST", "/invitaciones", 409,
            {"emailInvitado": cuentas[4] + "@example.test", "perfil": "Cuidador"})
        assert rechazada["codigo"] == "LIMITE_INVITADOS_ALCANZADO"
        assert llamar(propietario, "GET", "/me")["cupo"]["ocupados"] == 3
        return {"gestion_http": True, "aceptacion_idempotente": True, "revocacion_sin_reactivacion": True,
                "cupo_free": 3, "visibilidad_compartida_http": True, "correos_mailpit": True,
                "enlace_web_sin_aceptacion_automatica": True, "confirmacion_diaria_todos_los_perfiles": True,
                "situacion_temporal": True}
    finally:
        # Solo las cuentas aleatorias creadas por el verificador, nunca las cuentas del seed.
        for id_ in recordatorios_creados:
            sql(f"DELETE FROM recordatorio WHERE id_recordatorio='{id_}' AND creado_por_id='{propietario}'")
        for cuenta in cuentas:
            sql(f"DELETE FROM invitacion WHERE propietario_id='{cuenta}'")
        for cuenta in cuentas:
            sql(f"DELETE FROM acceso_compartido WHERE propietario_id='{cuenta}' OR invitado_id='{cuenta}'")
