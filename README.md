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
| `soundia.ia-engine.analysis-timeout` | `120s` | Timeout de lectura del análisis de audio |
| `soundia.cors.allowed-origins` | `http://localhost:4200` | Orígenes permitidos (separados por coma) |
| `soundia.stream.timeout` | `30m` | Duración máxima de una conexión SSE |
| `soundia.stream.heartbeat` | `15s` | Intervalo del latido `keep-alive` |
| `SOUNDIA_AUTH_ENABLED` | `false` | Enciende la autenticación (login con un usuario DJ); ver [Autenticación](#autenticación) |
| `SOUNDIA_AUTH_USERNAME` / `SOUNDIA_AUTH_PASSWORD` | — (obligatorias si está encendida) | Usuario y contraseña del DJ |
| `SOUNDIA_AUTH_JWT_SECRET` | — (obligatoria si está encendida) | Clave con la que se firman los tokens: al menos 32 caracteres, aleatoria y secreta |
| `soundia.auth.token-ttl` | `8h` | Vida del token de acceso |
| `soundia.auth.stream-ticket-ttl` | `30s` | Vida del ticket para abrir el stream en vivo |
| `soundia.auth.max-login-attempts` / `login-window` | `5` / `1m` | Intentos fallidos por dirección antes de bloquear con 429 |

## Autenticación

Viene **apagada por defecto**: el API funciona sin login, como hasta ahora. Para encenderla (hazlo en cualquier
despliegue):

```
SOUNDIA_AUTH_ENABLED=true
SOUNDIA_AUTH_USERNAME=dj
SOUNDIA_AUTH_PASSWORD=<una contraseña larga>
SOUNDIA_AUTH_JWT_SECRET=<32 o más caracteres aleatorios>     # p. ej.: openssl rand -base64 48
```

Sin alguna de las tres, con la autenticación encendida la aplicación **no arranca** (con un mensaje que dice cuál falta).
Los valores van solo en variables de entorno o en el `.env` local, nunca en el repositorio.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/v1/auth/status` | Público. `{enabled}`: el front lo usa para saber si hay que pedir login. |
| `POST` | `/api/v1/auth/login` | Público. `{username, password}` → `{token, expiresAt}`. 401 con un mensaje genérico (no dice si falló el usuario o la contraseña); 429 con `Retry-After` tras demasiados fallos; 404 si la autenticación está apagada. |
| `POST` | `/api/v1/auth/stream-ticket` | Con token. `{ticket, expiresAt}`: un ticket de 30 s que solo sirve para abrir el stream en vivo. |

Con la autenticación encendida **todas las rutas `/api/**`** (salvo el login y el estado) exigen
`Authorization: Bearer <token>`; si no, 401. Las peticiones `OPTIONS` (preflight de CORS) pasan sin token, y las
respuestas 401 llevan las cabeceras de CORS para que el navegador muestre «no autorizado» y no un error de red.

- **El stream en vivo** (`GET /api/v1/midi/stream`) lo abre el navegador con `EventSource`, que no puede enviar
  cabeceras. Por eso se acepta también `?ticket=<ticket>`, y **solo** en esa ruta y con ese método: el ticket dura 30 s y
  no sirve para nada más, así el token de acceso nunca viaja en una URL. Hay que pedir un ticket nuevo cada vez que se
  abra o reconecte el stream.
- **Tokens:** JWT firmados con HS256 (se rechaza cualquier otro algoritmo, incluido `none`), con emisor, caducidad y
  clase (`access` o `stream`) comprobados. La contraseña configurada solo se guarda en memoria como hash BCrypt.
- **Límite de intentos:** 5 fallos por dirección en 1 minuto bloquean esa dirección; un login correcto borra los fallos.

Limitaciones: es **un solo usuario**; los tokens no se pueden revocar antes de que caduquen (hay que cambiar
`SOUNDIA_AUTH_JWT_SECRET` y reiniciar para invalidarlos todos); el límite de intentos está en memoria, se reinicia con
la aplicación y mide la dirección de la conexión (detrás de un proxy inverso todos los clientes comparten la del
proxy); usa **HTTPS** en producción, porque el token y la contraseña viajan en cada petición; y el motor de IA
(Django) **no tiene autenticación propia**: no lo expongas a Internet, solo debe verlo este API.

## Con Docker

`docker-compose.yml` levanta PostgreSQL, el motor de IA, este API y el frontend juntos. Necesita los tres repositorios
clonados uno junto a otro y un `.env` con `DB_PASSWORD` y `DJANGO_SECRET_KEY`:

```bash
cp .env.example .env     # y edita las contraseñas
docker compose up --build
```

Frontend en http://localhost:4200 y API en http://localhost:8080. La base de datos y los audios guardados viven en
volúmenes (`db-data`, `ia-data`).

> **Aviso: los Dockerfile y el compose no se han podido construir ni probar** (se escribieron en una máquina sin Docker).
> Sí se verificó que `./mvnw package` genera un jar ejecutable y los nombres de las variables de entorno
> (`SOUNDIA_IA_ENGINE_BASE_URL`, `SOUNDIA_CORS_ALLOWED_ORIGINS`, `DB_*`). Si algo falla al construir, empezar por las imágenes base.

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

### Pistas (`/api/v1/tracks`)

Pasarela hacia el motor de IA: **Django es el dueño de los datos de pista** (BPM, tonalidad, energía, puntos de mezcla);
este API solo reenvía y devuelve el JSON sin tocarlo.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/analyze` | `multipart/form-data` con `file` (WAV, FLAC, OGG o MP3; máx. 50 MB) y `title` opcional. Devuelve 201 con la pista analizada. Analizar tarda unos segundos. |
| `GET` | `/` | Pistas analizadas (las 50 más recientes). |
| `GET` | `/{id}` | Una pista. |
| `GET` | `/{id}/audio` | El audio original (con su `Content-Type`), para decodificarlo y reproducirlo en un deck. Se descarga entero (máx. 50 MB). 404 si la pista no tiene audio. |
| `PUT` | `/{id}/grid` | `{downbeat}` (segundos, número ≥ 0). El DJ señala un «1» del compás cuando la detección automática no se puede fiar (reggaetón): el motor de IA lo ajusta al pulso más cercano y lo guarda como primer tiempo, con `grid.confidence` 1 y `grid.manual` verdadero. Devuelve la pista. 400 si el valor no es válido, 404 si la pista no existe, 409 si no tiene rejilla de pulsos. |

Los errores 400, 404, 413 y 422 del motor de IA se devuelven **con su código y su mensaje** (por ejemplo, 422 «El audio es
silencio»); cualquier otro fallo del motor se muestra como un 502 genérico. El análisis usa un timeout propio
(`soundia.ia-engine.analysis-timeout`, 120 s por defecto).

### Auto-DJ (`/api/v1/autodj` y cola de sesión)

La IA recomienda qué pista sigue y cómo mezclarla (el cálculo vive en el motor de IA; este API valida y reenvía).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/v1/autodj/next` | `{trackId, limit?}` (límite 1-20). Pistas que mejor siguen a la indicada, con su puntuación de compatibilidad (tempo, armonía, energía). |
| `POST` | `/api/v1/autodj/transition` | `{fromTrackId, toTrackId}`. Plan de transición: estilo (`blend`, `fade` si los tempos no se igualan, o `cut`), compases, segundo de salida y de entrada, y curvas de crossfader/EQ por pulso. |

| `POST` | `/api/v1/autodj/feedback` | `{fromTrackId, toTrackId, liked}` (`liked` debe ser `true`/`false`). El DJ valora una transición: el motor la guarda y reajusta cuánto pesan tempo, armonía y energía. 201 con `{weights, samples, learned}`. |
| `POST` | `/api/v1/autodj/timing` | `{fromTrackId, toTrackId, plannedMixOutAt, actualMixOutAt}` (segundos de la pista saliente, ≥ 0). El DJ pidió mezclar antes de lo planeado: el motor lo guarda y adelanta sus mezclas futuras. 201 con `{samples, advanceSeconds, learned}`. |
| `GET` | `/api/v1/autodj/weights` | Los pesos de la puntuación vigentes, si son aprendidos y con cuántas valoraciones. |

**Cola de reproducción** de una sesión (`/api/v1/sessions/{sessionId}/queue`). Orden FIFO; máximo 100 pistas; solo en sesiones abiertas.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/` | `{trackId}`. Encola una pista (verifica que exista en el motor de IA). 201; 409 si la sesión está cerrada o la cola llena. |
| `GET` | `/` | La cola en orden de reproducción. |
| `POST` | `/next` | Saca de la cola y devuelve la pista que toca; 404 si está vacía. |
| `DELETE` | `/{itemId}` | Quita un elemento; 204, o 404 si no está en esa sesión. |

### Grabaciones de mezclas (`/api/v1/recordings`)

Para que el sistema aprenda el estilo de un DJ hay que guardar lo que hace al pasar de una pista a otra. Son rutas de
pasarela: los datos y la validación viven en el motor de IA (ver su README para los campos, las curvas y los rasgos).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/` | Guarda una transición: `{fromTrackId, toTrackId, mixOutAt, mixInAt, overlapSeconds, pitchA, pitchB, curves?, ...}`. 201 con la grabación y sus rasgos; 400 si el motor la rechaza o el cuerpo está vacío o pasa de 256 KB. |
| `GET` | `/` | Las más recientes sin curvas, con un resumen por calidad. `?quality=good` filtra (`unreviewed`, `good`, `discard`). |
| `GET` | `/{id}` | Una grabación con sus curvas y la foto de las pistas. |
| `PUT` | `/{id}` | `{quality?, notes?}`: la revisión del DJ. |
| `DELETE` | `/{id}` | La borra (204). |

### Flujo en vivo (SSE)

`GET /api/v1/midi/stream` mantiene la conexión abierta (`text/event-stream`) y envía:

| Evento | Cuerpo | Cuándo |
|---|---|---|
| `midi` | `{"controlId":14,"value":127}` | Al llegar un evento MIDI |
| `decision` | `{"id":1,"controlId":14,"value":127,"status":"success","action":"...","createdIn":"..."}` | Cuando la IA responde y se guarda. Si la IA no responde tras 3 intentos, `status` es `unavailable` y `action` es `null`. |
| *(comentario)* `:conectado` | — | Nada más conectarse: hace salir las cabeceras para que el navegador sepa que la conexión está abierta (sin él tardaría hasta el primer latido, 15 s) |
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
