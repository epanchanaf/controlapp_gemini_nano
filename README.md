# Control de viaje

Aplicación Android local en Kotlin y Jetpack Compose para registrar gastos de viaje. El flujo principal permite dictar «Gasté 24 dólares en taxi hoy», transcribirlo con ML Kit Speech Recognition, extraer una propuesta con Gemini Nano Prompt, revisar importe/moneda/fecha/categoría y confirmar antes de guardar.

Al abrir, la app muestra los viajes creados y el botón «+ Viaje». La barra inferior ofrece Viajes y Asistente. Asistente abre un panel en el 60 % del área de contenido de la pantalla actual, con modos de texto y voz; no cambia de ruta. Dentro de un viaje, las pestañas Gastos y Plan muestran sus gastos y presupuestos. En el resumen de Gastos, «Editar viaje» permite cambiar nombre, fechas, moneda base y presupuesto general. Asistente conserva sus conversaciones al cambiar de sección; por voz puede crear viajes y preparar gastos para el viaje seleccionado. Cada propuesta de gasto se revisa y confirma antes de guardarse. Un menú desplegable en Gastos reúne la búsqueda y las opciones para agregar gastos: manual abre el formulario, voz inicia el dictado en el mismo listado e imagen abre un comprobante para combinar OCR con Gemini Nano.

La fecha se selecciona con el selector de fecha Material 3 de Compose; la app guarda el valor en formato `AAAA-MM-DD` y lo valida antes de crear o modificar un gasto.

## Compilar

