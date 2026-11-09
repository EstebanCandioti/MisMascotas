const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const ahoraArgentina = () => new Date(Date.now() - 10800000).toISOString().replace('Z', '-03:00');
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
const fechaDias = dias => new Date(Date.now() - 10800000 - dias * 86400000).toISOString().slice(0, 10) + 'T12:00:00-03:00';
function crearInfraClinica(raiz) {
    const base = 'http://localhost:8080', mail = 'http://localhost:8025';
    function docker(argumentos, entrada) {
        const r = spawnSync('docker', argumentos, { cwd: raiz, input: entrada, encoding: 'utf8', windowsHide: true, timeout: 20000, maxBuffer: 10 * 1024 * 1024 });
        if (r.status !== 0) throw new Error('No se pudo consultar Docker/SQL QA.');
        return r.stdout.trim();
    }
    function sql(consulta) {
        return docker(['exec', '-i', 'mismascotas-web-testing-db-web-testing-1', 'sh', '-c',
            'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X -q -t -A -v ON_ERROR_STOP=1'], 'BEGIN READ ONLY;\n' + consulta + '\nCOMMIT;\n');
    }
    function ficha() {
        const servicios = {};
        for (const [s, puerto] of [['db-web-testing', null], ['backend-web-testing', '8080'], ['mailpit-web-testing', '8025'], ['frontend-web-testing', '3000']]) {
            const d = JSON.parse(docker(['inspect', 'mismascotas-web-testing-' + s + '-1']))[0];
            if (d.Config.Labels['com.docker.compose.project'] !== 'mismascotas-web-testing' || d.Config.Labels['com.docker.compose.service'] !== s || d.State.Health?.Status !== 'healthy') throw new Error('Servicio fuera de QA o no saludable: ' + s);
            if (puerto && !(d.NetworkSettings.Ports[puerto + '/tcp'] || []).some(p => p.HostPort === puerto)) throw new Error('El puerto local no corresponde al servicio QA: ' + s);
            if (s === 'db-web-testing' && !d.Mounts.some(m => m.Name === 'mismascotas_web_testing_db')) throw new Error('Volumen PostgreSQL QA no reconocido.');
            if (s === 'backend-web-testing') {
                const smtp = Object.fromEntries(d.Config.Env.map(v => { const i=v.indexOf('='); return [v.slice(0,i),v.slice(i+1)]; }));
                if (smtp.MAIL_HOST !== 'mailpit-web-testing' || smtp.MAIL_PORT !== '1025' || smtp.MAIL_USERNAME || smtp.MAIL_PASSWORD || smtp.MAIL_SMTP_AUTH !== 'false') throw new Error('El SMTP no está aislado en Mailpit QA.');
            }
            servicios[s] = { imagen_id: d.Image, contenedor_id: d.Id, salud: d.State.Health.Status, iniciado_en: d.State.StartedAt };
        }
        return { capturada_en: ahoraArgentina(), proyecto: 'mismascotas-web-testing', servicios,
            origen_build: 'No verificado. No se atribuye el commit del checkout a las imágenes.',
            catalogo_sha256: hash(fs.readFileSync(path.join(raiz, 'docs/testing/casos_prueba.csv'))),
            selenium: require(path.join(raiz, '.qa/selenium-runtime/node_modules/selenium-webdriver/package.json')).version,
            sql: 'Solo lectura. Fixtures por API; no se modifica el seed.', sin_playwright: true };
    }
    function huellaSeed() {
        return sql(`SELECT md5(json_build_object(
            'mascotas',(SELECT json_agg(m ORDER BY id_mascota) FROM public.mascota m WHERE id_mascota::text LIKE '20000000-0000-4000-8000-%'),
            'eventos',(SELECT json_agg(e ORDER BY id_evento) FROM public.evento_clinico e WHERE id_evento::text LIKE '30000000-0000-4000-8000-%'),
            'recordatorios',(SELECT json_agg(r ORDER BY id_recordatorio) FROM public.recordatorio r WHERE id_recordatorio::text LIKE '40000000-0000-4000-8000-%'))::text);`);
    }
    function observar(usuario) {
        if (!/^[a-f0-9-]{36}$/i.test(usuario)) throw new Error('UUID de observación inválido.');
        return JSON.parse(sql(`SELECT json_build_object(
            'mascotas',coalesce((SELECT json_agg(m ORDER BY m.id_mascota) FROM public.mascota m WHERE propietario_id='${usuario}'::uuid),'[]'::json),
            'eventos',coalesce((SELECT json_agg(e ORDER BY e.id_evento) FROM public.evento_clinico e JOIN public.mascota m ON m.id_mascota=e.mascota_id WHERE m.propietario_id='${usuario}'::uuid),'[]'::json));`));
    }
    async function enviar(url, metodo = 'GET', datos, token) {
        let r;
        try { r = await fetch(url, { method: metodo, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
            ...(datos === undefined ? {} : { body: JSON.stringify(datos) }), signal: AbortSignal.timeout(15000) }); }
        catch { throw new Error('La solicitud HTTP local no se completó en 15 segundos.'); }
        const texto=await r.text(); let body={};try{if(texto)body=JSON.parse(texto);}catch{body={respuesta_no_json:true};}
        return { status:r.status, body };
    }
    const http = (m,p,b,t) => enviar(base+p,m,b,t);
    async function actor(c) {
        const usuario = { nombre: 'QA Clínico ' + c.id, email: 'qa.ecl.' + crypto.randomUUID().replace(/-/g,'') + '@mismascotas.test', password: 'QaClinico2026!' };
        const consulta='/api/v1/search?query='+encodeURIComponent('to:'+usuario.email)+'&limit=500';
        const previos=await enviar(mail+consulta);
        if(previos.status!==200||!Array.isArray(previos.body.messages))throw new Error('Mailpit no responde como se esperaba.');
        const ids=previos.body.messages.map(m=>m.ID), inicio=Date.now();
        const registro=await http('POST','/auth/register',usuario);
        if(registro.status!==200||!registro.body.token)throw new Error('No se pudo registrar el actor QA.');
        let codigo;
        while(!codigo&&Date.now()-inicio<30000){
            const mensajes=await enviar(mail+consulta);
            for(const m of mensajes.body.messages||[]){
                if(ids.includes(m.ID))continue;
                const r=await enviar(mail+'/api/v1/message/'+encodeURIComponent(m.ID));
                if(r.status!==200||!(r.body.To||[]).some(t=>t.Address.toLowerCase()===usuario.email))continue;
                if(Date.parse(r.body.Date||m.Created)<inicio-2000)continue;
                codigo=String(r.body.Text||'').match(/Tu codigo de verificacion es:\s*(\d{6})(?!\d)/)?.[1];
                if(codigo)break;
            }
            if(!codigo)await new Promise(r=>setTimeout(r,400));
        }
        if(!codigo)throw new Error('No llegó código 2FA nuevo para el actor QA.');
        const sesion=await http('POST','/auth/verificar-codigo',{codigo},registro.body.token);
        if(sesion.status!==200||!sesion.body.accessToken||!sesion.body.refreshToken)throw new Error('No se completó 2FA del actor QA.');
        c.sesiones.push(sesion.body.refreshToken);
        const perfil=await http('GET','/usuario/me',undefined,sesion.body.accessToken);
        if(perfil.status!==200||perfil.body.email!==usuario.email)throw new Error('Identidad QA no verificada.');
        const dato={email:usuario.email,idUsuario:perfil.body.idUsuario,nombre:perfil.body.nombre};
        c.r.datos.usuarios.push(dato);
        return {...dato,sesion:sesion.body};
    }
    async function mascota(c, actorActual=c.actor) {
        const cuerpo={nombre:'QA '+c.id+' '+(c.pets.length+1),especie:'Perro',raza:null,fechaNacimiento:null,fechaAproximada:false,edadValor:null,edadUnidad:null,fotoPerfil:null,pesoActual:null,notas:'Fixture clínico ficticio'};
        const r=await http('POST','/mascotas',cuerpo,actorActual.sesion.accessToken);
        if(r.status!==201||!r.body.idMascota)throw new Error('No se pudo preparar la mascota QA.');
        c.pets.push({id:r.body.idMascota,actor:actorActual});c.r.datos.mascotas.push({idMascota:r.body.idMascota,nombre:r.body.nombre});
        return r.body;
    }
    async function limpiar(c) {
        for(const p of c.pets){try{const r=await http('DELETE','/mascotas/'+p.id,undefined,p.actor.sesion.accessToken);if(![204,404].includes(r.status))c.r.limites.push('Limpieza lógica pendiente: '+p.id);}catch{c.r.limites.push('Limpieza lógica no completada: '+p.id);}}
        for(const refreshToken of c.sesiones){try{await http('POST','/auth/logout',{refreshToken});}catch{c.r.limites.push('No se pudo cerrar una sesión QA.');}}
    }
    return { ficha,huellaSeed,observar,http,actor,mascota,limpiar };
}
module.exports={crearInfraClinica,ahoraArgentina,hash,fechaDias};
