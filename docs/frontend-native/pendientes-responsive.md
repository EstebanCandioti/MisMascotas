# Deuda técnica y visual de `frontend-native`

## Propósito y alcance

Este documento registra hallazgos reales de la rama `testing-integracion-native` para que el frontend Native definitivo pueda resolverlos de forma consistente. La rama actual se utiliza para integración rápida: no se espera que absorba toda la deuda estructural mientras no bloquee las pruebas.

El foco principal es responsive, adaptabilidad, tipografía y composición. Al final se incluyen otras deudas transversales observadas durante la integración.

No es un plan de implementación inmediato ni implica que todas las mejoras deban aplicarse en `testing-integracion-native`.

## Convención de estados

| Estado | Significado |
|---|---|
| **Corregido provisionalmente en `testing-integracion-native`** | Existe una mitigación suficiente para continuar las pruebas, pero debe revisarse antes de incorporarla al frontend definitivo. |
| **Pendiente para el frontend definitivo** | Requiere una solución común o una refactorización que no corresponde forzar en la rama de integración. |
| **No prioritario** | Es mejorable, pero no se observó que bloquee pruebas ni que justifique una intervención inmediata. |

## Estado actual

El responsive actual es parcial. La mayoría de las pantallas utiliza `flex`, padding lateral y `ScrollView`, por lo que funciona razonablemente con tamaños de pantalla y escalado de fuente normales.

Sin embargo, no existe una estrategia adaptativa centralizada:

- no se usan `Dimensions` ni `useWindowDimensions`;
- no existen breakpoints ni reglas compartidas de reflow;
- no existe una política tipográfica común para escalado, `lineHeight` o truncamiento;
- no existe un contenedor de pantalla reutilizable que unifique ancho, safe areas, scroll y teclado;
- se repiten controles de altura fija y filas horizontales con texto dinámico;
- la aplicación declara soporte para tablets, pero no limita el ancho del contenido ni modifica la composición.

Por estas razones, una pantalla puede verse correcta en un dispositivo y recortar, superponer u ocultar contenido en otro. El riesgo aumenta con pantallas angostas, escalado de fuente del sistema, textos provenientes del backend y teclado visible.

### Evidencia resumida

- Se revisaron 19 pantallas funcionales.
- 17 usan `ScrollView`; login y verificación no lo usaban en el estado relevado.
- No se encontró uso de `Dimensions`, `useWindowDimensions` o `PixelRatio` para layout.
- No se encontró una política explícita de `allowFontScaling`, `maxFontSizeMultiplier` o `adjustsFontSizeToFit`.
- `numberOfLines` aparecía solamente en `app/albums/album-detalle.tsx`, donde el truncamiento era intencional.
- `flexShrink` aparecía de manera útil en `components/screen-header.tsx`, pero no formaba parte de un criterio general para las filas del proyecto.

> Línea base: este documento describe el estado observado durante el relevamiento original. No incorpora correcciones mínimas realizadas en paralelo posteriormente para habilitar pruebas.

## Problemas estructurales

### 1. Alturas fijas con texto escalable

**Pantalla/componente:** inputs, botones y encabezados de autenticación, formularios, recordatorios y pantallas internas. Ejemplos: `app/auth/login.tsx`, `app/auth/verificar-codigo.tsx`, `app/health/recordatorios.tsx`, `app/health/registrar-evento.tsx`.

**Problema:** se utilizan con frecuencia alturas fijas de 50 a 56 px para controles y 72 px para barras superiores. El texto de React Native puede aumentar por configuración de accesibilidad, pero el contenedor no crece.

**Impacto:** caracteres cortados verticalmente, botones con texto comprimido, títulos que ocupan más de una línea dentro de barras que no pueden crecer y campos difíciles de leer.

**Causa probable:** el layout se definió con métricas visuales pensadas para una escala tipográfica normal, sin probar escalas mayores.

**Prioridad:** alta.

**Solución recomendada:** reemplazar `height` por `minHeight` en controles que contengan texto, agregar padding vertical y permitir que el contenido determine la altura. Conservar tamaños fijos sólo para elementos puramente gráficos.

**Estado:** **pendiente para el frontend definitivo**.

### 2. Falta de `flexShrink` y `minWidth: 0`

**Pantalla/componente:** filas con texto y elementos laterales; especialmente `inicio`, `plan-premium`, `recordatorios`, configuración, privacidad y formularios.

