# Cuentas ficticias de prueba

Exclusivas de este paquete local, con seed `qa-base-v1`.

| Cuenta | Email | Contraseña | Plan | Mascotas iniciales |
|---|---|---|---|---:|
| Free vacío | qa.free.vacio@mismascotas.test | QaVacio2026! | Free | 0 |
| Free habitual | qa.free.habitual@mismascotas.test | QaHabitual2026! | Free | 2 |
| Free al límite | qa.free.limite@mismascotas.test | QaLimite2026! | Free | 5 |
| Premium | qa.premium@mismascotas.test | QaPremium2026! | Premium | 6 |
| Propietario A | qa.propietario.a@mismascotas.test | QaPropietarioA2026! | Free | 1 |
| Propietario B | qa.propietario.b@mismascotas.test | QaPropietarioB2026! | Free | 1 |

El segundo factor se consulta en http://localhost:8025, buscando el email de la
cuenta. Cada intento genera su propio código; usar el mensaje más reciente.

Los datos y contraseñas son ficticios y públicos. Las cantidades describen el
estado inicial: las acciones de cada tester pueden modificarlas.
