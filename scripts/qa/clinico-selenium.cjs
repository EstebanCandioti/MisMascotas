const fs=require('node:fs');
const path=require('node:path');
async function crearSeleniumClinico(raiz,c,carpeta) {
    const paquete=path.join(raiz,'.qa/selenium-runtime/node_modules/selenium-webdriver');
    const {Builder,By,until}=require(paquete), edge=require(path.join(paquete,'edge'));
    const driver=await new Builder().forBrowser('MicrosoftEdge').setEdgeOptions(new edge.Options().addArguments('--headless=new','--window-size=1280,1100','--disable-gpu','--no-first-run','--disable-background-networking')).build();
    await driver.manage().setTimeouts({implicit:0,pageLoad:30000,script:15000});
    await driver.sendDevToolsCommand('Emulation.setTimezoneOverride',{timezoneId:'America/Argentina/Buenos_Aires'});
    const caps=await driver.getCapabilities();c.r.navegador={herramienta:'Selenium WebDriver',version_selenium:require(path.join(paquete,'package.json')).version,nombre:caps.get('browserName'),version:caps.get('browserVersion')};
    const esperar=(fn,motivo)=>driver.wait(fn,15000,motivo,200);
    const elemento=selector=>driver.wait(until.elementLocated(By.css(selector)),15000);
    async function pulsar(elementoActual){await driver.executeScript('arguments[0].scrollIntoView({block:"center"})',elementoActual);await driver.wait(until.elementIsVisible(elementoActual),10000);await driver.wait(until.elementIsEnabled(elementoActual),10000);await elementoActual.click();}
    async function captura(nombre){fs.writeFileSync(path.join(carpeta,nombre),Buffer.from(await driver.takeScreenshot(),'base64'));c.r.capturas.push(nombre);}
    async function abrir(actor=c.actor,pet=c.pet){
        await driver.get('http://localhost:3000/login');
        await driver.executeScript('sessionStorage.clear();sessionStorage.setItem("mis-mascotas.auth.session",JSON.stringify(arguments[0]));',actor.sesion);
        await driver.get('http://localhost:3000/mascotas/'+pet.idMascota);
        await elemento('#historial-clinico');
        await esperar(async()=>!String(await driver.findElement(By.css('#historial-clinico')).getText()).includes('Cargando historial'), 'El historial no terminó de cargar.');
    }
    async function tarjetas(){return driver.executeScript(`return Array.from(document.querySelectorAll('#historial-clinico .clinical-event-list > *')).map(e=>({tipo:e.querySelector('h3')?.textContent||'',fecha:e.querySelector('time')?.getAttribute('datetime')||'',resumen:e.querySelector('strong')?.textContent||'',texto:e.innerText}));`);}
    async function registrar(datos,nombreCaptura){
        const antes=(await tarjetas()).length;
        const boton=await driver.findElement(By.xpath('//section[@id="historial-clinico"]//button[contains(normalize-space(.),"Registrar evento")]'));
        await pulsar(boton);await elemento('[role="dialog"] form');
        await pulsar(await driver.findElement(By.css('select[name="tipo"] option[value="'+datos.tipo+'"]')));
        const instante=new Date(datos.fecha), local=new Date(instante.getTime()-10800000).toISOString();
        // Controles nativos de fecha/hora: valor válido más eventos DOM, sin enviar la API ni sustituir el clic final.
        await driver.executeScript(`for(const [nombre,valor] of Object.entries(arguments[0])){const e=document.querySelector('[role="dialog"] [name="'+nombre+'"]');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,valor);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}`,{fecha:local.slice(0,10),hora:local.slice(11,16)});
        for(const campo of ['nombre','dosis','motivo','diagnostico','observaciones','valorPeso'])if(datos[campo]!==undefined&&datos[campo]!==null){const e=await elemento('[role="dialog"] [name="'+campo+'"]');await e.clear();await e.sendKeys(String(datos[campo]));}
        await captura(nombreCaptura+'_formulario.png');
        c.r.acciones_selenium.push({accion:'Guardar formulario clínico mediante clic WebDriver',datos,fecha:new Date().toISOString()});
        await pulsar(await driver.findElement(By.css('[role="dialog"] button[type="submit"]')));
        await esperar(async()=>!(await driver.findElements(By.css('[role="dialog"]'))).length,'El formulario clínico no se cerró al guardar.');
        await esperar(async()=>(await tarjetas()).length===antes+1,'La nueva tarjeta no apareció en el historial.');
        await captura(nombreCaptura+'_historial.png');
    }
    async function peticion(metodo,ruta,cuerpo,token=c.actor.sesion.accessToken){
        const r=await driver.executeAsyncScript(`const [metodo,ruta,cuerpo,token,fin]=arguments;fetch('http://localhost:8080'+ruta,{method:metodo,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(cuerpo===null?{}:{body:JSON.stringify(cuerpo)})}).then(async r=>{let b={};try{b=await r.json();}catch{}fin({status:r.status,body:b});}).catch(()=>fin({error_red:true}));`,metodo,ruta,cuerpo===undefined?null:cuerpo,token||'');
        if(r.error_red)throw new Error('Selenium no pudo completar la solicitud HTTP local.');
        c.r.solicitudes.push({origen:'Selenium executeAsyncScript / API directa',metodo,ruta,cuerpo:cuerpo??null,http:r.status,respuesta:r.body});
        return r;
    }
    async function filtro(texto){const e=await driver.findElement(By.xpath('//div[contains(@class,"clinical-filters")]//button[normalize-space(.)="'+texto+'"]'));await pulsar(e);await esperar(async()=>!String(await driver.findElement(By.css('#historial-clinico')).getText()).includes('Consultando filtro'), 'No terminó el filtro por tipo.');}
    async function obligatorios(){
        await pulsar(await driver.findElement(By.xpath('//section[@id="historial-clinico"]//button[contains(normalize-space(.),"Registrar evento")]')));
        await elemento('[role="dialog"] form');await pulsar(await driver.findElement(By.css('[role="dialog"] button[type="submit"]')));
        const invalidos=await driver.executeScript(`return Array.from(document.querySelectorAll('[role="dialog"] form :invalid')).filter(e=>e.name).map(e=>e.name)`);
        await captura('campos_obligatorios.png');
        await pulsar(await driver.findElement(By.xpath('//section[@role="dialog"]//button[normalize-space(.)="Cancelar"]')));
        return invalidos;
    }
    return {driver,By,esperar,elemento,pulsar,captura,abrir,tarjetas,registrar,peticion,filtro,obligatorios,cerrar:()=>driver.quit()};
}
module.exports={crearSeleniumClinico};