**Problema:** la mayoría de los bloques de texto usa `flex: 1`, pero no existe una convención que combine `flexShrink: 1` y `minWidth: 0`. En React Native, una fila puede mantener el ancho intrínseco del texto y competir con iconos, fechas, switches o botones.

**Impacto:** texto fuera del contenedor, superposición, saltos de línea inesperados o compresión del elemento lateral.

**Causa probable:** las filas fueron ajustadas de manera individual con textos de muestra cortos.

**Prioridad:** alta.

**Solución recomendada:** crear una primitiva `ResponsiveRow` y una convención para el bloque principal: `flex: 1`, `flexShrink: 1`, `minWidth: 0`. Definir por producto cuándo envolver, cuándo truncar y cuándo mover la acción a otra línea.

**Estado:** **pendiente para el frontend definitivo**. `components/screen-header.tsx` ya aplicaba `flexShrink` al bloque de texto y sirve como referencia parcial; se considera **corregido provisionalmente** sólo para ese componente.

### 3. Filas horizontales con contenido dinámico

**Pantalla/componente:** `app/home/inicio.tsx`, `app/account/plan-premium.tsx`, `app/health/recordatorios.tsx`, `app/pets/mascota-detalle.tsx`, `app/pets/mascota-formulario.tsx`.

**Problema:** títulos, nombres de mascotas, fechas, recordatorios y descripciones provenientes del usuario o backend comparten una fila con contenido lateral rígido.

**Impacto:** el layout depende de la longitud real de los datos. Casos que funcionan con los textos actuales pueden fallar con nombres o descripciones más largos.

**Causa probable:** la composición fue validada con datos simulados o cortos y luego conectada a datos reales sin redefinir sus restricciones.

**Prioridad:** alta.

**Solución recomendada:** establecer presupuestos de espacio por patrón, permitir reflow en anchos compactos y probar fixtures con textos largos. No usar truncamiento como respuesta automática.

**Estado:** **pendiente para el frontend definitivo**.

### 4. Falta de adaptación al teclado

**Pantalla/componente:** `app/auth/login.tsx`, `app/auth/registro.tsx`, `app/auth/verificar-codigo.tsx`, `app/pets/mascota-formulario.tsx`, `app/health/registrar-evento.tsx` y el modal de `app/health/recordatorios.tsx`.

**Problema:** no se utilizaba `KeyboardAvoidingView` ni existía un wrapper de formulario común. Login y verificación tampoco tenían scroll. El bottom sheet de recordatorios contenía un formulario completo sin scroll interno.

**Impacto:** campos, acciones principales o mensajes pueden quedar detrás del teclado en dispositivos bajos. El usuario puede interpretar la pérdida de contenido como un recorte de pantalla.

**Causa probable:** las pantallas se probaron sin cubrir combinaciones de teclado visible, pantalla compacta y fuente ampliada.

**Prioridad:** crítica para autenticación y el modal de recordatorios; alta para el resto de formularios.

**Solución recomendada:** definir un `FormScreen` basado en safe area, `KeyboardAvoidingView` y `ScrollView`; controlar insets del teclado por plataforma y asegurar que la acción principal sea alcanzable.

**Estado:** **pendiente para el frontend definitivo**.

### 5. Safe areas inconsistentes

**Pantalla/componente:** conjunto de pantallas de `app/`.

**Problema:** algunas pantallas importan `SafeAreaView` desde `react-native-safe-area-context` y otras desde `react-native`. Las pantallas principales con barra inferior usan generalmente la primera variante, mientras varias pantallas internas y de autenticación usan la segunda.

**Impacto:** diferencias de padding e insets entre plataformas, cámaras perforadas, notch y barras de navegación. No necesariamente produce recorte tipográfico por sí solo, pero reduce el espacio útil y vuelve inconsistente el diagnóstico.

**Causa probable:** las pantallas fueron creadas en momentos o ramas diferentes sin un wrapper común.

**Prioridad:** alta por consistencia; media como causa directa del texto recortado.

**Solución recomendada:** centralizar safe areas en `Screen`, usar `react-native-safe-area-context` de forma consistente y declarar por pantalla qué bordes consume.

**Estado:** **pendiente para el frontend definitivo**.

### 6. Ausencia de política tipográfica

**Pantalla/componente:** todos los textos de la aplicación.

