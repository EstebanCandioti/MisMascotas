const fs = require('node:fs');
const path = require('node:path');
const raiz = path.resolve(__dirname, '../..');
const csv = process.argv[2] || path.join(raiz, 'docs/testing/casos_prueba.csv');
const casos = fs.readFileSync(csv, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)
    .filter(x => x.startsWith('MAS-')).map(linea => {
        // El catálogo actual no tiene campos entrecomillados en este módulo.
        if (linea.includes('"')) throw new Error('Adaptar el lector al CSV entrecomillado antes de regenerar.');
        const [id, modulo, flujo, escenario, datos, accion, esperado, prioridad] = linea.split(';');
        return { id, modulo, flujo, escenario, datos, accion, esperado, prioridad };
    });
const ids = ['REG:5', 'LIS:4', 'DET:2', 'EDI:4', 'ELI:4'].flatMap(g => {
    const [grupo, n] = g.split(':'); return Array.from({ length: Number(n) }, (_, i) => 'MAS-' + grupo + '-' + String(i + 1).padStart(3, '0'));
});
if (casos.length !== 19 || new Set(casos.map(c => c.id)).size !== 19 || ids.some(id => !casos.some(c => c.id === id))) throw new Error('El alcance debe conservar los 19 IDs existentes.');
const biblioteca = fs.readFileSync(path.join(__dirname, 'mascotas-casos.cjs'), 'utf8').replace(/if \(typeof module[^\n]+/, '');
const adaptador = `
const base = String(pm.variables.get('baseUrl')).replace(new RegExp('/$'), '');
const mail = String(pm.variables.get('mailpitUrl')).replace(new RegExp('/$'), '');
const local = u => new RegExp('^http://(localhost|127[.]0[.]0[.]1)(:[0-9]+)?$').test(u);
if (!local(base) || !local(mail)) throw new Error('Solo se admiten URLs HTTP locales QA.');
const enviar = (url, metodo, datos, token) => new Promise((resolve, reject) => pm.sendRequest({
    url, method: metodo, auth: { type: 'noauth' }, header: { 'Content-Type':'application/json', ...(token ? {Authorization:'Bearer '+token}: {}) },
    ...(datos === undefined ? {} : {body:{mode:'raw',raw:JSON.stringify(datos)}})
}, (error,respuesta) => {if(error) return reject(new Error('Fallo HTTP auxiliar.')); let body={};try{body=respuesta.json();}catch{} resolve({status:respuesta.code,body});}));
const pruebas = crearPruebasMascotas({
    uuid:()=>pm.variables.replaceIn('{{$guid}}'),
    http:(m,p,b,t)=>enviar(base+p,m,b,t), mail:p=>enviar(mail+p,'GET'),
    premium:{email:pm.variables.get('qa_premium_email'),password:pm.variables.get('qa_premium_password')},
    comprobacion:(nombre,cumple)=>pm.test(pm.info.requestName.split(' ')[0]+' | '+nombre,()=>{if(!cumple)pm.expect.fail(nombre);})
});`;
const antes = biblioteca + '\n' + adaptador + `
(async()=>{
    pm.variables.set('mas_url', base+'/mascotas'); pm.variables.set('mas_metodo','GET'); pm.variables.set('mas_cuerpo','{}'); pm.variables.set('mas_token','');
    const c=await pruebas.preparar(pm.info.requestName.split(' ')[0]);
    pm.variables.set('mas_contexto',JSON.stringify(c));
    if(c.bloqueo){
        pm.test(c.id+' | BLOQUEADA: '+c.bloqueo,()=>pm.expect.fail(c.bloqueo));
        await pruebas.finalizar(c); pm.variables.unset('mas_contexto'); pm.execution.skipRequest(); return;
    }
    pm.variables.set('mas_url',base+c.accion.ruta); pm.variables.set('mas_metodo',c.accion.metodo);
    pm.request.method=c.accion.metodo;
    pm.variables.set('mas_cuerpo',JSON.stringify(c.accion.cuerpo||{}));
    pm.variables.set('mas_token',c.accion.token);
})();`;
const despues = biblioteca + '\n' + adaptador + `
(async()=>{
    const c=JSON.parse(pm.variables.get('mas_contexto')); let body={};try{body=pm.response.json();}catch{}
    const r=await pruebas.finalizar(c,{status:pm.response.code,body});
    pm.test(c.id+' | Estado '+r.resultado,()=>{if(r.resultado!=='Aprobada')pm.expect.fail(r.motivo||r.resultado);});
    pm.visualizer.set('<h3>{{id_caso}}: {{resultado}}</h3><p>{{motivo}}</p><p>Capa API. Repetir con el ejecutor para evidencia SQL/UI y registro en CSV.</p>',r);
    ['mas_contexto','mas_cuerpo','mas_token'].forEach(k=>pm.variables.unset(k));
})();`;
const variables = { baseUrl: 'http://localhost:8080', mailpitUrl: 'http://localhost:8025',
    qa_premium_email: 'qa.premium@mismascotas.test', qa_premium_password: '' };
const coleccion = { info: { _postman_id: '0684ee1c-d1fe-4e3f-90f1-51244ada657a', name: 'MisMascotas - Mascotas QA (19 casos CSV)',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    description: '19 casos MAS existentes. 2FA por Mailpit. Scripts compartidos con el ejecutor API/SQL/UI. No implementa funciones del producto. Ver docs/testing/mascotas_postman.md.' },
    auth: { type: 'noauth' }, variable: Object.entries(variables).map(([key,value])=>({key,value,type:'string'})),
    event: [{listen:'prerequest',script:{type:'text/javascript',exec:antes.split('\n')}},{listen:'test',script:{type:'text/javascript',exec:despues.split('\n')}}],
    item: ['REG','LIS','DET','EDI','ELI'].map(g=>({name:{REG:'01 Alta',LIS:'02 Listado',DET:'03 Detalle',EDI:'04 Edición',ELI:'05 Eliminación'}[g],
        item:casos.filter(c=>c.id.startsWith('MAS-'+g)).map(c=>({name:c.id+' — '+c.escenario,
            request:{ method:g==='REG'?'POST':g==='EDI'?'PUT':'GET', auth:{type:'noauth'},
                header:[{key:'Content-Type',value:'application/json'},{key:'Authorization',value:'Bearer {{mas_token}}'}],
                url:'{{mas_url}}', body:{mode:'raw',raw:'{{mas_cuerpo}}',options:{raw:{language:'json'}}},
                description:'**Datos:** '+c.datos+'\n\n**Acción:** '+c.accion+'\n\n**Esperado del CSV:** '+c.esperado+'\n\n**Prioridad:** '+c.prioridad+'\n\nLos escenarios de funciones ausentes se bloquean; los de UI se ejecutan desde el script QA.'},response:[]}))})) };
fs.mkdirSync(path.join(raiz,'postman'),{recursive:true});
fs.writeFileSync(path.join(raiz,'postman/MisMascotas Mascotas QA.postman_collection.json'),JSON.stringify(coleccion,null,2)+'\n');
fs.writeFileSync(path.join(raiz,'postman/MisMascotas Mascotas QA.postman_environment.json'),JSON.stringify({name:'MisMascotas Mascotas QA local',_postman_variable_scope:'environment',values:Object.entries(variables).map(([key,value])=>({key,value,enabled:true,type:key.endsWith('password')?'secret':'default'}))},null,2)+'\n');
console.log('Generados 19 casos Mascotas y entorno QA.');
