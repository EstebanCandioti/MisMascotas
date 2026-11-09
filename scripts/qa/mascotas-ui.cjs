const path = require('node:path');
const os = require('node:os');
async function crearInterfazMascotas(carpeta, url = 'http://localhost:3000') {
    let playwright;
    for (const modulo of ['playwright', process.env.MASCOTAS_PLAYWRIGHT_MODULE,
        path.join(os.homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')].filter(Boolean)) {
        try { playwright = require(modulo); break; } catch {}
    }
    if (!playwright) throw new Error('Falta Playwright para la tanda UI. Instalarlo en el runtime QA o indicar MASCOTAS_PLAYWRIGHT_MODULE.');
    const navegador = await playwright.chromium.launch({ channel: 'msedge', headless: true });
    return {
        cerrar: () => navegador.close(),
        async ejecutar(c, h) {
            const contexto = await navegador.newContext({ viewport: { width: 1280, height: 900 }, timezoneId: 'America/Argentina/Buenos_Aires', locale: 'es-AR' });
            await contexto.route('**/*', route => /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(route.request().url()) ? route.continue() : route.abort());
            await contexto.addInitScript(sesion => sessionStorage.setItem('mis-mascotas.auth.session', JSON.stringify(sesion)), c.actor.sesion);
            const pagina = await contexto.newPage();
            pagina.setDefaultTimeout(12000);
            let borrados = 0;
            pagina.on('request', r => { if (r.method() === 'DELETE' && r.url().includes('/mascotas/' + c.pet?.idMascota)) borrados++; });
            const captura = async nombre => {
                await pagina.screenshot({ path: path.join(carpeta, c.id + '__01', nombre), fullPage: true });
                c.resultado.capturas = [...(c.resultado.capturas || []), nombre];
            };
            try {
                await pagina.goto(url + (c.id === 'MAS-LIS-002' ? '/mascotas' : '/mascotas/' + c.pet.idMascota));
                if (c.id === 'MAS-LIS-002') {
                    await pagina.getByText('Todavía no cargaste mascotas', { exact: true }).waitFor();
                    h.comprobar(c, 'Estado vacío visible sin error', await pagina.getByText('Todavía no cargaste mascotas', { exact: true }).isVisible() && await pagina.getByRole('alert').count() === 0,
                        'Mensaje vacío sin alertas', 'Mensaje mostrado y alertas consultadas', 'MAS-UI-VACIO');
                    const lista = await h.http(c, 'GET', '/mascotas', undefined, c.actor.sesion.accessToken);
                    h.comprobar(c, 'API devuelve listado vacío', lista.status === 200 && Array.isArray(lista.body) && lista.body.length === 0, [], lista.body, 'MAS-UI-VACIO');
                    await captura('estado_vacio.png');
                    await pagina.getByRole('button', { name: 'Agregar mascota', exact: true }).first().click();
                    await pagina.getByRole('dialog', { name: 'Agregar mascota' }).waitFor();
                    h.comprobar(c, 'Acción inicial abre el formulario de alta', true, 'Formulario visible', 'Formulario visible', 'MAS-UI-VACIO');
                    await captura('accion_inicial.png');
                    await pagina.getByRole('dialog').getByRole('button', { name: 'Cancelar', exact: true }).click();
                } else {
                    await pagina.getByRole('button', { name: 'Eliminar mascota', exact: true }).waitFor();
                    await captura('antes.png');
                    await pagina.getByRole('button', { name: 'Eliminar mascota', exact: true }).click();
                    const dialogo = pagina.getByRole('alertdialog');
                    await dialogo.waitFor();
                    h.comprobar(c, 'Confirmación identifica la mascota y todavía no envía DELETE',
                        (await dialogo.innerText()).includes(c.pet.nombre) && borrados === 0,
                        'Mascota identificada; 0 DELETE antes de confirmar', { texto: await dialogo.innerText(), borrados }, 'MAS-UI-CONFIRMACION');
                    await captura('confirmacion.png');
                    if (c.id === 'MAS-ELI-002') {
                        await dialogo.getByRole('button', { name: 'Cancelar', exact: true }).click();
                        await dialogo.waitFor({ state: 'hidden' });
                        const detalle = await h.http(c, 'GET', '/mascotas/' + c.pet.idMascota, undefined, c.actor.sesion.accessToken);
                        h.comprobar(c, 'Cancelar conserva la mascota y no envía DELETE', borrados === 0 && detalle.status === 200, '0 DELETE y HTTP 200', { borrados, http: detalle.status }, 'MAS-UI-CANCELACION');
                        const despues = await h.observar(c, 'despues');
                        h.comprobar(c, 'SQL: mascota y relaciones permanecen intactas', JSON.stringify(c.resultado.sql.antes) === JSON.stringify(despues), 'Instantáneas iguales', 'Comparación SQL', 'MAS-UI-CANCELACION');
                    } else {
                        await dialogo.getByRole('button', { name: 'Eliminar', exact: true }).click();
                        await pagina.waitForURL(url + '/mascotas');
                        const detalle = await h.http(c, 'GET', '/mascotas/' + c.pet.idMascota, undefined, c.actor.sesion.accessToken);
                        const lista = await h.http(c, 'GET', '/mascotas', undefined, c.actor.sesion.accessToken);
                        h.comprobar(c, 'Confirmar elimina del listado y deniega detalle activo', borrados === 1 && detalle.status === 404 && !lista.body.some(m => m.idMascota === c.pet.idMascota),
                            '1 DELETE, detalle 404 y ausencia del listado', { borrados, detalle: detalle.status, lista: lista.body }, 'MAS-BORRADO-LOGICO');
                        const despues = await h.observar(c, 'despues');
                        const fila = despues.mascotas.find(m => m.id_mascota === c.pet.idMascota);
                        h.comprobar(c, 'SQL: fila retenida con fecha de eliminación e historial conservado',
                            Boolean(fila?.fecha_eliminacion) && JSON.stringify(c.resultado.sql.antes.eventos) === JSON.stringify(despues.eventos),
                            'Fila lógica y eventos intactos', { mascota: fila, eventos: despues.eventos }, 'MAS-BORRADO-LOGICO');
                    }
                    await captura('despues.png');
                }
            } catch (e) {
                await captura('interfaz_al_fallar.png').catch(() => {});
                // El detalle de Playwright puede incluir estado de sesión: no se exporta.
                if (e.name === 'TimeoutError') throw new Error('La interfaz no alcanzó el estado esperado en 12 segundos. Ver captura y solicitudes.');
                throw new Error('No se pudo completar la verificación de interfaz. Ver captura y solicitudes.');
            } finally { await contexto.close(); }
        }
    };
}
module.exports = { crearInterfazMascotas };
