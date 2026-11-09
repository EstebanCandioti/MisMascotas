// Ejecuta Newman sin persistir respuestas, contraseñas, códigos ni tokens.
const fs = require('node:fs');
const path = require('node:path');
const raiz = path.resolve(__dirname, '../..');
const candidatos = [path.join(raiz, '.qa/auth-runtime/node_modules/newman'), path.join(raiz, 'runtime/node_modules/newman'), 'newman'];
let newman;
for (const candidato of candidatos) { try { newman = require(candidato); break; } catch {} }
if (!newman) throw new Error('Instalar Newman: npm.cmd install --prefix .qa/auth-runtime --no-audit --no-fund newman@6.2.3');
const args = process.argv.slice(2);
const opcion = nombre => { const i = args.indexOf(nombre); return i >= 0 ? args[i + 1] : undefined; };
const coleccion = JSON.parse(fs.readFileSync(path.join(raiz, 'postman/MisMascotas Auth.postman_collection.json')));
const seleccion = opcion('--caso');
if (seleccion) {
    if (!/^AUT-(REG|LOG|2FA|SES)-\d{3}$/.test(seleccion)) throw new Error('ID de caso inválido.');
    coleccion.item.forEach(g => { g.item = g.item.filter(i => i.name.startsWith(seleccion + ' ')); });
    coleccion.item = coleccion.item.filter(g => g.item.length);
    if (!coleccion.item.length) throw new Error('El caso no existe en la colección.');
}
const estados = {};
for (const grupo of coleccion.item) for (const item of grupo.item) {
    const id = item.name.split(' ')[0];
    estados[id] = { id, estado: 'NO_EJECUTADO', verificaciones: 0, pendientes: 0, fallos: [] };
}
const inicio = new Date().toISOString();
const salida = opcion('--salida') || path.join(raiz, 'docs/testing/evidencias', 'AUTH-' + inicio.replace(/[:.]/g, '-') + '-' + process.pid);
fs.mkdirSync(salida, { recursive: true });
const fichero = path.join(salida, 'resumen_auth.json');
if (fs.existsSync(fichero)) throw new Error('La salida ya existe; no se sobrescribe evidencia.');
const esperar = args.includes('--esperar-vencimientos');
const configuracion = { collection: coleccion, reporters: [], iterationCount: 1,
    timeoutRequest: 15000, timeoutScript: esperar ? 1250000 : 60000,
    envVar: [{ key: 'qa_esperar_vencimientos', value: String(esperar) }] };
if (opcion('--codigo-ttl-ms')) configuracion.envVar.push({ key: 'qa_codigo_ttl_ms', value: opcion('--codigo-ttl-ms') });
const entorno = opcion('--entorno');
if (entorno) configuracion.environment = JSON.parse(fs.readFileSync(entorno));
const registro = evento => estados[String(evento.item?.name || '').split(' ')[0]];
newman.run(configuracion, (error, resumen) => {
    const problemas = (resumen?.run?.failures || []).filter(f => f.error?.name !== 'AssertionError');
    for (const problema of problemas) {
        const id = String(problema.source?.name || '').split(' ')[0];
        if (estados[id]) { estados[id].estado = 'ERROR_EJECUCION'; estados[id].fallos.push('Error de red o script; revisar ejecución local.'); }
    }
    const resultados = Object.values(estados);
    const informe = { inicio, fin: new Date().toISOString(), alcance: 'API Auth; no certifica interfaz ni integridad SQL completa.',
        error_general: Boolean(error), errores_ejecucion: problemas.length, resultados };
    fs.writeFileSync(fichero, JSON.stringify(informe, null, 2) + '\n', { flag: 'wx' });
    for (const caso of resultados) console.log(caso.id + ': ' + caso.estado + ' (' + caso.verificaciones + ' verificaciones; ' + caso.pendientes + ' pendientes)');
    console.log('Informe saneado: ' + fichero);
    process.exitCode = error || problemas.length || resultados.some(r => r.estado !== 'APROBADO_API') ? 1 : 0;
}).on('assertion', (error, evento) => {
    const caso = registro(evento);
    if (!caso) return;
    caso.verificaciones++;
    if (evento.error || error) {
        caso.estado = evento.assertion.includes('BLOQUEADO:') ? 'BLOQUEADO' : 'FALLÓ';
        caso.fallos.push(evento.assertion);
    } else if (evento.skipped) {
        caso.pendientes++;
        if (!['FALLÓ', 'BLOQUEADO'].includes(caso.estado)) caso.estado = 'PARCIAL';
    } else if (caso.estado === 'NO_EJECUTADO') caso.estado = 'APROBADO_API';
});
