/* Biblioteca incorporada a la colección por Generar-AuthPostman.mjs.
 * Los secretos viven únicamente en el contexto local de cada solicitud. */
function crearQA(pm) {
    const id = pm.info.requestName.split(' ')[0];
    const obtener = (k, defecto) => pm.variables.get(k) ?? defecto;
    const api = String(obtener('baseUrl', 'http://localhost:8080')).replace(/\/$/, '');
    const mail = String(obtener('mailpitUrl', 'http://localhost:8025')).replace(/\/$/, '');
    let contexto = {};
    let fallo = false;
    let parcial = false;
    const pausa = ms => new Promise(resolve => setTimeout(resolve, ms));
    const registrar = (nombre, condicion) => {
        pm.test(id + ' | ' + nombre, () => {
            if (!condicion) pm.expect.fail(nombre);
        });
        if (!condicion) { fallo = true; throw new Error(nombre); }
    };
    const pendiente = motivo => {
        parcial = true;
        pm.test.skip(id + ' | PENDIENTE: ' + motivo, () => {});
    };
    function resultado(estado, detalle) {
        const datos = JSON.parse(obtener('qa_resultados', '{}'));
        datos[id] = { estado, detalle, fecha: new Date().toISOString() };
        pm.variables.set('qa_resultados', JSON.stringify(datos));
        pm.visualizer.set('<h3>{{id}}: {{estado}}</h3><p>{{detalle}}</p>', { id, estado, detalle });
    }
    async function enviar(ruta, metodo = 'GET', datos, token = '') {
        const header = { 'Content-Type': 'application/json' };
        if (token) header.Authorization = 'Bearer ' + token;
        try {
            return await new Promise((resolve, reject) => pm.sendRequest({ url: ruta.startsWith('http') ? ruta : api + ruta,
                method: metodo, header, auth: { type: 'noauth' },
                ...(datos === undefined ? {} : { body: { mode: 'raw', raw: JSON.stringify(datos) } }) },
                (error, respuesta) => error ? reject(error) : resolve(respuesta)));
        } catch { throw new Error('No se pudo completar una solicitud HTTP auxiliar. Revisar backend/Mailpit.'); }
    }
    const json = r => { try { return r.json(); } catch { throw new Error('La respuesta debe ser JSON.'); } };
    function urlLocal(valor) {
        return /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(valor);
    }
    async function mensajes(email) {
        const r = await enviar(mail + '/api/v1/search?query=' + encodeURIComponent('to:' + email) + '&limit=500');
        if (r.code !== 200) throw new Error('Mailpit no responde HTTP 200.');
        const b = json(r);
        if (!Array.isArray(b.messages)) throw new Error('Respuesta de búsqueda de Mailpit inesperada.');
        return b.messages;
    }
    async function leerCodigo(email, previos, inicio) {
        const limite = Date.now() + Number(obtener('qa_mail_timeout_ms', 30000));
        while (Date.now() < limite) {
            for (const m of await mensajes(email)) {
                if (previos.includes(m.ID)) continue;
                const r = await enviar(mail + '/api/v1/message/' + encodeURIComponent(m.ID));
                if (r.code !== 200) throw new Error('No se pudo leer un mensaje de Mailpit.');
                const d = json(r);
                if (!(d.To || []).some(t => String(t.Address).toLowerCase() === email.toLowerCase())) continue;
                if (Date.parse(d.Date || m.Created) < inicio - 2000) continue;
                const coincidencia = String(d.Text || '').match(/Tu codigo de verificacion es:\s*(\d{6})(?!\d)/);
                if (coincidencia) return coincidencia[1];
            }
            await pausa(400);
        }
        throw new Error('No llegó un código 2FA nuevo para el destinatario dentro del tiempo permitido.');
    }
    function nuevo() {
        const sufijo = pm.variables.replaceIn('{{$guid}}').replace(/-/g, '').toLowerCase();
        return { nombre: 'QA Auth ' + id, email: 'qa.auth.' + sufijo + '@mismascotas.test', password: 'QaAuth2026!' };
    }
    function preauth(r) {
        registrar('HTTP 200: desafío emitido', r.code === 200);
        const b = json(r);
        registrar('Token previo y tipo Bearer, sin sesión definitiva',
            typeof b.token === 'string' && b.token.length > 0 && b.tipo === 'Bearer' && !b.accessToken && !b.refreshToken);
        return b.token;
    }
    function sesion(r) {
        registrar('HTTP 200: sesión emitida', r.code === 200);
        const b = json(r);
        registrar('Pareja completa de tokens y vencimiento informado',
            typeof b.accessToken === 'string' && b.accessToken.length > 0 &&
            typeof b.refreshToken === 'string' && b.refreshToken.length > 0 && b.tipo === 'Bearer' &&
            Date.parse(b.accessTokenExpiraEn) > Date.now());
        contexto.sesiones.push(b.refreshToken);
        return b;
    }
    async function desafio(usuario, ruta) {
        const anteriores = (await mensajes(usuario.email)).map(m => m.ID);
        const inicio = Date.now();
        const token = preauth(await enviar(ruta, 'POST', usuario));
        const codigo = await leerCodigo(usuario.email, anteriores, inicio);
        return { token, codigo };
    }
    async function crearPendiente() {
        contexto.usuario = nuevo();
        contexto.desafio = await desafio(contexto.usuario, '/auth/register');
    }
    async function crearSesion() {
        await crearPendiente();
        contexto.sesion = sesion(await enviar('/auth/verificar-codigo', 'POST',
            { codigo: contexto.desafio.codigo }, contexto.desafio.token));
    }
    function rechazo(r, estado, codigo) {
        registrar('HTTP esperado ' + estado + ', recibido ' + r.code, r.code === estado);
        const b = json(r);
        registrar('Código de error esperado', b.codigo === codigo);
        registrar('No devuelve tokens ni perfil protegido', !b.token && !b.accessToken && !b.refreshToken &&
            !b.idUsuario && !b.email && !b.password && !b.passwordHash);
        return b;
    }
    async function perfil(token) {
        const r = await enviar('/usuario/me', 'GET', undefined, token);
        registrar('Perfil autorizado: HTTP 200', r.code === 200);
        const b = json(r);
        registrar('Identidad, nombre, UUID y plan del usuario propio', b.email === contexto.usuario.email &&
            b.nombre === contexto.usuario.nombre && typeof b.idUsuario === 'string' &&
            /^[0-9a-f-]{36}$/i.test(b.idUsuario) && b.esPremium === false);
        registrar('Perfil sin secretos ni campos adicionales',
            Object.keys(b).every(k => ['idUsuario', 'nombre', 'email', 'esPremium'].includes(k)));
    }
    const regInvalidos = {
        '003': { email: 'email-invalido' }, '004': { password: 'Qa2026!' },
        '005': { email: '' }, '006': { password: '' }, '007': { email: '', password: '' },
        '010': { nombre: '' }, '011': { nombre: '', email: '' },
        '012': { nombre: '', password: '' }, '013': { nombre: '', email: '', password: '' },
        '014': { password: 'sinmayuscula2026' }, '015': { password: 'SinNumerosQA' }
    };
    async function preparar() {
        contexto = { sesiones: [], usuario: nuevo(), cuerpo: {}, token: '', anteriores: [], inicio: Date.now() };
        pm.variables.unset('qa_contexto');
        pm.variables.set('qa_cuerpo', '{}');
        pm.variables.set('qa_token', '');
        if (!urlLocal(api) || !urlLocal(mail)) throw new Error('Esta colección solo admite URLs HTTP locales de QA.');
        await mensajes(contexto.usuario.email);
        const n = id.slice(-3);
        if (id.startsWith('AUT-REG')) {
            contexto.cuerpo = { ...contexto.usuario, ...(regInvalidos[n] || {}) };
            if (['002', '017'].includes(n)) {
                await crearPendiente();
                contexto.cuerpo = { ...contexto.usuario };
                if (n === '017') contexto.cuerpo.email = '  ' + contexto.usuario.email.toUpperCase() + '  ';
            }
            if (n === '016') contexto.cuerpo.password = 'Qa123456';
        } else if (id.startsWith('AUT-LOG')) {
            if (['001', '003'].includes(n)) await crearSesion();
            else if (n !== '002') await crearPendiente();
            contexto.cuerpo = { email: contexto.usuario.email, password: contexto.usuario.password };
            if (n === '003') contexto.cuerpo.password = 'OtraClave2026!';
            if (['004', '006'].includes(n)) contexto.cuerpo.email = '';
            if (['005', '006'].includes(n)) contexto.cuerpo.password = '';
        } else if (id.startsWith('AUT-2FA')) {
            if (['005', '006'].includes(n)) throw new Error('BLOQUEADO: el backend no ofrece endpoint ni contrato de reenvío de registro.');
            if (n === '004' && String(obtener('qa_esperar_vencimientos', 'false')) !== 'true')
                throw new Error('BLOQUEADO: activar qa_esperar_vencimientos y usar pre-auth con vigencia mayor que el código.');
            await crearPendiente();
            if (n === '002') contexto.desafio = await desafio(contexto.usuario, '/auth/login');
            if (n === '004') {
                const ttl = Number(obtener('qa_codigo_ttl_ms', 600000));
                const payload = JSON.parse(atob(contexto.desafio.token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
                if (!(ttl > 0 && ttl <= 1200000) || payload.exp * 1000 <= Date.now() + ttl + 5000)
                    throw new Error('BLOQUEADO: el pre-auth vencería antes de poder comprobar el código expirado. Revisar vigencias de QA.');
                await pausa(ttl + 1000);
            }
            contexto.cuerpo = { codigo: contexto.desafio.codigo };
            if (n === '003') contexto.cuerpo.codigo = contexto.desafio.codigo.slice(0, -1) +
                ((Number(contexto.desafio.codigo.slice(-1)) + 1) % 10);
            contexto.token = contexto.desafio.token;
        } else {
            await crearSesion();
            if (['001', '006'].includes(n)) contexto.token = contexto.sesion.accessToken;
            if (['003', '004'].includes(n)) {
                contexto.cuerpo = { refreshToken: contexto.sesion.refreshToken };
                if (n === '004') contexto.rotada = sesion(await enviar('/auth/refresh', 'POST', contexto.cuerpo));
            }
            if (n === '006') contexto.rotada = sesion(await enviar('/auth/refresh', 'POST', { refreshToken: contexto.sesion.refreshToken }));
        }
        contexto.anteriores = (await mensajes(contexto.usuario.email)).map(m => m.ID);
        contexto.inicio = Date.now();
        pm.variables.set('qa_cuerpo', JSON.stringify(contexto.cuerpo));
        pm.variables.set('qa_token', contexto.token);
        pm.variables.set('qa_contexto', JSON.stringify(contexto));
    }
    async function verificar(r) {
        contexto = JSON.parse(obtener('qa_contexto', '{}'));
        const n = id.slice(-3);
        if (id.startsWith('AUT-REG')) {
            if (['002', '017'].includes(n)) rechazo(r, 409, 'ERROR_NEGOCIO');
            else if (regInvalidos[n]) {
                const b = rechazo(r, 400, 'ERROR_VALIDACION');
                for (const campo of Object.keys(regInvalidos[n])) registrar('Validación informa el campo ' + campo,
                    typeof b.mensaje === 'string' && b.mensaje.includes(campo + ':'));
                const recibidos = await mensajes(contexto.usuario.email);
                registrar('No genera correo de registro rechazado', recibidos.every(m => contexto.anteriores.includes(m.ID)));
                // Para emails utilizables, un login posterior debe seguir sin encontrar una cuenta.
                if (contexto.cuerpo.email === contexto.usuario.email)
                    rechazo(await enviar('/auth/login', 'POST', contexto.usuario), 401, 'NO_AUTENTICADO');
            } else {
                const token = preauth(r);
                const codigo = await leerCodigo(contexto.usuario.email, contexto.anteriores, contexto.inicio);
                rechazo(await enviar('/usuario/me', 'GET', undefined, token), 401, 'NO_AUTENTICADO');
                if (n === '008') {
                    const reanudado = await desafio(contexto.usuario, '/auth/login');
                    const s = sesion(await enviar('/auth/verificar-codigo', 'POST', { codigo: reanudado.codigo }, reanudado.token));
                    await perfil(s.accessToken);
                }
                if (n === '016') {
                    const s = sesion(await enviar('/auth/verificar-codigo', 'POST', { codigo }, token));
                    await perfil(s.accessToken);
                }
                if (n === '009') pendiente('Confirmar navegación directa a 2FA en la interfaz.');
            }
        } else if (id.startsWith('AUT-LOG')) {
            if (n === '001') {
                const token = preauth(r);
                await leerCodigo(contexto.usuario.email, contexto.anteriores, contexto.inicio);
                rechazo(await enviar('/usuario/me', 'GET', undefined, token), 401, 'NO_AUTENTICADO');
            } else {
                const b = rechazo(r, ['002', '003'].includes(n) ? 401 : 400,
                    ['002', '003'].includes(n) ? 'NO_AUTENTICADO' : 'ERROR_VALIDACION');
                for (const campo of ['email', 'password'].filter(k => contexto.cuerpo[k] === ''))
                    registrar('Validación informa ' + campo, String(b.mensaje).includes(campo + ':'));
                const limite = Date.now() + 1200;
                while (Date.now() < limite) {
                    registrar('Sin correo adicional por login rechazado',
                        (await mensajes(contexto.usuario.email)).every(m => contexto.anteriores.includes(m.ID)));
                    await pausa(400);
                }
            }
        } else if (id.startsWith('AUT-2FA')) {
            if (['001', '002'].includes(n)) {
                const s = sesion(r); await perfil(s.accessToken);
                rechazo(await enviar('/auth/verificar-codigo', 'POST', { codigo: contexto.desafio.codigo }, contexto.desafio.token), 400, 'CODIGO_INVALIDO');
            } else if (n === '003') {
                rechazo(r, 400, 'CODIGO_INVALIDO');
                rechazo(await enviar('/usuario/me', 'GET', undefined, contexto.desafio.token), 401, 'NO_AUTENTICADO');
                const s = sesion(await enviar('/auth/verificar-codigo', 'POST', { codigo: contexto.desafio.codigo }, contexto.desafio.token));
                await perfil(s.accessToken);
            } else {
                rechazo(r, 410, 'CODIGO_EXPIRADO');
                const codigo = await leerCodigo(contexto.usuario.email, contexto.anteriores, contexto.inicio);
                const s = sesion(await enviar('/auth/verificar-codigo', 'POST', { codigo }, contexto.desafio.token));
                await perfil(s.accessToken);
            }
        } else {
            if (n === '001' || n === '006') {
                registrar('Access anterior aún vigente: HTTP 200', r.code === 200);
                registrar('Respuesta pertenece al usuario propio', json(r).email === contexto.usuario.email);
                await perfil(contexto.sesion.accessToken);
                if (n === '006') {
                    if (String(obtener('qa_esperar_vencimientos', 'false')) === 'true') {
                        const demora = Date.parse(contexto.sesion.accessTokenExpiraEn) - Date.now() + 1500;
                        if (!(demora > 0 && demora < 1200000)) throw new Error('Vencimiento fuera del límite de espera de 20 minutos.');
                        await pausa(demora);
                        rechazo(await enviar('/usuario/me', 'GET', undefined, contexto.sesion.accessToken), 401, 'NO_AUTENTICADO');
                    } else pendiente('Falta rechazo del access al vencer; activar qa_esperar_vencimientos.');
                }
            } else if (n === '002') {
                rechazo(r, 401, 'NO_AUTENTICADO');
                rechazo(await enviar('/usuario/me', 'GET', undefined, 'qa-token-invalido'), 401, 'NO_AUTENTICADO');
                const partes = contexto.sesion.accessToken.split('.');
                partes[2] = (partes[2][0] === 'A' ? 'B' : 'A') + partes[2].slice(1);
                rechazo(await enviar('/usuario/me', 'GET', undefined, partes.join('.')), 401, 'NO_AUTENTICADO');
            } else if (n === '003') {
                const s = sesion(r);
                registrar('Refresh rotado distinto del anterior', s.refreshToken !== contexto.sesion.refreshToken);
                await perfil(s.accessToken);
                rechazo(await enviar('/auth/refresh', 'POST', { refreshToken: contexto.sesion.refreshToken }), 401, 'NO_AUTENTICADO');
            } else if (n === '004') rechazo(r, 401, 'NO_AUTENTICADO');
            else if (n === '005') {
                registrar('Backend protegido disponible', r.code === 401);
                const enviarRefresh = () => enviar('/auth/refresh', 'POST', { refreshToken: contexto.sesion.refreshToken });
                const respuestas = await Promise.all([enviarRefresh(), enviarRefresh()]);
                const exitosas = respuestas.filter(x => x.code === 200);
                for (const x of exitosas) {
                    const b = json(x); if (b.refreshToken) contexto.sesiones.push(b.refreshToken);
                }
                registrar('Como máximo una renovación simultánea emite sesión; emisiones observadas: ' + exitosas.length, exitosas.length <= 1);
                registrar('Una renovación válida prospera y la repetida devuelve 401',
                    exitosas.length === 1 && respuestas.filter(x => x.code === 401).length === 1);
                sesion(exitosas[0]);
                rechazo(respuestas.find(x => x.code === 401), 401, 'NO_AUTENTICADO');
            }
        }
    }
    async function limpiar() {
        let advertencias = 0;
        for (const token of [...new Set(contexto.sesiones || [])]) {
            try {
                const r = await enviar('/auth/logout', 'POST', { refreshToken: token });
                if (![204, 401].includes(r.code)) advertencias++;
            } catch { advertencias++; }
        }
        if (advertencias) pendiente('No se pudieron cerrar todas las sesiones temporales.');
        ['qa_contexto', 'qa_cuerpo', 'qa_token'].forEach(k => pm.variables.unset(k));
    }
    return {
        async antes() {
            try { await preparar(); }
            catch (e) {
                try { await limpiar(); } catch { /* Mantener el impedimento original. */ }
                resultado('BLOQUEADO', e.message);
                pm.test(id + ' | BLOQUEADO: ' + e.message, () => pm.expect.fail(e.message));
                pm.execution.skipRequest();
            }
        },
        async despues() {
            let detalle = 'Verificaciones de API completas. Persistencia interna y UI fuera del alcance automático.';
            try { await verificar(pm.response); }
            catch (e) {
                detalle = e.message;
                if (!fallo) { fallo = true; pm.test(id + ' | Error de ejecución: ' + detalle, () => pm.expect.fail(detalle)); }
            } finally {
                await limpiar();
                resultado(fallo ? 'FALLÓ' : parcial ? 'PARCIAL' : 'APROBADO_API', detalle);
            }
        }
    };
}
