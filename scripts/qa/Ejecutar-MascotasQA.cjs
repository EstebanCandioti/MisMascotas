// Ejecuta las tandas existentes y agrega una fila por resultado al historial.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const { crearPruebasMascotas } = require('./mascotas-casos.cjs');
const raiz = path.resolve(process.env.MASCOTAS_QA_RAIZ || path.resolve(__dirname, '../..'));
const args = process.argv.slice(2);
const opcion = nombre => { const i = args.indexOf(nombre); return i >= 0 ? args[i + 1] : undefined; };
const tanda = opcion('--tanda') || 'api';
if (!['api', 'web'].includes(tanda)) throw new Error('Usar --tanda api o --tanda web.');
const base = 'http://localhost:8080';
const mail = 'http://localhost:8025';
const web = 'http://localhost:3000';
const ahoraArgentina = () => new Date(Date.now() - 10800000).toISOString().replace('Z', '-03:00');
const hash = valor => crypto.createHash('sha256').update(valor).digest('hex');
const archivoCSV = path.join(raiz, 'docs/testing/ejecuciones_pruebas.csv');
const encabezado = 'id_ejecucion;id_caso;fecha_hora;version_codigo;entorno;datos_utilizados;resultado;observaciones;evidencia;incidencia;ejecutor';
const escapar = valor => /[;"\r\n]/.test(String(valor ?? '')) ? '"' + String(valor ?? '').replaceAll('"', '""') + '"' : String(valor ?? '');
const catalogo = fs.readFileSync(path.join(raiz, 'docs/testing/casos_prueba.csv'), 'utf8').split(/\r?\n/).filter(l => l.startsWith('MAS-')).map(l => {
    if (l.includes('"')) throw new Error('Adaptar el lector para campos entrecomillados.');
    const [id, modulo, flujo, escenario, datos, accion, esperado] = l.split(';'); return { id, escenario, datos, accion, esperado };
});
if (catalogo.length !== 19 || new Set(catalogo.map(c => c.id)).size !== 19) throw new Error('El catálogo de Mascotas debe tener los 19 casos revisados.');
const uiIds = ['MAS-LIS-002', 'MAS-ELI-001', 'MAS-ELI-002'];
const seleccion = catalogo.filter(c => (tanda === 'web') === uiIds.includes(c.id)).filter(c => !opcion('--caso') || c.id === opcion('--caso'));
if (!seleccion.length) throw new Error('No hay casos de esa selección en la tanda.');
const idCorrida = 'MAS-' + tanda.toUpperCase() + '-' + new Date().toISOString().replace(/[-:.]/g, '') + '-' + crypto.randomBytes(3).toString('hex');
const carpeta = path.join(raiz, 'docs/testing/evidencias', idCorrida);
fs.mkdirSync(carpeta, { recursive: false });
const csvOriginal = fs.readFileSync(archivoCSV);
if (csvOriginal.toString('utf8').replace(/^\uFEFF/, '').split(/\r?\n/)[0] !== encabezado) throw new Error('El CSV no tiene el esquema previsto.');
const respaldo = path.join(raiz, '.qa/respaldos', idCorrida);
fs.mkdirSync(respaldo, { recursive: true }); fs.writeFileSync(path.join(respaldo, 'ejecuciones_pruebas.csv'), csvOriginal, { flag: 'wx' });
let ultimoHash = hash(csvOriginal);
function docker(argumentos, entrada) {
    const r = spawnSync('docker', argumentos, { cwd: raiz, input: entrada, encoding: 'utf8', windowsHide: true, timeout: 20000, maxBuffer: 8 * 1024 * 1024 });
    if (r.status !== 0) throw new Error('No se pudo consultar Docker/SQL de QA. No se exporta la salida de diagnóstico con variables sensibles.');
    return r.stdout.trim();
}
function sql(consulta) {
    return docker(['exec', '-i', 'mismascotas-web-testing-db-web-testing-1', 'sh', '-c',
        'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q -t -A -v ON_ERROR_STOP=1'], 'BEGIN READ ONLY;\n' + consulta + '\nCOMMIT;\n');
}
function observar(usuario) {
    if (!/^[a-f0-9-]{36}$/i.test(usuario)) throw new Error('Identificador SQL inválido.');
    return JSON.parse(sql(`SELECT json_build_object(
        'mascotas',coalesce((SELECT json_agg(m ORDER BY m.id_mascota) FROM public.mascota m WHERE propietario_id='${usuario}'::uuid),'[]'::json),
        'eventos',coalesce((SELECT json_agg(e ORDER BY e.id_evento) FROM public.evento_clinico e JOIN public.mascota m ON m.id_mascota=e.mascota_id WHERE m.propietario_id='${usuario}'::uuid),'[]'::json),
        'recordatorios',coalesce((SELECT json_agg(r ORDER BY r.id_recordatorio) FROM public.recordatorio r JOIN public.mascota m ON m.id_mascota=r.mascota_id WHERE m.propietario_id='${usuario}'::uuid),'[]'::json));`));
}
function huellaSeed() {
    return sql(`SELECT md5(json_build_object(
        'mascotas',(SELECT json_agg(m ORDER BY id_mascota) FROM public.mascota m WHERE id_mascota::text LIKE '20000000-0000-4000-8000-%'),
        'eventos',(SELECT json_agg(e ORDER BY id_evento) FROM public.evento_clinico e WHERE id_evento::text LIKE '30000000-0000-4000-8000-%'),
        'recordatorios',(SELECT json_agg(r ORDER BY id_recordatorio) FROM public.recordatorio r WHERE id_recordatorio::text LIKE '40000000-0000-4000-8000-%'))::text);`);
}
async function pedir(url, metodo = 'GET', cuerpo, token) {
    let respuesta;
    try { respuesta = await fetch(url, { method: metodo, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
        ...(cuerpo === undefined || metodo === 'GET' ? {} : { body: JSON.stringify(cuerpo) }), signal: AbortSignal.timeout(15000) }); }
    catch { throw new Error('No se pudo completar una solicitud HTTP local en 15 segundos.'); }
    const texto = await respuesta.text(); let body = {};
    try { if (texto) body = JSON.parse(texto); } catch { body = { respuesta_no_json: true }; }
    return { status: respuesta.status, body };
}
const rutaDefectos = path.join(raiz, 'docs/testing/defectos_mascotas.json');
const defectos = fs.existsSync(rutaDefectos) ? JSON.parse(fs.readFileSync(rutaDefectos, 'utf8')) : {};
function agregarDefectos(resultado, relativo) {
    for (const chequeo of resultado.comprobaciones.filter(c => !c.cumple && c.defecto)) {
        const id = 'DEF-' + chequeo.defecto;
        if (!defectos[id]) defectos[id] = { id, estado: 'Abierto', titulo: chequeo.nombre,
            impacto: /PESO|HISTORIA/.test(id) ? 'Inconsistencia entre perfil básico e historial clínico.' : 'Datos inválidos aceptados o errores internos ante entradas de usuario.',
            casos: [], observaciones: [] };
        const d = defectos[id];
        if (!d.casos.includes(resultado.id_caso)) d.casos.push(resultado.id_caso);
        d.observaciones.push({ id_ejecucion: resultado.id_ejecucion, evidencia: relativo + '/resultado.json',
            comprobacion: chequeo.nombre, esperado: chequeo.esperado, observado: chequeo.observado,
            reproduccion: 'Ejecutar --tanda ' + tanda + ' --caso ' + resultado.id_caso + '. Ver la variante y solicitudes saneadas en la evidencia.' });
    }
    fs.writeFileSync(rutaDefectos, JSON.stringify(defectos, null, 2) + '\n');
    let md = '# Defectos consolidados de Mascotas\n\nSolo comportamientos observados; las funciones ausentes se listan como bloqueos en `mascotas_postman.md`. Los defectos permanecen abiertos hasta una corrección y una nueva ejecución verificable.\n\n';
    md += '| Defecto | Casos | Estado | Comportamiento |\n|---|---|---|---|\n';
    for (const d of Object.values(defectos)) md += `| [${d.id}](#${d.id.toLowerCase()}) | ${d.casos.join(', ')} | ${d.estado} | ${d.titulo.replaceAll('|', '/')} |\n`;
    for (const d of Object.values(defectos)) {
        md += `\n## ${d.id}\n\n${d.titulo}. ${d.impacto}\n\n${d.nota ? d.nota + '\n\n' : ''}`;
        for (const o of d.observaciones) md += `- [${o.id_ejecucion}](${o.evidencia.replace('docs/testing/', '')}): ${o.comprobacion}. Esperado: ${JSON.stringify(o.esperado)}. Observado: ${JSON.stringify(o.observado)}. ${o.reproduccion}\n`;
    }
    fs.writeFileSync(path.join(raiz, 'docs/testing/defectos_mascotas.md'), md);
}
function registrar(resultado, caso, ficha) {
    resultado.id_ejecucion = idCorrida + '__' + resultado.id_caso + '__01';
    resultado.esperado_catalogo = caso.esperado;
    resultado.escenario = caso.escenario;
    resultado.fecha_hora = ahoraArgentina();
    resultado.ficha = '../entorno.json';
    resultado.versiones = ficha.servicios;
    const relativo = 'docs/testing/evidencias/' + idCorrida + '/' + resultado.id_caso + '__01';
    const destino = path.join(raiz, relativo);
    fs.mkdirSync(destino, { recursive: true });
    fs.writeFileSync(path.join(destino, 'resultado.json'), JSON.stringify(resultado, null, 2) + '\n', { flag: 'wx' });
    agregarDefectos(resultado, relativo);
    const fallos = resultado.comprobaciones.filter(c => !c.cumple);
    const ids = [...new Set(fallos.map(c => c.defecto ? 'DEF-' + c.defecto : '').filter(Boolean))];
    const version = 'back img:' + (ficha.servicios?.['backend-web-testing']?.imagen_id || 'no capturada').replace('sha256:', '').slice(0, 12) +
        (tanda === 'web' ? ' / web img:' + (ficha.servicios?.['frontend-web-testing']?.imagen_id || 'no capturada').replace('sha256:', '').slice(0, 12) : ' / web: no intervino');
    const observacion = resultado.motivo || (fallos.length ? fallos.length + ' verificaciones contradicen el esperado. ' + ids.join(', ') : resultado.comprobaciones.length + ' verificaciones satisfactorias.');
    const fila = [resultado.id_ejecucion, resultado.id_caso, resultado.fecha_hora, version,
        'mismascotas-web-testing / ' + (resultado.capa || (tanda === 'web' ? 'Web + API + SQL' : 'API + SQL')),
        resultado.datos.usuarios.map(u => u.email + ' (' + u.plan + ')').join(' / ') || 'Sin fixture: dependencia ausente', resultado.resultado,
        observacion + (tanda === 'api' ? ' UI sin probar.' : ' UI, API y SQL según evidencia.'), relativo + '/resultado.json',
        ids.length ? 'docs/testing/defectos_mascotas.md' : '', 'Codex / Node HTTP, PostgreSQL' + (tanda === 'web' ? ' y Playwright' : '')];
    const actual = fs.readFileSync(archivoCSV);
    if (hash(actual) !== ultimoHash) throw new Error('El historial cambió durante la tanda. Evidencia preservada; conciliar antes de agregar filas.');
    const salto = actual.includes(Buffer.from('\r\n')) ? '\r\n' : '\n';
    fs.appendFileSync(archivoCSV, (actual.at(-1) === 10 ? '' : salto) + fila.map(escapar).join(';') + salto, 'utf8');
    ultimoHash = hash(fs.readFileSync(archivoCSV));
    console.log(resultado.id_caso + ': ' + resultado.resultado + ' (' + resultado.comprobaciones.length + ' verificaciones; ' + fallos.length + ' fallidas)');
}
async function main() {
    const ficha = { id_corrida: idCorrida, capturada_en: ahoraArgentina(), capa: tanda, proyecto: 'mismascotas-web-testing',
        servicios: {}, origen_build: 'No verificado: el commit del checkout no se atribuye a las imágenes.',
        catalogo_sha256: hash(fs.readFileSync(path.join(raiz, 'docs/testing/casos_prueba.csv'))),
        ejecutor_sha256: hash(fs.readFileSync(__filename)), biblioteca_sha256: hash(fs.readFileSync(path.join(__dirname, 'mascotas-casos.cjs'))),
        version_node: process.version, sin_restauracion: true, sql: 'Solo lectura; fixtures nuevos por API. Seed original comparado antes/después.' };
    const resultados = []; let interfaz, causaGlobal, huellaAntes;
    try {
        for (const servicio of ['db-web-testing', 'backend-web-testing', 'mailpit-web-testing', ...(tanda === 'web' ? ['frontend-web-testing'] : [])]) {
            const contenedor = JSON.parse(docker(['inspect', 'mismascotas-web-testing-' + servicio + '-1']))[0];
            const labels = contenedor.Config.Labels;
            if (labels['com.docker.compose.project'] !== 'mismascotas-web-testing' || labels['com.docker.compose.service'] !== servicio || contenedor.State.Health?.Status !== 'healthy') throw new Error('Servicio fuera de QA o no saludable: ' + servicio);
            if (servicio === 'db-web-testing' && !contenedor.Mounts.some(m => m.Name === 'mismascotas_web_testing_db' && m.Destination === '/var/lib/postgresql/data')) throw new Error('Volumen QA no reconocido.');
            const puertoEsperado = { 'backend-web-testing': ['8080/tcp', '8080'], 'mailpit-web-testing': ['8025/tcp', '8025'], 'frontend-web-testing': ['3000/tcp', '3000'] }[servicio];
            if (puertoEsperado && !(contenedor.NetworkSettings.Ports[puertoEsperado[0]] || []).some(p => p.HostPort === puertoEsperado[1])) throw new Error('La URL de pruebas no coincide con el puerto publicado del servicio QA: ' + servicio);
            if (servicio === 'backend-web-testing') {
                const smtp = Object.fromEntries(contenedor.Config.Env.map(v => { const i = v.indexOf('='); return [v.slice(0, i), v.slice(i + 1)]; }));
                if (smtp.MAIL_HOST !== 'mailpit-web-testing' || smtp.MAIL_PORT !== '1025' || smtp.MAIL_SMTP_AUTH !== 'false' || smtp.MAIL_USERNAME || smtp.MAIL_PASSWORD) throw new Error('El SMTP del backend no está aislado en Mailpit QA.');
            }
            ficha.servicios[servicio] = { imagen_id: contenedor.Image, contenedor_id: contenedor.Id, salud: contenedor.State.Health.Status, iniciado_en: contenedor.State.StartedAt };
        }
        huellaAntes = huellaSeed(); ficha.huella_seed_antes = huellaAntes;
        if (tanda === 'web') interfaz = await require('./mascotas-ui.cjs').crearInterfazMascotas(carpeta, web);
    } catch (e) { causaGlobal = e.message; }
    fs.writeFileSync(path.join(carpeta, 'entorno.json'), JSON.stringify(ficha, null, 2) + '\n');
    const docCuentas = fs.readFileSync(path.join(raiz, 'docs/testing/cuentas_qa.md'), 'utf8');
    const premium = docCuentas.match(/`(qa\.premium@mismascotas\.test)`\s*\|\s*`([^`]+)`/);
    const pruebas = crearPruebasMascotas({ uuid: () => crypto.randomUUID(), http: (m, p, b, t) => pedir(base + p, m, b, t),
        mail: p => pedir(mail + p), observar, premium: premium ? { email: premium[1], password: premium[2] } : null,
        ...(interfaz ? { ui: (c, h) => interfaz.ejecutar(c, h) } : {}) });
    try {
        for (const caso of seleccion) {
            fs.mkdirSync(path.join(carpeta, caso.id + '__01'), { recursive: true });
            let resultado;
            if (causaGlobal) resultado = { id_caso: caso.id, inicio: new Date().toISOString(), fin: new Date().toISOString(), resultado: 'Bloqueada', motivo: causaGlobal, datos: { usuarios: [], mascotas: [] }, comprobaciones: [] };
            else {
                const c = await pruebas.preparar(caso.id);
                let r;
                if (!c.bloqueo && !uiIds.includes(caso.id)) {
                    try {
                        r = await pedir(base + c.accion.ruta, c.accion.metodo, c.accion.cuerpo, c.accion.token);
                        c.resultado.solicitudes.push({ fase: 'acción principal', metodo: c.accion.metodo, ruta: c.accion.ruta,
                            cuerpo: c.accion.cuerpo ?? null, http: r.status, respuesta: r.body });
                    } catch (e) { c.bloqueo = e.message; c.resultado.resultado = 'Bloqueada'; c.resultado.motivo = e.message; }
                }
                resultado = await pruebas.finalizar(c, r);
            }
            registrar(resultado, caso, ficha); resultados.push(resultado);
            fs.writeFileSync(path.join(carpeta, 'resumen.json'), JSON.stringify({ id_corrida: idCorrida, tanda, resultados: resultados.map(r => ({ id: r.id_caso, resultado: r.resultado, evidencia: r.id_caso + '__01/resultado.json' })) }, null, 2) + '\n');
        }
    } finally { if (interfaz) await interfaz.cerrar(); }
    if (huellaAntes) {
        const huellaDespues = huellaSeed(); ficha.huella_seed_despues = huellaDespues; ficha.seed_original_intacto = huellaAntes === huellaDespues;
        fs.writeFileSync(path.join(carpeta, 'entorno.json'), JSON.stringify(ficha, null, 2) + '\n');
        if (huellaAntes !== huellaDespues) throw new Error('La huella de los fixtures seed cambió. Investigar con evidencia antes de continuar.');
    }
    console.log('Corrida y registro: ' + idCorrida);
    process.exitCode = resultados.some(r => r.resultado !== 'Aprobada') ? 1 : 0;
}
main().catch(e => {
    fs.writeFileSync(path.join(carpeta, 'error_ejecutor.json'), JSON.stringify({ tipo: e.name,
        traza_sin_mensaje: String(e.stack || '').split('\n').slice(1), fecha: ahoraArgentina() }, null, 2) + '\n');
    console.error('La tanda se interrumpió. La evidencia y las filas ya registradas se conservan en ' + carpeta);
    process.exitCode = 2;
});