- Android Studio con JDK 17, SDK Android 37.1 y Gradle 9.6 (wrapper incluido).
- Ejecutar `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
- APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.
- `minSdk 26`; los modelos GenAI exigen AICore y compatibilidad específica comprobada en tiempo de ejecución.
- Para Samsung Galaxy Z Flip8, prueba en el teléfono real con AICore y Speech Recognition actualizados. El reconocimiento avanzado figura entre las capacidades admitidas en la documentación de ML Kit, pero el modelo puede requerir descarga. La API Agent y Embeddings pueden exigir la cuenta en AICore Developer Preview. El estado real se muestra antes de ejecutar.

## Arquitectura

- `feature/trips/data/`: base Room (`TravelDatabase`), DAO, entidades de viaje y catálogo, y `TravelRepository`, que coordina las operaciones compartidas entre viajes, gastos y presupuestos. Los importes se guardan en centavos (`Long`) para evitar errores de coma flotante.
- `feature/expenses/data/`: entidad de gasto, validación y clasificación de importes, y extracción conservadora del total de comprobantes.
- `feature/budgets/data/`: entidad de presupuesto por categoría y moneda.
- `feature/voice/data/`: reconocimiento de voz y reglas para interpretar fechas e importes de presupuesto dictados.
- Hilt proporciona los ViewModels, repositorios y el DAO de Room. Las AppFunctions obtienen el mismo DAO mediante un punto de entrada de Hilt, sin abrir otra base de datos por su cuenta.
- `feature/expenses/ui/`: estado y ViewModel de gastos, OCR de comprobantes y confirmación de alta, cambio y borrado.
- `feature/voice/data/` y `feature/voice/ui/`: repositorio de reconocimiento avanzado de ML Kit y ViewModels de dictado y conversación. El resultado se pasa a `NanoRepository`; la propuesta se puede editar. Se detectan posibles duplicados exactos antes de guardar.
- `feature/budgets/ui/`: estado y ViewModel del presupuesto y las alertas por categoría y moneda.
- `feature/ai/data/`: Prompt para extraer gastos dictados, analizar comprobantes e interpretar solicitudes del asistente; Embeddings para búsqueda semántica; Proofreading y Rewriting para mejorar texto. `TravelTools` expone AppFunctions: Gemini puede listar viajes y, tras una orden explícita de la persona, registrar un gasto validado en uno de ellos. `AgentRepository` conserva las consultas locales de solo lectura.
- `feature/ai/ui/`: ViewModel del asistente y `AgentA2uiCard`, que muestra superficies A2UI Material 3 para respuestas estructuradas sobre saldo, gastos y presupuestos por categoría.
- `ui/theme/`: paleta oscura 80/20 y estilos Compose reutilizables para tarjetas, bordes activos y alertas. Los excesos de presupuesto se identifican con icono y texto además del ámbar.
- `MainActivity.kt`: `NavHost` y `NavController` para viajes, listado de gastos, formulario y presupuestos. La barra inferior permite volver a Viajes o abrir el panel transversal del Asistente; Gastos y Plan son pestañas dentro de cada viaje.

## Criterios de análisis

«Alto/medio/bajo» compara un gasto con el límite de su categoría; sin presupuesto usa una referencia de 100 unidades monetarias. «Bueno/malo» es una sugerencia basada en si el gasto supera el saldo del presupuesto de su categoría, o si es esencial cuando aún no hay presupuesto. No es una evaluación moral del viajero y se muestra junto con su motivo. La persona revisa los datos antes de guardar.

OCR de ML Kit lee texto del comprobante y propone el importe de la línea «TOTAL». En el flujo «Agregar gasto por imagen», Gemini Nano recibe la imagen y el texto OCR para proponer importe, moneda, fecha, categoría, concepto y notas. Si OCR y Gemini proponen importes distintos, el campo queda vacío y se muestran ambos para revisión. La persona revisa la propuesta y confirma la vista previa antes de guardar.

## ML Kit, AICore y Gemini Nano

### Concepto general

**AICore** es el servicio del sistema Android que permite a las aplicaciones compatibles acceder a modelos de IA generativa en el dispositivo. En esta app es el proveedor de Gemini Nano y de varias capacidades GenAI de ML Kit. La información de gastos se conserva en Room; las solicitudes de IA se envían al modelo disponible localmente, no a un backend propio de la aplicación.

Que un teléfono tenga la app instalada no implica que cada capacidad esté preparada. Para las funciones GenAI, la app consulta el estado del modelo: `Disponible`, `Descargable`, `Descargando` o `No disponible`. Si el modelo se puede descargar, la pantalla ofrece el botón correspondiente. Esto permite que la app se adapte a AICore, a la cuenta de Developer Preview y a los servicios de voz que haya en el dispositivo.

### Resumen de funciones y activación

| Función | Dónde se activa | Uso en la app |
| --- | --- | --- |
| Speech Recognition | **Gastos > + > Agregar gasto por voz** o **Asistente > Voz > Hablar**. Requiere permiso de micrófono; si aparece, pulsa **Descargar voz** o **Activar voz**. | Transcribe el dictado para crear un gasto o conversar con el asistente. |
| Prompt API (Gemini Nano) | Se ejecuta tras el dictado, al analizar un comprobante y al interpretar solicitudes por voz en el asistente. Si aparece, pulsa **Descargar Nano**, **Activar Nano** o **Activar Gemini Nano**. | Propone campos del gasto a partir de texto o imagen y reconoce órdenes como crear un viaje o preparar un gasto. |
| Text Recognition (OCR) | **Gastos > + > Agregar gasto por imagen** y selección de un comprobante. | Lee el texto de la imagen y busca el importe de la línea «TOTAL». |
| Embeddings | **Gastos > + > Buscar gastos** o **Mis viajes > Buscar gastos en todos los viajes**. Si aparece, pulsa **Activar búsqueda en este dispositivo**. | Encuentra gastos por similitud de significado. |
| Proofreading | Formulario de gasto: **Mejorar > Corregir ortografía** en Descripción o Notas. También se intenta aplicar al texto extraído de un gasto dictado. | Propone correcciones de redacción y ortografía. |
| Rewriting | Formulario de gasto: **Mejorar > Reformular** en Descripción o Notas. | Propone otra redacción; se aplica solo al pulsar **Usar propuesta**. |

Las propuestas de gastos se revisan antes de guardarse. OCR es una función de visión de ML Kit; A2UI, descrito más abajo, es una biblioteca de interfaz y no una función de ML Kit.

El Galaxy Z Flip8 es compatible con Gemini Nano y con Speech Recognition avanzado según la lista de dispositivos de ML Kit. Agent y Embeddings pueden requerir acceso de Developer Preview; por eso el estado que muestra la aplicación siempre prevalece sobre esta compatibilidad teórica.

### Prompt API con Gemini Nano

**Concepto.** Prompt API recibe instrucciones en lenguaje natural y devuelve texto generado. También puede recibir una imagen junto con texto, de modo que una misma consulta puede tener contexto visual.

**Uso en la app.** En **Gastos > Agregar gasto por voz**, el texto reconocido se procesa con una instrucción estructurada: Gemini Nano propone `importe`, `moneda`, `fecha`, `categoría`, `concepto` y `notas` como JSON. La aplicación valida esos valores y no guarda nada hasta que la persona lo confirma. La interfaz independiente de consultas libres e imágenes se retiró de IA.

En **Gastos > Agregar gasto por imagen**, Prompt multimodal analiza la imagen junto al texto OCR y propone los mismos campos en el formulario de gasto. El importe OCR tiene prioridad cuando se distingue la línea «TOTAL»; una discrepancia con Gemini exige que la persona escriba el importe revisado.

### Speech Recognition

**Concepto.** Speech Recognition transcribe audio del micrófono en el dispositivo. Puede emitir resultados parciales mientras se habla y un resultado final cuando termina la captura.

**Uso en la app.** Desde **Gastos > Agregar gasto por voz**, el usuario dicta por ejemplo «Gasté 24 dólares en taxi hoy». La transcripción se presenta temporalmente en el mismo listado; al recibir el resultado final, se envía al flujo Prompt para extraer el gasto. Se puede detener la captura y, si el modelo no está instalado, descargarlo desde la interfaz. La fecha «hoy», «ayer» o «anteayer» se resuelve con el calendario del dispositivo; una fecha sin mención queda pendiente de revisión.

### Agent y AppFunctions locales

**Concepto.** Agent permite una conversación de varios pasos con un modelo generativo. Una AppFunction expone una acción local y acotada para que el agente la invoque, sin otorgarle acceso arbitrario a la aplicación.

**Estado en la app.** No hay una pantalla ni un botón de Agent. El Asistente llama a `AgentRepository` para preguntas sobre saldo, presupuestos y gastos. Cuando identifica un viaje y el tipo de consulta, el repositorio lee Room y construye directamente la respuesta y su tarjeta A2UI. Para consultas que no resuelve por esa vía, conserva un ejecutor Agent con AppFunctions locales `trip_budgets` y `trip_expenses`, ambas de solo lectura.

La app también publica para asistentes Android de confianza (incluida Gemini, cuando la integración esté disponible para la cuenta/dispositivo) `list_trips` y `add_expense_to_trip`. Gemini debe listar primero los viajes, solicitar aclaración si el nombre es ambiguo y solo usar `add_expense_to_trip` ante una orden explícita de guardar. La acción exige importe positivo en centavos, moneda admitida (`USD`, `EUR`, `PEN` o `COP`), fecha `AAAA-MM-DD`, categoría válida y descripción. Al guardarse, se recalculan los indicadores de presupuesto igual que en el formulario de la app. AppFunctions es una vista previa y la integración de Gemini continúa limitada a probadores autorizados; por ello la función puede no aparecer aún aunque la APK la incluya.

### Embeddings

**Concepto.** Un embedding transforma un texto en un vector numérico. Al comparar vectores se puede encontrar texto parecido por significado, aunque las palabras no sean idénticas.

**Uso en la app.** Desde el menú **Gastos > Buscar gastos**, el usuario escribe una búsqueda como «transporte al aeropuerto». La app genera el vector de la consulta y lo compara contra la descripción, nota y categoría de hasta 50 gastos del viaje; muestra hasta diez gastos relacionados en el propio listado, donde se pueden editar o borrar. Si el modelo se puede descargar, el buscador ofrece activarlo. La detección de duplicados al guardar sigue siendo conservadora y se basa en concepto e importe iguales.

En **Mis viajes > Buscar gastos en todos los viajes**, la misma búsqueda recorre los gastos de todos los viajes y muestra hasta diez resultados con el nombre del viaje. Al tocar uno se abre su formulario para revisarlo o editarlo.

### Proofreading

**Concepto.** Proofreading detecta y propone correcciones de ortografía, gramática y redacción sin cambiar la intención del texto.

**Uso en la app.** En el formulario de gasto, el botón **Mejorar** junto a Descripción y Notas permite elegir **Corregir ortografía**. Se muestra la propuesta antes de aplicarla; el texto original se conserva si se descarta.

### Rewriting

**Concepto.** Rewriting reformula un texto con otra redacción, manteniendo el sentido general. Sirve para transformar frases incompletas o poco claras en descripciones más legibles.

**Uso en la app.** En el mismo botón **Mejorar**, la opción **Reformular** propone una redacción alternativa en español. Solo sustituye el campo cuando la persona pulsa **Usar propuesta**.

### OCR de comprobantes

**Concepto.** OCR (Text Recognition de ML Kit) detecta texto dentro de una imagen. No es una API generativa de Gemini Nano; la app combina su resultado con Prompt multimodal para proponer el gasto.

**Uso en la app.** Desde **Gastos > Agregar gasto por imagen**, la app guarda una copia local de la imagen, extrae su texto y busca una cantidad situada en una línea con «TOTAL». El importe propuesto se muestra en el formulario para revisión antes de confirmar el gasto completo.

### A2UI en las respuestas

**Concepto.** A2UI permite renderizar superficies de interfaz a partir de mensajes estructurados con componentes nativos de Compose. La interfaz que ejecuta acciones debe seguir controlada por la app anfitriona.

**Dónde se activa.** Abre **Asistente > Texto** y pregunta, por ejemplo, «¿Cuál es el saldo del viaje Quito?», «¿Cuál es el presupuesto general de Quito?», «Muéstrame los gastos de Quito» o «¿Cuáles son los límites por categoría de Quito?». También funciona desde **Asistente > Voz** con una pregunta reconocida. El asistente debe poder identificar un viaje único para construir la tarjeta.

**Uso en la app.** `AgentRepository.replyForUi` lee los datos de Room y entrega uno de cuatro modelos visuales: saldo, presupuesto general, gastos o categorías. `AgentA2uiCard` los convierte en componentes A2UI Material 3 y los muestra dentro de la conversación, tanto en texto como en voz. Saldo destaca el importe disponible y el avance; presupuesto general muestra el límite y el consumo; gastos muestra cantidad, totales por moneda y últimos movimientos; categorías muestra límite, consumo y exceso o restante. Las respuestas libres del asistente aparecen como texto normal. A2UI solo presenta información; las acciones que escriben datos se ejecutan en los ViewModels y requieren la confirmación correspondiente.

## Pruebas

- `ExpenseRulesTest`: validación de importe, moneda, fecha y categoría.
- `ExternalExpenseValidationTest`: validación de los datos recibidos desde una AppFunction externa.
- `VoiceDateRulesTest`: fechas relativas y ausencia de fecha en dictado.
- `ReceiptAmountRulesTest`: lectura conservadora del total OCR.
- `AgentResponseTest`: oculta marcadores internos de AppFunctions.
- `TravelPersistenceTest` (instrumentación): inserta un viaje/gasto, cierra Room y comprueba los datos tras reabrir la base. Ejecutar en dispositivo o emulador con `./gradlew :app:connectedDebugAndroidTest`.

## Límites actuales

Embeddings es una búsqueda local en memoria de hasta 50 gastos por viaje y requiere Developer Preview; para un historial grande conviene indexar con AppSearch. La app no convierte monedas ni deduce tasas de cambio. La clasificación por presupuesto considera el gasto acumulado de la categoría, sin periodo temporal. Las fechas relativas de voz se resuelven con el calendario del dispositivo; una fecha no mencionada queda pendiente de revisión.

## Fuentes oficiales

- [ML Kit GenAI](https://developers.google.com/ml-kit/genai)
- [Prompt y Gemini Nano](https://developers.google.com/ml-kit/genai/prompt/android)
- [Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android)
- [Agent y AppFunctions](https://developers.google.com/ml-kit/genai/agent/android/define-tools)
- [Embeddings y búsqueda semántica](https://developers.google.com/ml-kit/genai/embedding/android)
- [Proofreading](https://developers.google.com/ml-kit/genai/proofreading/android)
- [Rewriting](https://developers.google.com/ml-kit/genai/rewriting/android)
- [OCR / Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/android)
- [A2UI Compose](https://developer.android.com/develop/ui/compose/agentic/render-surfaces)
