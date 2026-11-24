-- REINICIO LOCAL DE SUSCRIPCIONES: SOLO PARA UNA CUENTA DE QA.
-- No es una migracion y no debe ejecutarse como parte del arranque.
--
-- Antes de ejecutar:
-- 1. Cancelar en MP TODOS los preapprovals de esta cuenta de prueba y verificar
--    su cancelacion. El SQL no llama a MP, no detiene cobros ni devuelve dinero.
--    Un intento sin ID remoto puede necesitar conciliacion antes del reinicio.
-- 2. Detener el backend para que no procese webhooks o solicitudes durante la limpieza.
-- 3. Reemplazar UNICAMENTE el UUID indicado abajo y ejecutar el bloque completo
--    en el SQL Editor de la base de desarrollo/QA correspondiente.
--
-- Efectos, atomicos dentro de este DO:
-- - Detiene los avisos PENDIENTE que puede vincular mediante preapproval,
--   factura o pago locales. Conserva esas filas como REVISION / REINICIO_QA.
-- - Elimina todos los pagos y suscripciones locales del usuario elegido.
-- - Restablece es_premium = false, incluso si Premium se habia asignado manualmente.
-- - Conserva usuario, credenciales, sesiones, mascotas y auditorias existentes.
--
-- Limite: la bandeja no guarda usuario_id ni preapproval_id de cada cuota.
-- Una notificacion de cuota sin factura/pago vinculado localmente puede quedar
-- sin identificar. No se borran avisos ajenos ni se adivina su propietario.
-- Al reiniciar, esos avisos antiguos pueden pasar a REVISION porque la suscripcion
-- ya no existe. No deben vincularse a una suscripcion nueva con UUID diferente.
--
-- Si alguna instruccion falla, PostgreSQL revierte todas las modificaciones del DO.
-- El resumen se emite como NOTICE; el editor puede mostrar "Success. No rows returned".

DO $reinicio_qa$
DECLARE
    -- CAMBIAR ESTE UUID POR EL DEL USUARIO DE PRUEBA:
    v_usuario_id constant uuid := '00000000-0000-0000-0000-000000000000';

    v_email text;
    v_suscripciones uuid[];
    v_preapprovals text[];
    v_facturas text[];
    v_transacciones text[];
    v_avisos_detenidos integer := 0;
    v_pagos_eliminados integer := 0;
    v_suscripciones_eliminadas integer := 0;
BEGIN
    IF v_usuario_id = '00000000-0000-0000-0000-000000000000'::uuid THEN
        RAISE EXCEPTION 'Completar v_usuario_id con el UUID de la cuenta QA. No se modifico ningun dato.';
    END IF;

    -- Evita esperar indefinidamente por otro proceso que conserve un bloqueo.
    PERFORM set_config('lock_timeout', '5s', true);

    SELECT u.email INTO v_email
    FROM public.usuario u
    WHERE u.id_usuario = v_usuario_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'El usuario % no existe. No se modifico ningun dato.', v_usuario_id;
    END IF;

    -- Conservar los IDs necesarios antes de borrar sus asociaciones locales.
    SELECT COALESCE(array_agg(s.id_suscripcion), ARRAY[]::uuid[]),
           COALESCE(array_agg(s.id_suscripcion_mercadopago)
               FILTER (WHERE s.id_suscripcion_mercadopago IS NOT NULL), ARRAY[]::text[])
    INTO v_suscripciones, v_preapprovals
    FROM public.suscripcion s
    WHERE s.usuario_id = v_usuario_id;

    SELECT COALESCE(array_agg(p.id_factura_mercadopago)
               FILTER (WHERE p.id_factura_mercadopago IS NOT NULL), ARRAY[]::text[]),
           COALESCE(array_agg(p.id_transaccion_mercadopago)
               FILTER (WHERE p.id_transaccion_mercadopago IS NOT NULL), ARRAY[]::text[])
    INTO v_facturas, v_transacciones
    FROM public.pago p
    WHERE p.suscripcion_id = ANY(v_suscripciones);

    UPDATE public.notificacion_mercado_pago n
    SET estado_procesamiento = 'REVISION',
        ultimo_motivo = 'REINICIO_QA',
        proximo_procesamiento_en = NULL
    WHERE n.estado_procesamiento = 'PENDIENTE'
      AND n.procesada_en IS NULL
      AND (
          (n.topico = 'subscription_preapproval'
              AND n.id_recurso = ANY(v_preapprovals))
          OR
          (n.topico = 'subscription_authorized_payment'
              AND (n.id_recurso = ANY(v_facturas)
                  OR n.id_pago_mp = ANY(v_transacciones)))
      );
    GET DIAGNOSTICS v_avisos_detenidos = ROW_COUNT;

    -- Primero pagos: existe una FK desde pago hacia suscripcion.
    DELETE FROM public.pago p
    WHERE p.suscripcion_id = ANY(v_suscripciones);
    GET DIAGNOSTICS v_pagos_eliminados = ROW_COUNT;

    DELETE FROM public.suscripcion s
    WHERE s.usuario_id = v_usuario_id;
    GET DIAGNOSTICS v_suscripciones_eliminadas = ROW_COUNT;

    UPDATE public.usuario u
    SET es_premium = false
    WHERE u.id_usuario = v_usuario_id;

    RAISE NOTICE 'Reinicio local QA completado: usuario % (%), suscripciones eliminadas %, pagos eliminados %, avisos detenidos %.',
        v_usuario_id, v_email, v_suscripciones_eliminadas,
        v_pagos_eliminados, v_avisos_detenidos;
END;
$reinicio_qa$;
