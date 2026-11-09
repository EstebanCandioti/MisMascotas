const {fechaDias}=require('./clinico-infra.cjs');
const {crearSeleniumClinico}=require('./clinico-selenium.cjs');
const bloqueos={
    'ECL-REG-005':'No hay contrato específico de PUT de carga inicial que cree una medición histórica. El PUT del perfil básico no se sustituye por ese flujo; POST PESO se prueba en otros casos.',
    'ECL-LIS-004':'Falta el acceso compartido aceptado de Familia/Cuidador en la aplicación.',
    'ECL-EDI-001':'El catálogo mantiene Pendiente de permisos para corrección permitida. Falta cerrar autorización y trazabilidad antes de ejecutar la mutación.',
    'ECL-ELI-001':'El catálogo mantiene Pendiente de permisos para eliminación permitida y su semántica. No se ejecuta el borrado mientras siga pendiente.'
};
const etiqueta={VACUNA:'Vacuna',MEDICACION:'Medicación',CONSULTA_VETERINARIA:'Consulta veterinaria',PESO:'Control de peso'};
function datosEvento(tipo,dias=2,cambios={}){
    return {tipo,fecha:fechaDias(dias),...(tipo==='VACUNA'?{nombre:'Vacuna ficticia QA',observaciones:'Sin uso clínico.'}:tipo==='MEDICACION'?{nombre:'Tratamiento ficticio QA',dosis:'Dosis ficticia QA',observaciones:'Sin indicación veterinaria.'}:tipo==='CONSULTA_VETERINARIA'?{motivo:'Consulta ficticia QA',diagnostico:'Diagnóstico ficticio QA',observaciones:'Registro exclusivo de pruebas.'}:{valorPeso:10}),...cambios};
}
function verificar(c,nombre,cumple,esperado,observado,defecto=''){
    c.r.comprobaciones.push({nombre,cumple:Boolean(cumple),esperado,observado,defecto:cumple?'':defecto});
    return Boolean(cumple);
}
const igual=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
async function ejecutarCasoClinico(raiz,caso,infra,carpeta){
    const c={id:caso.id,pets:[],sesiones:[],r:{id_caso:caso.id,inicio:new Date().toISOString(),resultado:'No ejecutada',motivo:'',capa:'Selenium Web + API + SQL',
        datos:{usuarios:[],mascotas:[],eventos:[]},comprobaciones:[],solicitudes:[],acciones_selenium:[],capturas:[],sql:{},limites:[]}};
    let ui;
    const bloquear=motivo=>{const e=new Error(motivo);e.bloqueo=true;throw e;};
    const check=(...args)=>verificar(c,...args);
    const observar=etapa=>{const d=infra.observar(c.actor.idUsuario);c.r.sql[etapa]=d;return d;};
    const lista=async()=>{const r=await infra.http('GET','/mascotas/'+c.pet.idMascota+'/eventos-clinicos',undefined,c.actor.sesion.accessToken);if(r.status!==200||!Array.isArray(r.body))bloquear('No se pudo leer el historial de la mascota QA.');return r.body;};
    const pet=async()=>{const r=await infra.http('GET','/mascotas/'+c.pet.idMascota,undefined,c.actor.sesion.accessToken);if(r.status!==200)bloquear('No se pudo leer el peso actual de la mascota.');return r.body;};
    async function fixture(datos,mascota=c.pet){
        const r=await infra.http('POST','/mascotas/'+mascota.idMascota+'/eventos-clinicos',datos,c.actor.sesion.accessToken);
        if(r.status!==201||!r.body.idEvento)bloquear('No se pudo preparar el evento clínico ficticio.');
        c.r.datos.eventos.push(r.body.idEvento);c.r.solicitudes.push({fase:'preparación',metodo:'POST',ruta:'/mascotas/'+mascota.idMascota+'/eventos-clinicos',cuerpo:datos,http:r.status,respuesta:r.body});
        return r.body;
    }
    async function guardarUI(datos,referencia){
        const antes=await lista();
        await ui.registrar(datos,referencia);
        const despues=await lista();const nuevos=despues.filter(e=>!antes.some(a=>a.idEvento===e.idEvento));
        check(referencia+': una sola fila clínica nueva',nuevos.length===1,1,nuevos.length,'ECL-ALTA-DUPLICADA');
        if(nuevos.length!==1)bloquear('No se identificó una única alta de interfaz.');
        const e=nuevos[0];c.r.datos.eventos.push(e.idEvento);
        const conserva=Object.entries(datos).every(([k,v])=>k==='fecha'?Date.parse(e.fecha)===Date.parse(v):e[k]===v);
        check(referencia+': tipo, campos, fecha y autoría conservados',conserva&&e.mascotaId===c.pet.idMascota&&e.registradoPorId===c.actor.idUsuario&&e.registradoPorNombre===c.actor.nombre,
            {...datos,autor:c.actor.idUsuario},e,'ECL-CAMPOS-AUTORIA');
        const sql=infra.observar(c.actor.idUsuario);const fila=sql.eventos.find(x=>x.id_evento===e.idEvento);
        check(referencia+': persistencia SQL e identidad de mascota/autor',Boolean(fila)&&fila.mascota_id===c.pet.idMascota&&fila.registrado_por_id===c.actor.idUsuario&&Date.parse(fila.fecha)===Date.parse(datos.fecha),
            {mascota:c.pet.idMascota,autor:c.actor.idUsuario,fecha:datos.fecha},fila,'ECL-PERSISTENCIA');
        return e;
    }
    async function invalida(nombre,datos,defecto,campos=[]){
        const antes=observar('antes_'+nombre);
        const r=await ui.peticion('POST','/mascotas/'+c.pet.idMascota+'/eventos-clinicos',datos);
        check(nombre+': rechazo de validación controlado',[400,422].includes(r.status),'HTTP 400 o 422',{http:r.status,respuesta:r.body},defecto);
        if(campos.length&&r.status===400)for(const campo of campos)check(nombre+': error identifica '+campo,String(r.body.mensaje).includes(campo+':'),campo,r.body.mensaje,defecto);
        const despues=observar('despues_'+nombre);
        check(nombre+': sin persistencia ni alteración del peso',igual(antes,despues),'Instantáneas SQL idénticas',{eventos_antes:antes.eventos.length,eventos_despues:despues.eventos.length},defecto);
    }
    try{
        if(bloqueos[c.id]){c.r.capa='Contrato pendiente; sin ejecución funcional';bloquear(bloqueos[c.id]);}
        c.actor=await infra.actor(c);c.pet=await infra.mascota(c);
        if(['ECL-REG-003','ECL-EDI-002','ECL-ELI-002'].includes(c.id))c.ajeno=await infra.actor(c);
        if(['ECL-REG-006','ECL-REG-008','ECL-REG-009'].includes(c.id))c.primero=await fixture(datosEvento('PESO',c.id==='ECL-REG-009'?1:8,{valorPeso:10}));
        if(['ECL-EDI-002','ECL-ELI-002'].includes(c.id))c.original=await fixture(datosEvento('VACUNA'));
        if(c.id.startsWith('ECL-LIS')){
            c.preparados=[];
            for(const [tipo,dias,peso] of [['PESO',12,19],['VACUNA',9],['MEDICACION',6],['CONSULTA_VETERINARIA',4],['PESO',1,18.5]])c.preparados.push(await fixture(datosEvento(tipo,dias,peso===undefined?{}:{valorPeso:peso})));
            if(c.id==='ECL-LIS-001'){c.otraPet=await infra.mascota(c);c.otroEvento=await fixture(datosEvento('VACUNA',3,{nombre:'Otro historial no debe filtrarse'}),c.otraPet);}
        }
        observar('antes');
        ui=await crearSeleniumClinico(raiz,c,carpeta);
        await ui.abrir();await ui.captura('antes.png');
        if(c.id==='ECL-REG-001'){
            await guardarUI(datosEvento('CONSULTA_VETERINARIA'),'consulta_valida');
        }else if(c.id==='ECL-REG-004'){
            for(const tipo of ['CONSULTA_VETERINARIA','VACUNA','MEDICACION','PESO'])await guardarUI(datosEvento(tipo),'tipo_'+tipo.toLowerCase());
            const eventos=await lista();check('Los cuatro tipos están presentes una sola vez',eventos.length===4&&new Set(eventos.map(e=>e.tipo)).size===4,['CONSULTA_VETERINARIA','VACUNA','MEDICACION','PESO'],eventos.map(e=>e.tipo),'ECL-TIPOS');
        }else if(c.id==='ECL-REG-002'){
            const requeridos=await ui.obligatorios();check('Selenium: campos vacíos impiden enviar formulario',requeridos.includes('fecha')&&requeridos.includes('hora')&&requeridos.includes('nombre'),['fecha','hora','nombre'],requeridos,'ECL-UI-OBLIGATORIOS');
            const vac=datosEvento('VACUNA');
            for(const [nombre,datos,campos] of [
                ['tipo_vacio',{...vac,tipo:''},['tipo']],['tipo_invalido',{...vac,tipo:'OTRO_INVENTADO'},['tipo']],
                ['fecha_ausente',{...vac,fecha:null},['fecha']],['vacuna_sin_nombre',{...vac,nombre:''},['nombre']],
                ['medicacion_sin_nombre',{...datosEvento('MEDICACION'),nombre:''},['nombre']],['medicacion_sin_dosis',{...datosEvento('MEDICACION'),dosis:''},['dosis']],
                ['consulta_sin_motivo',{...datosEvento('CONSULTA_VETERINARIA'),motivo:''},['motivo']],
                ['fecha_futura',{...vac,fecha:fechaDias(-2)},[]],['fecha_no_parseable',{...vac,fecha:'no-es-un-instante'},[]],
                ['fecha_inexistente',{...vac,fecha:'2026-02-30T12:00:00Z'},[]],['nombre_256',{...vac,nombre:'N'.repeat(256)},[]],
                ['dosis_101',{...datosEvento('MEDICACION'),dosis:'D'.repeat(101)},[]],['observaciones_256',{...vac,observaciones:'O'.repeat(256)},[]]
            ])await invalida(nombre,datos,'ECL-VAL-'+nombre.toUpperCase(),campos);
        }else if(c.id==='ECL-REG-007'){
            for(const [nombre,valor]of[['nulo',null],['cero',0],['negativo',-1],['menor_minimo',0.009],['mayor_maximo',300.01],['no_numerico','no-es-numero']])await invalida('peso_'+nombre,datosEvento('PESO',2,{valorPeso:valor}),'ECL-PESO-'+nombre.toUpperCase());
            // Las fronteras válidas se prueban también mediante altas reales de interfaz.
            for(const valor of [0.01,300])await guardarUI(datosEvento('PESO',valor===0.01?3:1,{valorPeso:valor}),'frontera_'+String(valor).replace('.','_'));
        }else if(['ECL-REG-006','ECL-REG-008','ECL-REG-009'].includes(c.id)){
            const atras=c.id==='ECL-REG-009',nuevo=datosEvento('PESO',atras?15:2,{valorPeso:atras?99:12.5});
            const creado=await guardarUI(nuevo,'medicion_peso');
            const eventos=await lista(),pesos=eventos.filter(e=>e.tipo==='PESO');
            check('Las dos mediciones permanecen con IDs y valores propios',pesos.length===2&&pesos.some(e=>e.idEvento===c.primero.idEvento&&e.valorPeso===10)&&pesos.some(e=>e.idEvento===creado.idEvento&&e.valorPeso===nuevo.valorPeso),
                [{id:c.primero.idEvento,valor:10},{id:creado.idEvento,valor:nuevo.valorPeso}],pesos,'ECL-PESO-HISTORIA');
            check('El historial ordena por fecha de medición descendente',pesos.every((e,i)=>!i||Date.parse(pesos[i-1].fecha)>=Date.parse(e.fecha)),'Fecha descendente',pesos.map(e=>e.fecha),'ECL-ORDEN');
            const actual=await pet(),esperado=atras?10:12.5;
            check('Peso actual calculado por fecha y no por orden de carga',actual.pesoActual===esperado,esperado,actual.pesoActual,'ECL-PESO-ACTUAL');
            await ui.abrir();const texto=await ui.driver.findElement(ui.By.css('.metric-card')).getText();
            check('Selenium: la ficha muestra el peso vigente',texto.includes(String(esperado)+' kg'),String(esperado)+' kg',texto,'ECL-PESO-UI');
            await ui.captura('peso_actual.png');
        }else if(c.id==='ECL-REG-003'||c.id==='ECL-EDI-002'||c.id==='ECL-ELI-002'){
            const metodo=c.id==='ECL-REG-003'?'POST':c.id==='ECL-EDI-002'?'PUT':'DELETE';
            const ruta=metodo==='POST'?'/mascotas/'+c.pet.idMascota+'/eventos-clinicos':'/eventos-clinicos/'+c.original.idEvento;
            const datos=metodo==='DELETE'?undefined:datosEvento('VACUNA',2,{nombre:'Intento ajeno no permitido'});
            for(const [nombre,token,esperado]of[['tercero',c.ajeno.sesion.accessToken,403],['sin_token','',401]]){
                const r=await ui.peticion(metodo,ruta,datos,token);
                check('Selenium/API: '+nombre+' no puede '+metodo,r.status===esperado&&!r.body.idEvento&&!r.body.mascotaId&&!r.body.nombre,esperado,{http:r.status,respuesta:r.body},'ECL-AUTORIZACION-'+metodo);
            }
            const despues=observar('despues_rechazos');check('SQL: evento, mascota y peso intactos tras rechazo',igual(c.r.sql.antes,despues),'Estado anterior conservado','Comparación SQL','ECL-RECHAZO-MUTA');
            if(c.original){const eventos=await lista();check('El evento original sigue accesible y sin cambios',eventos.some(e=>e.idEvento===c.original.idEvento&&e.nombre===c.original.nombre),c.original,eventos,'ECL-RECHAZO-MUTA');}
        }else if(c.id==='ECL-LIS-001'){
            const eventos=await lista(),tarjetas=await ui.tarjetas();
            check('Historial completo sin omisiones ni cruce entre mascotas',eventos.length===5&&c.preparados.every(e=>eventos.some(x=>x.idEvento===e.idEvento))&&!eventos.some(e=>e.idEvento===c.otroEvento.idEvento),c.preparados.map(e=>e.idEvento),eventos.map(e=>e.idEvento),'ECL-HISTORIAL');
            check('Selenium: cinco tarjetas con tipo, valor y fecha correctos',tarjetas.length===5&&tarjetas.every((t,i)=>t.tipo===etiqueta[eventos[i].tipo]&&Date.parse(t.fecha)===Date.parse(eventos[i].fecha)&&t.texto.includes(c.actor.nombre)),eventos.map(e=>({tipo:etiqueta[e.tipo],fecha:e.fecha})),tarjetas,'ECL-HISTORIAL-UI');
            check('Historial ordenado por fecha descendente',eventos.every((e,i)=>!i||Date.parse(eventos[i-1].fecha)>=Date.parse(e.fecha)),'Fecha descendente',eventos.map(e=>e.fecha),'ECL-ORDEN');
        }else if(c.id==='ECL-LIS-002'){
            for(const tipo of ['VACUNA','MEDICACION','CONSULTA_VETERINARIA','PESO']){
                await ui.filtro(etiqueta[tipo]);const esperados=c.preparados.filter(e=>e.tipo===tipo),r=await ui.peticion('GET','/mascotas/'+c.pet.idMascota+'/eventos-clinicos?tipo='+tipo);
                await ui.esperar(async()=>(await ui.tarjetas()).length===esperados.length,'El filtro no mostró la cantidad esperada.');
                const tarjetas=await ui.tarjetas();check('Filtro '+tipo+': solo coincidencias',r.status===200&&r.body.length===esperados.length&&r.body.every(e=>e.tipo===tipo)&&tarjetas.every(t=>t.tipo===etiqueta[tipo]),esperados.length,{api:r.body,tarjetas},'ECL-FILTRO-TIPO');
                await ui.captura('filtro_'+tipo.toLowerCase()+'.png');
            }
            await ui.filtro('Todos');await ui.esperar(async()=>(await ui.tarjetas()).length===5,'Limpiar no restauró todo el historial.');
            check('Todos limpia el filtro y recupera los cinco eventos',(await ui.tarjetas()).length===5,5,(await ui.tarjetas()).length,'ECL-FILTRO-LIMPIAR');
            const controles=await ui.driver.findElements(ui.By.css('#historial-clinico input[type="date"],#historial-clinico input[type="datetime-local"]'));
            c.r.funcion_faltante={filtro_por_fecha:controles.length===0};await ui.captura('filtro_fecha_ausente.png');
            bloquear('Se probaron tipo y limpiar, pero falta el filtro por fecha del catálogo. Caso parcial que permanece Bloqueada.');
        }else if(c.id==='ECL-LIS-003'){
            const pesos=(await lista()).filter(e=>e.tipo==='PESO');check('Precondición: dos mediciones reales con fechas distintas',pesos.length===2&&pesos[0].fecha!==pesos[1].fecha,2,pesos,'');
            const graficos=await ui.driver.findElements(ui.By.css('#historial-clinico canvas,#historial-clinico .recharts-wrapper,#historial-clinico [aria-label*="evolución"],#historial-clinico [aria-label*="Evolución"],.weight-chart,.peso-chart'));
            c.r.funcion_faltante={grafico:graficos.length===0};await ui.captura('grafico_ausente.png');
            bloquear('No hay gráfico de evolución disponible para comprobar ejes y valores. El listado de pesos no sustituye al gráfico.');
        }
        observar('despues');
        await ui.captura('despues.png');
        c.r.resultado=c.r.comprobaciones.some(x=>!x.cumple)?'Fallida':'Aprobada';
    }catch(e){
        c.r.resultado=c.r.comprobaciones.some(x=>!x.cumple)?'Fallida':'Bloqueada';
        c.r.motivo=e.bloqueo?e.message:'No se completó la preparación o la verificación Selenium. Ver captura y diagnóstico saneado.';
        if(!e.bloqueo)c.r.error_ejecutor={tipo:e.name,traza_sin_mensaje:String(e.stack||'').split('\n').slice(1)};
        if(ui)await ui.captura('estado_al_interrumpirse.png').catch(()=>{});
        if(c.actor){try{observar('al_interrumpirse');}catch{c.r.limites.push('No se pudo capturar SQL al interrumpirse.');}}
    }finally{
        if(ui)await ui.cerrar().catch(()=>{});
        await infra.limpiar(c);c.r.fin=new Date().toISOString();
    }
    return c.r;
}
module.exports={ejecutarCasoClinico,bloqueos,datosEvento};