**Problema:** cada pantalla declara `fontSize`, `fontWeight` y, ocasionalmente, `lineHeight`. No existe un conjunto de variantes tipográficas ni criterios compartidos para escalado, wrapping y altura de línea.

**Impacto:** comportamiento diferente ante font scale, métricas de fuente del sistema y pesos sintetizados por distintos dispositivos. Los componentes densos son especialmente sensibles.

**Causa probable:** estilos construidos localmente y duplicados entre pantallas.

**Prioridad:** alta.

**Solución recomendada:** crear `AppText` con variantes semánticas y line heights definidos. Mantener el escalado accesible. Usar `maxFontSizeMultiplier` sólo en chrome extremadamente compacto y después de validarlo; nunca como política global.

**Estado:** **pendiente para el frontend definitivo**.

### 7. Ausencia de una estrategia real para tablets

**Pantalla/componente:** todas las pantallas; configuración `ios.supportsTablet: true` en `app.json`.

**Problema:** la aplicación declara soporte para tablet, pero el contenido ocupa el ancho disponible sin `maxWidth`, columnas adaptativas ni composición específica.

**Impacto:** no suele recortar texto, pero genera líneas demasiado largas, controles estirados y jerarquía visual débil. En algunos bloques porcentuales también puede producir tarjetas excesivamente grandes.

**Causa probable:** soporte de plataforma habilitado sin una etapa de diseño para tablet.

**Prioridad:** media. No bloquea la integración actual si las pruebas se concentran en teléfonos.

**Solución recomendada:** definir un ancho máximo centrado para contenido y habilitar composiciones de dos columnas sólo donde aporten valor.

**Estado:** **no prioritario** para `testing-integracion-native`; **pendiente para el frontend definitivo**.

### 8. Duplicación de estructuras visuales

**Pantalla/componente:** barras superiores y contenido de `badges`, `privacidad`, `plan-premium`, `album-detalle`, `historial-clinico`, `recordatorios`, `registrar-evento` y `mascota-formulario`.

**Problema:** se repiten localmente la barra de 72 px, botones de 42 px, padding de 20 px y estilos de título. Los componentes visualmente equivalentes no comparten implementación.

**Impacto:** un arreglo responsive aplicado a una pantalla no se propaga a las demás; aumenta la probabilidad de nuevas inconsistencias.

**Causa probable:** desarrollo por pantalla y por ramas de funcionalidades.

**Prioridad:** alta porque amplifica el costo de todos los demás arreglos.

**Solución recomendada:** consolidar gradualmente `Screen`, `AppHeader`, `AppButton`, `AppInput` y `ResponsiveRow`, sin intentar una reescritura masiva en la rama de integración.

**Estado:** **pendiente para el frontend definitivo**.

## Problemas puntuales

### Navegación inferior con seis opciones

**Pantalla/componente:** `components/app-bottom-nav.tsx`.

**Problema:** seis destinos comparten el ancho en partes iguales. Etiquetas como “Calendario” y “Cuidadores” se muestran con `fontSize: 8`, sin una decisión explícita para escalado, wrapping o truncamiento.

**Impacto:** etiquetas difíciles de leer y riesgo de salto o recorte en pantallas angostas o con fuente ampliada. También reduce el área táctil horizontal efectiva.

**Causa probable:** se incorporaron todos los destinos primarios a una única barra.

**Prioridad:** alta.

**Solución recomendada:** revisar la arquitectura de navegación. Evaluar un máximo menor de destinos principales y agrupar secundarios; si se mantienen seis, definir límites tipográficos accesibles y pruebas específicas.

**Estado:** **pendiente para el frontend definitivo**.

### Login y verificación sin scroll

**Pantalla/componente:** `app/auth/login.tsx` y `app/auth/verificar-codigo.tsx`.

**Problema:** utilizaban un contenedor vertical fijo, márgenes superiores amplios y controles de altura fija, sin scroll ni adaptación al teclado.

**Impacto:** son las pantallas con mayor probabilidad de perder acciones o textos en equipos bajos.

**Causa probable:** layout diseñado para una altura de referencia.

**Prioridad:** crítica.

**Solución recomendada:** migrarlas primero al wrapper común de formularios.

**Estado:** **pendiente para el frontend definitivo**; sólo debería corregirse en testing si bloquea el flujo de autenticación en los dispositivos de prueba.

### Bottom sheet de recordatorios

