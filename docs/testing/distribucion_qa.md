# Distribución local de MisMascotas para testers

## Entrega publicada

Versión: **qa-2026.10.25-01**, publicada el **25 de octubre de 2026**.
Cuenta Docker Hub: **estebancandioti**. Las tres imágenes son públicas.

- [Backend](https://hub.docker.com/r/estebancandioti/mismascotas-backend).
- [Web](https://hub.docker.com/r/estebancandioti/mismascotas-web).
- [PostgreSQL QA con seed](https://hub.docker.com/r/estebancandioti/mismascotas-db-qa).

El archivo para entregar es
`distribucion/qa-web/MisMascotas-QA-qa-2026.10.25-01.zip`.
Incluye Compose, configuración QA lista para usar, guía, cuentas ficticias,
identificación de versiones y sumas SHA-256. Los testers no necesitan el
repositorio ni iniciar sesión en Docker Hub para descargar las imágenes.

## Decisiones

- El Compose distribuido usa `image`, sin `build` ni montajes de código/SQL del
  repositorio. El ZIP basta para arrancar.
- Backend y Web se reetiquetaron desde las imágenes realmente usadas en las
  tandas: no se reconstruyó silenciosamente otra versión del código. El commit
  de origen de esos builds sigue sin verificarse. `version.json` conserva sus IDs.
- La imagen `mismascotas-db-qa` incorpora la estructura, catálogo y seed ficticio
  `qa-base-v1`. No contiene un respaldo del volumen usado para ejecutar pruebas.
- El inicializador habilita el contexto QA exigido por el SQL existente. Escribe
  una marca solo tras completar el seed; el control de salud exige esa marca y
  disponibilidad TCP. Así no se inicia el backend sobre una carga parcial.
- El seed se ejecuta únicamente sobre el volumen vacío de esta imagen. Recrear
  contenedores conserva datos y no restaura el seed.
- El proyecto de distribución es `mismascotas-qa`, con red y volumen propios.
  No comparte `mismascotas_web_testing_db` ni sustituye el Compose de desarrollo.
- Web/API/Mailpit se publican solo en localhost. Los puertos son 3000/8080/8025;
  la API está incorporada en la imagen Web con destino `http://localhost:8080`.
- Las claves entregadas son ficticias, públicas y exclusivas de QA local. El
  correo termina en Mailpit; no se entregan credenciales SMTP ni de Docker Hub.
- Los digests del Compose fijan las tres imágenes aunque una etiqueta cambie.
  Para una nueva entrega, usar otra versión: no sobrescribir esta etiqueta.

## Verificación de esta entrega

Se levantó un proyecto temporal independiente con volumen inicialmente vacío:

- Los cuatro servicios alcanzaron estado saludable.
- Se cargaron seis usuarios, 15 mascotas, 20 eventos clínicos y 15 recordatorios.
- Las seis cuentas completaron login y 2FA mediante el Mailpit temporal.
- Se contrastaron identidad, plan y cantidad de mascotas de cada cuenta.
- La página de login Web devolvió HTTP 200 y el perfil sin token devolvió 401.
- Una cuenta temporal creada durante la comprobación permaneció después de
  retirar y recrear los contenedores: el seed no se reaplicó.
- Se verificó sin autenticación la visibilidad pública y la etiqueta de las tres
  imágenes en Docker Hub.

Para no interferir con el entorno de tandas, la comprobación usó puertos
alternativos 13001/18081/18026. No se repitió la suite Selenium completa sobre
esa instancia ni se certificó otra PC. Antes de distribuir al resto del equipo,
un tester debe ensayar el ZIP en su propia PC siguiendo solamente la guía.

Estas son comprobaciones de distribución, no nuevas aprobaciones de casos de
la matriz. El historial de pruebas y el volumen QA original no se modificaron.

## Fuentes y mantenimiento

La receta de PostgreSQL está en `docker/qa-db/`, con un contexto de construcción
restringido por `Dockerfile.dockerignore`. Reutiliza exclusivamente
`DB/01-init.sql`, `DB/02-seed.sql` y `DB/qa/restablecer-seed.sql`.

El procedimiento del seed del entorno habitual permanece en `seed_qa.md`.
Sus scripts validan nombres de proyecto y servicios de ese entorno: no deben
aplicarse sin adaptación al paquete `mismascotas-qa`. La restauración del paquete
se definirá como operación explícita; iniciar o descargar imágenes no borra datos.

El paquete actual está preparado para Linux amd64, usado desde Docker Desktop
en una PC Intel/AMD de 64 bits. No se verificó Mac/ARM.

Documentación de referencia:

- [Inicio ordenado y controles de salud de Compose](https://docs.docker.com/compose/how-tos/startup-order/).
- [Inicialización de la imagen oficial de PostgreSQL](https://hub.docker.com/_/postgres).
- [Repositorios y visibilidad en Docker Hub](https://docs.docker.com/docker-hub/repos/create/).
