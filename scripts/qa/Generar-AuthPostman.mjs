import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const csv = process.argv[2] || path.join(raiz, 'docs/testing/casos_prueba.csv');
// Analizador de CSV con punto y coma, campos entre comillas y saltos de línea.
function leerCSV(texto) {
    const filas = []; let fila = [], campo = '', comillas = false;
    texto = texto.replace(/^\uFEFF/, '');
    for (let i = 0; i < texto.length; i++) {
        const c = texto[i];
        if (c === '"') {
            if (comillas && texto[i + 1] === '"') { campo += '"'; i++; }
            else comillas = !comillas;
        } else if (c === ';' && !comillas) { fila.push(campo); campo = ''; }
        else if (c === '\n' && !comillas) { fila.push(campo.replace(/\r$/, '')); filas.push(fila); fila = []; campo = ''; }
        else campo += c;
    }
    if (comillas) throw new Error('CSV con comillas sin cerrar.');
    if (campo || fila.length) { fila.push(campo.replace(/\r$/, '')); filas.push(fila); }
    const cabecera = filas.shift();
    return filas.filter(f => f.some(Boolean)).map(f => Object.fromEntries(cabecera.map((k, i) => [k, f[i]])));
}
const casos = leerCSV(fs.readFileSync(csv, 'utf8')).filter(c => /^AUT-(REG|LOG|2FA|SES)-\d{3}$/.test(c.id));
const ids = ['REG:17', 'LOG:6', '2FA:6', 'SES:6'].flatMap(g => {
    const [grupo, cantidad] = g.split(':');
    return Array.from({ length: Number(cantidad) }, (_, i) => `AUT-${grupo}-${String(i + 1).padStart(3, '0')}`);
});
if (casos.length !== 35 || new Set(casos.map(c => c.id)).size !== 35 || ids.some(id => !casos.some(c => c.id === id)))
    throw new Error('Se requieren exactamente los 35 IDs acordados de Auth. Revisar el catálogo antes de generar.');
const biblioteca = fs.readFileSync(path.join(raiz, 'scripts/qa/auth-postman.js'), 'utf8');
const variables = { baseUrl: 'http://localhost:8080', mailpitUrl: 'http://localhost:8025',
    qa_mail_timeout_ms: '30000', qa_esperar_vencimientos: 'false', qa_codigo_ttl_ms: '600000' };
const coleccion = {
    info: { _postman_id: 'c0689999-0fe3-4a89-8d87-4102c37ee185', name: 'MisMascotas - Auth QA (35 casos CSV)',
        schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
        description: 'Casos AUT-REG-001 a AUT-SES-006 del CSV. Mailpit automático. Cada caso prepara datos aislados. Resultados API; ver docs/testing/auth_postman.md para bloqueos y cobertura parcial. Genera cuentas temporales; no restaura la base.' },
    auth: { type: 'noauth' },
    variable: Object.entries(variables).map(([key, value]) => ({ key, value, type: 'string' })),
    event: ['prerequest', 'test'].map(listen => ({ listen, script: { type: 'text/javascript',
        exec: (biblioteca + '\n(async () => { await crearQA(pm).' + (listen === 'prerequest' ? 'antes' : 'despues') + '(); })();').split('\n') } })),
    item: ['REG', 'LOG', '2FA', 'SES'].map(grupo => ({ name: { REG: '01 Registro', LOG: '02 Login', '2FA': '03 Verificación 2FA', SES: '04 Sesión' }[grupo],
        item: casos.filter(c => c.id.startsWith('AUT-' + grupo + '-')).map(c => {
            const recurso = grupo === 'REG' ? '/auth/register' : grupo === 'LOG' ? '/auth/login' : grupo === '2FA' ? '/auth/verificar-codigo' :
                ['003', '004'].includes(c.id.slice(-3)) ? '/auth/refresh' : '/usuario/me';
            const metodo = recurso === '/usuario/me' ? 'GET' : 'POST';
            return { name: c.id + ' — ' + c.escenario, request: { method: metodo,
                auth: { type: 'noauth' }, header: [
                    { key: 'Content-Type', value: 'application/json' },
                    ...((grupo === '2FA' || (grupo === 'SES' && ['001', '006'].includes(c.id.slice(-3)))) ? [{ key: 'Authorization', value: 'Bearer {{qa_token}}' }] : [])
                ], url: '{{baseUrl}}' + recurso,
                ...(metodo === 'POST' ? { body: { mode: 'raw', raw: '{{qa_cuerpo}}', options: { raw: { language: 'json' } } } } : {}),
                description: `**ID:** ${c.id}\n\n**Precondiciones:** ${c.precondiciones_y_datos}\n\n**Acción:** ${c.accion}\n\n**Esperado del CSV:** ${c.resultado_esperado}\n\n**Prioridad:** ${c.prioridad}\n\nPreparación automática y verificaciones adicionales mediante scripts de colección. Consultar la guía para partes manuales o bloqueadas.` }, response: [] };
        }) }))
};
fs.mkdirSync(path.join(raiz, 'postman'), { recursive: true });
fs.writeFileSync(path.join(raiz, 'postman/MisMascotas Auth.postman_collection.json'), JSON.stringify(coleccion, null, 2) + '\n');
fs.writeFileSync(path.join(raiz, 'postman/MisMascotas Auth QA.postman_environment.json'), JSON.stringify({
    name: 'MisMascotas Auth QA local', values: Object.entries(variables).map(([key, value]) => ({ key, value, type: 'default', enabled: true })),
    _postman_variable_scope: 'environment'
}, null, 2) + '\n');
console.log('Colección y entorno generados: 35 casos, IDs y esperados conservados.');