**Pantalla/componente:** modal en `app/health/recordatorios.tsx`.

**Problema:** el sheet tenía padding fijo y varios campos, pero no disponía de scroll ni tratamiento de teclado.

**Impacto:** el botón de creación puede quedar oculto en pantallas pequeñas.

**Causa probable:** el modal fue concebido con contenido de altura estable.

**Prioridad:** crítica cuando se prueba creación de recordatorios.

**Solución recomendada:** sheet desplazable, altura máxima dependiente de la ventana e insets del teclado.

**Estado:** **pendiente para el frontend definitivo**; corregir mínimamente en testing sólo si bloquea la integración.

### Encabezado de Plan Premium

**Pantalla/componente:** `app/account/plan-premium.tsx`.

**Problema:** descripción y precio/estado comparten una fila con `justifyContent: "space-between"`, sin restricciones de ancho o contracción.

**Impacto:** “Próximamente” puede colisionar con la descripción en ancho reducido o font scale alto.

**Causa probable:** el encabezado se diseñó originalmente para un precio corto.

**Prioridad:** alta.

**Solución recomendada:** permitir que el bloque descriptivo se contraiga o apilar el estado en layout compacto.

**Estado:** **pendiente para el frontend definitivo**.

### Inicio: recordatorios, encabezados y tarjetas de mascotas

**Pantalla/componente:** `app/home/inicio.tsx`.

**Problema:** existen encabezados título/acción, recordatorios texto/fecha y hasta tres tarjetas de mascota en una fila sin wrap.

**Impacto:** títulos, nombres, razas o fechas reales pueden desbordar. El riesgo aumentó al reemplazar datos simulados por datos de integración.

**Causa probable:** composición validada con contenido controlado.

**Prioridad:** alta.

**Solución recomendada:** agregar restricciones a los bloques de texto y cambiar cantidad de columnas según ancho disponible.

**Estado:** la eliminación de datos simulados estaba **corregida provisionalmente en `testing-integracion-native`**; la adaptabilidad seguía **pendiente para el frontend definitivo**.

### Formularios y acciones en dos o tres columnas

**Pantalla/componente:** `app/auth/registro.tsx`, `app/pets/mascota-formulario.tsx`, `app/pets/mascota-detalle.tsx`, `app/health/registrar-evento.tsx`.

**Problema:** nombre/apellido, edad/peso, especies, accesos rápidos y tipos de evento se fuerzan en dos o tres columnas.

**Impacto:** etiquetas y valores pierden espacio en anchos compactos y con fuente ampliada.

**Causa probable:** ausencia de una regla de reflow por disponibilidad real de espacio.

**Prioridad:** media-alta.

**Solución recomendada:** usar `useWindowDimensions` para cambiar composición, no para escalar el contenido. Apilar campos o reducir columnas cuando el contenido no alcance su ancho mínimo.

**Estado:** **pendiente para el frontend definitivo**.

### Truncamiento en detalle de álbum

**Pantalla/componente:** `app/albums/album-detalle.tsx`.

**Problema:** título del álbum y título de cada foto usan `numberOfLines={1}`. En este contexto evita que la grilla y el header se rompan, pero oculta parte del valor.

**Impacto:** pérdida controlada de información en títulos largos.

**Causa probable:** decisión local para proteger una composición compacta.

**Prioridad:** baja.

**Solución recomendada:** mantener truncamiento sólo si es una decisión de producto, usar `ellipsizeMode="tail"` explícito y ofrecer el nombre completo en la vista de detalle o accesibilidad.

**Estado:** **corregido provisionalmente en `testing-integracion-native`**; revisar en el frontend definitivo. No es evidencia de que deba aplicarse `numberOfLines` al resto de la aplicación.

## Errores y deuda de rutas Expo

### Rutas reorganizadas y tipado omitido en la barra inferior

**Pantalla/componente:** árbol `app/`, `app/_layout.tsx` y `components/app-bottom-nav.tsx`.

**Problema:** durante la integración existieron errores preexistentes asociados a la reorganización de rutas Expo. En el estado revisado, los destinos utilizados existen y `tsc --noEmit` finaliza correctamente. Sin embargo, la barra inferior convierte sus rutas con `as Href`, lo que evita que `typedRoutes` valide de forma completa ese mapa.

**Impacto:** una ruta renombrada o eliminada en un merge futuro puede compilar en ese componente y fallar en navegación en tiempo de ejecución.

