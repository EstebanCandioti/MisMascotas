const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const {crearInfraClinica,ahoraArgentina,hash}=require('./clinico-infra.cjs');
const {ejecutarCasoClinico}=require('./clinico-casos.cjs');
const raiz=path.resolve(__dirname,'../..');
const args=process.argv.slice(2),opcion=k=>{const i=args.indexOf(k);return i<0?undefined:args[i+1];};
const tanda=opcion('--tanda')||'completa';
if(!['registro','pesos','consulta','permisos','completa'].includes(tanda))throw new Error('Tanda válida: registro, pesos, consulta, permisos o completa.');
const catalogo=fs.readFileSync(path.join(raiz,'docs/testing/casos_prueba.csv'),'utf8').split(/\r?\n/).filter(l=>l.startsWith('ECL-')).map(l=>{
    if(l.includes('"'))throw new Error('Adaptar el lector si el catálogo agrega campos entrecomillados.');
    const [id,modulo,flujo,escenario,datos,accion,esperado,prioridad,tipo,fundamento]=l.split(';');return{id,escenario,datos,accion,esperado,prioridad,tipo,fundamento};
});
const idsEsperados=['REG:9','LIS:4','EDI:2','ELI:2'].flatMap(s=>{const[g,n]=s.split(':');return Array.from({length:Number(n)},(_,i)=>'ECL-'+g+'-'+String(i+1).padStart(3,'0'));});
if(catalogo.length!==17||new Set(catalogo.map(c=>c.id)).size!==17||idsEsperados.some(id=>!catalogo.some(c=>c.id===id)))throw new Error('El alcance debe conservar los 17 IDs ECL existentes.');
const pertenece=c=>tanda==='completa'||tanda==='registro'&&/^ECL-REG-00[1-4]$/.test(c.id)||tanda==='pesos'&&/^ECL-REG-00[5-9]$/.test(c.id)||tanda==='consulta'&&c.id.startsWith('ECL-LIS-')||tanda==='permisos'&&/^ECL-(EDI|ELI)-/.test(c.id);
const seleccion=catalogo.filter(pertenece).filter(c=>!opcion('--caso')||c.id===opcion('--caso'));
if(!seleccion.length)throw new Error('No hay casos en esa selección.');
const corrida='ECL-SELENIUM-'+tanda.toUpperCase()+'-'+new Date().toISOString().replace(/[-:.]/g,'')+'-'+crypto.randomBytes(3).toString('hex');
const carpeta=path.join(raiz,'docs/testing/evidencias',corrida);fs.mkdirSync(carpeta,{recursive:false});
const csv=path.join(raiz,'docs/testing/ejecuciones_pruebas.csv');
const original=fs.readFileSync(csv),encabezado='id_ejecucion;id_caso;fecha_hora;version_codigo;entorno;datos_utilizados;resultado;observaciones;evidencia;incidencia;ejecutor';
if(original.toString('utf8').replace(/^\uFEFF/,'').split(/\r?\n/)[0]!==encabezado)throw new Error('Historial con columnas no previstas.');
const respaldo=path.join(raiz,'.qa/respaldos',corrida);fs.mkdirSync(respaldo,{recursive:true});fs.writeFileSync(path.join(respaldo,'ejecuciones_pruebas.csv'),original,{flag:'wx'});
let ultimoHash=hash(original);
const escapar=v=>/[;"\r\n]/.test(String(v??''))?'"'+String(v??'').replaceAll('"','""')+'"':String(v??'');
const archivoDefectos=path.join(raiz,'docs/testing/defectos_historial_clinico.json');
const defectos=fs.existsSync(archivoDefectos)?JSON.parse(fs.readFileSync(archivoDefectos,'utf8')):{};
function consolidar(r,relativo){
    for(const x of r.comprobaciones.filter(x=>!x.cumple&&x.defecto)){
        const id='DEF-'+x.defecto;
        if(!defectos[id])defectos[id]={id,estado:'Abierto',titulo:x.nombre,casos:[],observaciones:[]};
        const d=defectos[id];if(!d.casos.includes(r.id_caso))d.casos.push(r.id_caso);
        d.observaciones.push({id_ejecucion:r.id_ejecucion,fecha:r.fecha_hora,esperado:x.esperado,observado:x.observado,evidencia:relativo+'/resultado.json',
            reproduccion:'node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso '+r.id_caso});
    }
    fs.writeFileSync(archivoDefectos,JSON.stringify(defectos,null,2)+'\n');
    let md='# Defectos consolidados de Historial Clínico\n\nSolo defectos observados. Funciones ausentes y permisos pendientes se registran como bloqueos, con su motivo en la evidencia y en el cierre. Interfaz probada con Selenium.\n\n';
    md+='| Defecto | Casos | Estado | Hallazgo |\n|---|---|---|---|\n';
    for(const d of Object.values(defectos))md+=`| [${d.id}](#${d.id.toLowerCase()}) | ${d.casos.join(', ')} | ${d.estado} | ${d.titulo} |\n`;
    for(const d of Object.values(defectos)){
        md+=`\n## ${d.id}\n\n**Estado:** ${d.estado}. **Casos:** ${d.casos.join(', ')}.\n\n${d.titulo}.\n\n`;
        for(const o of d.observaciones)md+=`- [${o.id_ejecucion}](${o.evidencia.replace('docs/testing/','')}). Esperado: ${JSON.stringify(o.esperado)}. Observado: ${JSON.stringify(o.observado)}. Reproducción: \`${o.reproduccion}\`.\n`;
        md+='\nLa evidencia contiene los datos ficticios, solicitud y respuesta saneadas; su ficha `../entorno.json` identifica las imágenes y las versiones del ejecutor. Un dato inválido no debe persistirse ni convertirse en error interno.\n';
    }
    fs.writeFileSync(path.join(raiz,'docs/testing/defectos_historial_clinico.md'),md);
}
function registrar(r,caso,ficha){
    r.id_ejecucion=corrida+'__'+caso.id+'__01';r.fecha_hora=ahoraArgentina();r.esperado_catalogo=caso.esperado;r.escenario=caso.escenario;r.fundamento=caso.fundamento;r.ficha='../entorno.json';
    const relativo='docs/testing/evidencias/'+corrida+'/'+caso.id+'__01';
    fs.writeFileSync(path.join(raiz,relativo,'resultado.json'),JSON.stringify(r,null,2)+'\n',{flag:'wx'});consolidar(r,relativo);
    const fallos=r.comprobaciones.filter(x=>!x.cumple),incidencias=[...new Set(fallos.map(x=>x.defecto).filter(Boolean))];
    const back=ficha.servicios?.['backend-web-testing']?.imagen_id,web=ficha.servicios?.['frontend-web-testing']?.imagen_id;
    const version='back img:'+(back?back.replace('sha256:','').slice(0,12):'no capturada')+' / web img:'+(web?web.replace('sha256:','').slice(0,12):'no capturada');
    const observacion=r.motivo||`${r.comprobaciones.length} verificaciones, ${fallos.length} fallidas. Selenium con HTTP/SQL complementarios.`;
    const fila=[r.id_ejecucion,caso.id,r.fecha_hora,version,'mismascotas-web-testing / '+r.capa,
        r.datos.usuarios.map(u=>u.email+' / '+u.idUsuario).join(' | ')||'Sin fixture: dependencia o decisión pendiente',r.resultado,
        observacion,relativo+'/resultado.json',incidencias.length?'docs/testing/defectos_historial_clinico.md':'','Codex / Selenium '+(ficha.selenium||'no disponible')+', HTTP y PostgreSQL'];
    const antes=fs.readFileSync(csv);if(hash(antes)!==ultimoHash)throw new Error('El historial cambió concurrentemente; conciliar evidencia antes de agregar filas.');
    const salto=antes.includes(Buffer.from('\r\n'))?'\r\n':'\n';fs.appendFileSync(csv,(antes.at(-1)===10?'':salto)+fila.map(escapar).join(';')+salto);ultimoHash=hash(fs.readFileSync(csv));
    console.log(caso.id+': '+r.resultado+' ('+r.comprobaciones.length+' verificaciones; '+fallos.length+' fallidas)');
}
async function main(){
    const infra=crearInfraClinica(raiz);let ficha,causaGlobal,huella;
    try{ficha=infra.ficha();huella=infra.huellaSeed();ficha.huella_seed_antes=huella;}catch(e){causaGlobal=e.message;ficha={error_preparacion:causaGlobal};}
    Object.assign(ficha,{id_corrida:corrida,tanda,ejecutor_sha256:hash(fs.readFileSync(__filename)),casos_sha256:hash(fs.readFileSync(path.join(__dirname,'clinico-casos.cjs'))),selenium_adapter_sha256:hash(fs.readFileSync(path.join(__dirname,'clinico-selenium.cjs')))});
    fs.writeFileSync(path.join(carpeta,'entorno.json'),JSON.stringify(ficha,null,2)+'\n');
    const resultados=[];
    for(const caso of seleccion){
        const destino=path.join(carpeta,caso.id+'__01');fs.mkdirSync(destino);
        const r=causaGlobal?{id_caso:caso.id,inicio:new Date().toISOString(),fin:new Date().toISOString(),resultado:'Bloqueada',motivo:causaGlobal,capa:'Preparación QA',datos:{usuarios:[],mascotas:[],eventos:[]},comprobaciones:[]}:
            await ejecutarCasoClinico(raiz,caso,infra,destino);
        registrar(r,caso,ficha);resultados.push({id:caso.id,resultado:r.resultado,motivo:r.motivo,evidencia:caso.id+'__01/resultado.json'});
        fs.writeFileSync(path.join(carpeta,'resumen.json'),JSON.stringify({id_corrida:corrida,tanda,resultados},null,2)+'\n');
    }
    if(huella){ficha.huella_seed_despues=infra.huellaSeed();ficha.seed_original_intacto=huella===ficha.huella_seed_despues;fs.writeFileSync(path.join(carpeta,'entorno.json'),JSON.stringify(ficha,null,2)+'\n');if(!ficha.seed_original_intacto)throw new Error('Cambió la huella del seed original.');}
    console.log('Tanda registrada: '+corrida);process.exitCode=resultados.some(r=>r.resultado!=='Aprobada')?1:0;
}
main().catch(e=>{fs.writeFileSync(path.join(carpeta,'error_ejecutor.json'),JSON.stringify({tipo:e.name,traza_sin_mensaje:String(e.stack||'').split('\n').slice(1),fecha:ahoraArgentina()},null,2)+'\n');console.error('Tanda interrumpida; se conserva evidencia en '+carpeta);process.exitCode=2;});
