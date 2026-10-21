# SoundIA — API Core (Spring Boot)

API Gateway de SoundIA. Recibe la telemetría de la consola DJ, consulta al motor de IA (Django),
guarda el resultado en PostgreSQL y lo transmite en vivo al frontend (Angular).

```
Angular (:4200) ──► Spring Boot (:8080) ──► Django (:8000)
                        │  └── PostgreSQL
                        └── SSE en vivo hacia Angular
```

## Requisitos

- Java 25 y Maven (incluye `mvnw`)
- PostgreSQL (solo para ejecutar la app; los tests usan H2 en memoria)

## Configuración

Copia `.env.example` a `.env` (está en `.gitignore`) y ajusta los valores, o define las variables de entorno.

| Variable / propiedad | Por defecto | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/soundia_db` | URL de PostgreSQL |
| `DB_USERNAME` | `soundia_user` | Usuario |
| `DB_PASSWORD` | — (obligatoria) | Contraseña |
| `soundia.ia-engine.base-url` | `http://localhost:8000` | URL del motor de IA |
| `soundia.ia-engine.connect-timeout` / `read-timeout` | `2s` / `5s` | Timeouts hacia Django |
| `soundia.cors.allowed-origins` | `http://localhost:4200` | Orígenes permitidos (separados por coma) |
| `soundia.stream.timeout` | `30m` | Duración máxima de una conexión SSE |
| `soundia.stream.heartbeat` | `15s` | Intervalo del latido `keep-alive` |

## Ejecutar y probar

```bash
./mvnw spring-boot:run     # requiere DB_PASSWORD
./mvnw test                # no requiere PostgreSQL ni DB_PASSWORD
```

## Endpoints (`/api/v1/midi`)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/event` | Recibe un evento MIDI `{controlId, value}`. Responde al instante; la IA se procesa en segundo plano. |
| `POST` | `/ai/predict` | Igual que `/event` pero síncrono: devuelve la decisión `{status, action}`. |
| `GET` | `/logs` | Últimos 50 eventos guardados (incluye `action`, `null` si la IA no estuvo disponible). |
| `GET` | `/stream` | Flujo SSE en vivo (ver abajo). |

### Sesiones (`/api/v1/sessions`)

Una sesión agrupa los eventos MIDI desde que el DJ la inicia hasta que la cierra. **Solo puede haber una abierta a la vez**
(iniciar otra devuelve 409). Cada evento procesado se asocia a la sesión abierta *en el momento de guardarse*; sin sesión
abierta se guarda igual, con `sessionId` nulo.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/` | `{name}` (obligatorio, máx. 100). Inicia una sesión: 201 con `Location`; 409 si ya hay una abierta. |
| `GET` | `/` | Las 20 sesiones más recientes. |
| `GET` | `/active` | La sesión abierta; 404 si no hay. |
| `GET` | `/{id}` | Una sesión; 404 si no existe. |
| `GET` | `/{id}/logs` | Eventos de la sesión, del más antiguo al más reciente (máx. 500). |
| `POST` | `/{id}/end` | Cierra la sesión; 409 si ya estaba cerrada. |

`controlId` debe ser ≥ 0 y `value` estar entre 0 y 127. Los errores devuelven `{ "error": "...", "detalles": [...] }`
con 400 (validación o petición mal formada), 404, 405, 409 (conflicto), 415, 502 (motor de IA caído) o 500.

### Flujo en vivo (SSE)

`GET /api/v1/midi/stream` mantiene la conexión abierta (`text/event-stream`) y envía:

| Evento | Cuerpo | Cuándo |
|---|---|---|
| `midi` | `{"controlId":14,"value":127}` | Al llegar un evento MIDI |
| `decision` | `{"id":1,"controlId":14,"value":127,"status":"success","action":"...","createdIn":"..."}` | Cuando la IA responde y se guarda. Si la IA no responde tras 3 intentos, `status` es `unavailable` y `action` es `null`. |
| *(comentario)* `:keep-alive` | — | Cada 15 s, para mantener viva la conexión |

Ejemplo en el navegador:

```js
const es = new EventSource('http://localhost:8080/api/v1/midi/stream');
es.addEventListener('midi', (e) => console.log('MIDI', JSON.parse(e.data)));
es.addEventListener('decision', (e) => console.log('IA', JSON.parse(e.data)));
```

## Arquitectura

- **Observer**: `DjControllerReader` notifica a todos los `MidiObserver` registrados como beans.
  `MidiProcessingObserver` (IA + guardado, asíncrono con reintentos) y `LiveStreamObserver` (SSE).
- `MidiEventService` es el único flujo de un evento: consulta la IA, guarda **un** registro y publica un evento interno.
- `LiveStreamPublisher` convierte ese evento interno en un mensaje SSE.
