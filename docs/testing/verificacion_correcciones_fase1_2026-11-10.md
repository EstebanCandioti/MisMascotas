# Verificación de correcciones de Fase 1 — 2026-11-10

## Alcance y versión

- Rama: `testing-integracion-web`.
- Base verificada: `e313c3c`.
- Entorno disponible para esta ejecución: Java 21 y Maven en la VM Ubuntu.
- Entorno no disponible: Docker/Compose Web; la VM no expone el comando `docker`, `docker.exe` ni un socket de Docker Desktop.

## Ejecución dirigida de backend

Comando:

```bash
bash mvnw -Dtest=RecordatorioServiceTests,RegistroRequestDTOTests,AuthControllerTests,EventoClinicoControllerTests test
```

Resultado: **aprobada**, 13 pruebas ejecutadas, 0 fallos y 0 errores.

Variantes cubiertas:

- recordatorio único `ACTIVO` confirmado: pasa a `COMPLETADO` y conserva autor y fecha;
- recordatorio único `COMPLETADO` desconfirmado: vuelve a `ACTIVO` y limpia autor y fecha;
- recordatorio único `INACTIVO`: confirmar o desconfirmar conserva `INACTIVO`;
- serie recurrente `ACTIVO`: confirmar no cambia la serie a `COMPLETADO`;
- email duplicado normalizado con mayúsculas y espacios: respuesta HTTP `409`;
- email que continúa inválido después de normalizar: respuesta HTTP `400`;
- email nuevo válido: continúa al desafío 2FA;
- fecha clínica no interpretable: respuesta HTTP `400` con `ERROR_VALIDACION`;
- fecha clínica inexistente: respuesta HTTP `400` con `ERROR_VALIDACION`;
- fecha clínica válida con offset `-03:00`: conserva el instante UTC equivalente.

También se ejecutaron todas las pruebas que no requieren el contexto con PostgreSQL:

```bash
bash mvnw -Dtest='!BackendApplicationTests' test
```

Resultado: **aprobada**, 18 pruebas ejecutadas, 0 fallos y 0 errores.

## Ejecución completa de Maven

Comando:

```bash
bash mvnw test
```

Resultado: **bloqueada por configuración del entorno**, 19 pruebas detectadas, 18 sin fallos y un error en `BackendApplicationTests.contextLoads`. El contexto intenta usar literalmente `${DB_URL}` porque la corrida se ejecutó fuera del Compose y sin variables de base de datos. Las pruebas dirigidas de esta corrección finalizaron correctamente dentro de la misma ejecución.

## Validación pendiente en Compose Web

No se registran `AUT-REG-017` ni `ECL-REG-002` como aprobados funcionalmente porque no fue posible ejecutar PostgreSQL, Mailpit, Newman ni Selenium desde esta VM. Para completar la aprobación deben ejecutarse en el host con Docker Desktop:

```powershell
docker compose --env-file .env.web-testing -f docker-compose.web-testing.yml up -d --build --wait
node scripts/qa/Ejecutar-AuthPostman.cjs
node scripts/qa/Ejecutar-HistorialClinicoQA.cjs --tanda completa --caso ECL-REG-002
```

La corrida clínica completa es necesaria porque `ECL-REG-002` contiene variantes adicionales fuera de las dos fechas corregidas. Cada ejecutor conserva sus resultados en una carpeta nueva de `docs/testing/evidencias/` y agrega una fila independiente a `docs/testing/ejecuciones_pruebas.csv`.