**Causa probable:** movimiento de pantallas entre ramas y necesidad de estabilizar rápidamente la integración.

**Prioridad:** media.

**Solución recomendada:** tipar el mapa de rutas sin cast, mantener una única fuente de destinos, regenerar los tipos de Expo Router después de reorganizaciones y ejecutar un smoke test de todos los accesos.

**Estado:** **corregido provisionalmente en `testing-integracion-native`** respecto de los destinos actuales; el endurecimiento del tipado queda **pendiente para el frontend definitivo**.

> No se documenta aquí un error de ruta actualmente reproducible: la deuda comprobable es la pérdida parcial de validación causada por el cast y el antecedente de reorganización entre ramas.

## Recomendaciones futuras de arquitectura visual

Estas recomendaciones son una dirección de diseño, no tareas obligatorias inmediatas.

### `Screen`

Wrapper para safe area, fondo, padding, scroll opcional, ancho máximo y comportamiento con teclado. Debe poder expresar pantallas principales, internas y formularios sin duplicar estructura.

### `AppText`

Variantes semánticas —por ejemplo título de pantalla, título de sección, cuerpo, etiqueta y caption— con tamaños, pesos y line heights consistentes. Debe conservar accesibilidad y definir explícitamente dónde se acepta truncamiento.

### `AppHeader`

Encabezado común para botón de regreso, título, subtítulo y acción lateral. El título debe contar con un área flexible y comportamiento definido para dos líneas o elipsis.

### `AppButton`

Usar `minHeight` y padding vertical. Debe soportar texto ampliado, estado de carga, iconos y variante deshabilitada sin depender de una altura rígida.

### `AppInput`

Campo común con etiqueta, mensaje de error, iconos y altura mínima. Evitar overlays manuales de placeholder y conservar un área táctil accesible.

### `ResponsiveRow`

Patrón para icono, bloque principal y acción lateral. El bloque principal debería partir de `flex: 1`, `flexShrink: 1` y `minWidth: 0`.

### Uso de `useWindowDimensions`

Utilizarlo para decisiones discretas de composición:

- apilar una fila;
- cambiar de tres a dos o una columna;
- limitar el ancho de formularios en tablet;
- reducir padding cuando el ancho útil no alcanza.

No utilizarlo para multiplicar todos los tamaños ni recrear un zoom manual de la interfaz.

### Tratamiento de teclado

Crear un patrón común con `KeyboardAvoidingView` y contenido desplazable. Verificar Android e iOS por separado y garantizar que el campo enfocado y la acción principal sean alcanzables.

### Safe areas

Usar `react-native-safe-area-context` de forma uniforme. Centralizar en `Screen` qué bordes se respetan y evitar compensaciones manuales por pantalla.

### Line heights

Definirlos junto con cada variante tipográfica y probarlos con la fuente real en Android e iOS. Evitar line heights demasiado ajustados, especialmente con pesos altos y font scale mayor a 1.

### Testing por ancho y escala tipográfica

Las pruebas deben incluir contenido realista y casos deliberadamente largos, no sólo los textos actuales.

| Ancho lógico | Font scale 1.0 | Font scale 1.3 | Font scale 1.6 |
|---:|:---:|:---:|:---:|
| 320 px | Obligatorio | Obligatorio | Obligatorio |
| 360 px | Obligatorio | Obligatorio | Obligatorio |
| 390 px | Obligatorio | Obligatorio | Obligatorio |

Cobertura mínima adicional:

- Android e iOS;
- teclado visible en todos los formularios;
- nombres, emails, títulos y descripciones largos;
- barra inferior completa;
- headers internos;
- rotación no requerida mientras `orientation` continúe configurada como `portrait`;
- al menos una tablet antes de declarar soporte de tablet terminado.

## Qué no conviene hacer

- Desactivar globalmente `allowFontScaling`.
- Crear fixes por marca o modelo de dispositivo.
- Reducir fuentes solamente en las pantallas donde se observó el fallo.
- Llenar la interfaz de `numberOfLines={1}` para ocultar desbordes.
- Agregar alturas fijas para compensar problemas de alineación.
- Escalar toda la interfaz con `PixelRatio`.
- Multiplicar cada medida según el ancho de pantalla.
- Resolver cada pantalla de forma aislada sin criterios y componentes comunes.
- Convertir todos los layouts en porcentajes; no resuelve las restricciones intrínsecas del texto.
- Usar `maxFontSizeMultiplier` como sustituto de un layout capaz de crecer.

## Nota sobre `AGENTS.md` y la versión de Expo

`frontend-native/AGENTS.md` indica que debe consultarse documentación versionada de Expo 54 antes de escribir código. El proyecto real declara Expo `^57.0.24`, Expo Router `~57.0.19` y React Native `0.86.3` en `frontend-native/package.json`.

Esta diferencia puede llevar a aplicar APIs, defaults o recomendaciones incompatibles con el runtime actual.

**Prioridad:** alta antes de una refactorización estructural.

**Recomendación:** actualizar `AGENTS.md` para apuntar a la versión efectiva o definir un procedimiento explícito de actualización coordinada. No cambiar dependencias para hacerlas coincidir con el documento sin una decisión técnica separada.

**Estado:** **pendiente para el frontend definitivo**. No bloquea las pruebas actuales si no se implementan cambios basados en documentación de Expo 54.

## Otras deudas transversales observadas

### Sesión

La integración actual ya maneja `accessToken`, `refreshToken`, renovación y logout, y usa `SecureStore` en Native. Esto representa una **corrección provisional en `testing-integracion-native`** frente al contrato anterior.

Quedan dos puntos para revisión futura:

- `auth-storage.ts` cae a `AsyncStorage` si falla `SecureStore`; debe decidirse si ese fallback es aceptable para credenciales en una versión productiva;
- el reenvío de 2FA conserva email y contraseña en memoria para volver a ejecutar login. La limitación ya está relacionada con el contrato actual del backend y no debe resolverse sólo desde UI.

**Prioridad:** media para revisión de seguridad; no forma parte del arreglo responsive.

### Navegación

La navegación principal está implementada manualmente mediante `AppBottomNav` y `router.replace`, en vez de una estructura declarativa de tabs. Además del riesgo visual de seis opciones, esto duplica conocimiento de rutas y limita el estado/navegación que podría administrar React Navigation.

**Prioridad:** media. Evaluar junto con la arquitectura de navegación definitiva; no migrar durante integración sólo por preferencia técnica.

### Duplicación

Headers, botones, inputs, tarjetas y tokens visuales se repiten en archivos de pantalla. Esta deuda es directamente relevante porque impide corregir responsive una sola vez.

**Prioridad:** alta para el frontend definitivo.

### Contratos

Parte de la UI todavía refleja capacidades no conectadas o temporalmente deshabilitadas —badges, álbumes/fotos, cuidadores, plan premium y edición de perfil—. La rama de integración ya reemplazó varias simulaciones por estados vacíos o mensajes de “función no disponible”.

**Estado:** **corregido provisionalmente en `testing-integracion-native`** para evitar datos falsos. Los contratos y flujos definitivos siguen pendientes por módulo y no deben inventarse desde el frontend.

### Accesibilidad

Además del font scaling, no se observa una política común para `accessibilityRole`, labels accesibles, estados de botones o tamaño mínimo de targets. Los iconos suelen estar acompañados por texto, por lo que no todos los casos son defectos actuales, pero hace falta una auditoría antes de producción.

**Prioridad:** media. Integrarla en la creación de componentes base para no duplicar trabajo.

### Dependencias y verificación

`tsc --noEmit` finaliza sin errores en el estado revisado. El comando estándar `expo lint` no pudo completarse en esta revisión porque intentó escribir su caché dentro de `.expo`; esto no demuestra un error del código y no debe registrarse como defecto funcional.

Antes del frontend definitivo conviene fijar una verificación reproducible sin depender de caché local y alinear las instrucciones de Expo con las versiones instaladas.

## Orden sugerido para el frontend definitivo

1. Confirmar dispositivos, escalas y casos que reproducen el recorte.
2. Alinear documentación y versión de Expo.
3. Definir `AppText`, `Screen`, safe areas y tratamiento de teclado.
4. Implementar `AppHeader`, `AppButton`, `AppInput` y `ResponsiveRow`.
5. Migrar autenticación y modal de recordatorios.
6. Corregir navegación inferior y filas con datos dinámicos.
7. Adaptar grillas y formularios multicolumna.
8. Incorporar tablet y matriz de regresión visual/accesible.

Este orden busca corregir primero las causas compartidas y después las excepciones de pantalla, evitando una colección de hacks por dispositivo.
