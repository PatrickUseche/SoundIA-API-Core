# Design Pattern Review Report

## Project Summary

| Field | Value |
|-------|-------|
| Language(s) | Java (Spring Boot, `pom.xml`) |
| Mode | Code Review |
| Scope | `src/main/java/com/soundia` (muestreo de ~20 de ~60 archivos: telemetría, cola, sesión, clientes del motor, auth, config) |
| Design docs reviewed | none (`docs/` solo tiene guías de uso y despliegue) |

---

## Patterns Currently in Use

| Pattern | Category | Location | Assessment |
|---------|----------|----------|------------|
| Observer | Behavioral | `telemetry/pattern/` (`DjControllerReader` + `MidiObserver`, `MidiProcessingObserver`, `LiveStreamObserver`) | Well implemented: el sujeto recibe los observadores como beans, un observador nuevo solo es un `@Component`; lo invoca `MidiTelemetryController` |
| Observer (eventos de Spring) | Behavioral | `MidiEventService` publica `MidiDecisionEvent`; `LiveStreamPublisher` lo escucha | Well implemented, pero es un segundo mecanismo de notificación paralelo al de `DjControllerReader` |
| Chain of Responsibility / Interceptor | Behavioral | `JwtAuthFilter`, `TransientRetryInterceptor` | Well implemented (vienen del framework) |
| Repository | Modern | `*Repository` (Spring Data JPA) | Well implemented |
| DTO | Modern | `autodj/dto`, `queue/dto`, `telemetry/dto` | Well implemented |
| Gateway / Adapter (implícito) | Structural | `IaEngineClient`, `AutoDjClient`, `TrackClient`, `RecordingClient` | Partially applied: aíslan a Django, pero no comparten interfaz y devuelven JSON crudo (`String`) |
| Singleton (por contenedor) | Creational | beans `@Service`/`@Component`, `LoginThrottle` | Partially applied: lo gestiona Spring, no hay singleton explícito |
| Retry / Resilience | Modern | `@Retryable` en `MidiProcessingObserver`, `TransientRetryInterceptor`, bucle propio en `TrackClient` | Partially applied: tres implementaciones distintas del mismo concepto |

---

## Recommended Patterns

| Opportunity | Suggested Pattern | Location | Impact | Priority |
|------------|-------------------|----------|--------|----------|
| `pollNext` asume siempre orden FIFO | Strategy | `queue/service/QueueService.java` | Medium | High |
| `DjSessionLog` con 3 constructores y setters; cuerpos de petición armados a mano con `LinkedHashMap` | Builder | `telemetry/model/DjSessionLog.java`, `autodj/AutoDjClient.java` | Medium | High |
| Mensajes SSE creados en varios sitios (`MidiMessage`, `DecisionMessage`) según el tipo de evento | Factory Method | `telemetry/stream/`, `telemetry/pattern/LiveStreamObserver.java` | Low | Medium |
| Estado compartido en memoria (`LoginThrottle`, registro de emisores, disponibilidad del motor) sin punto único explícito | Singleton | `auth/LoginThrottle.java`, `telemetry/stream/LiveStreamService.java` | Low | Medium |
| `QueueService.add` encadena tres validaciones con `if` | Chain of Responsibility | `queue/service/QueueService.java` | Low | Medium |
| `DjSession.isActive()` es un booleano derivado de `endedAt == null`; los servicios consultan el estado con `if` | State | `session/model/DjSession.java`, `DjSessionService.java`, `QueueService.java` | Low | Low |
| Reintentos duplicados en `TrackClient`, `TransientRetryInterceptor` y `@Retryable` | Decorator / Proxy | `track/TrackClient.java`, `config/TransientRetryInterceptor.java` | Medium | Medium |
| Cuatro clientes del motor sin interfaz común | Adapter / Facade | `*Client.java`, `AutoDjController` | Low | Low |

---

## Detailed Recommendations

### Strategy — `queue/service/QueueService.java`

**Problem:** `pollNext` siempre toma el primero por id. El orden de reproducción está fijo en el repositorio (`findFirstBySessionIdOrderByIdAsc`), así que no se puede cambiar sin tocar servicio y repositorio.

**Why this pattern fits:** el algoritmo de selección es lo que varía (FIFO, el más reciente, aleatorio) mientras el resto del flujo (validar sesión, borrar el elemento) no cambia. Es la definición de Strategy.

**How to apply:** interfaz `QueueOrderStrategy` con `select(List<QueueItem>)`; implementaciones `FifoOrder`, `NewestFirstOrder`, `ShuffleOrder`; `QueueService` recibe la estrategia por parámetro (`?order=`), con FIFO por defecto.

**Trade-offs to consider:** `ShuffleOrder` necesita un `Random` inyectable para poder probarla; hay que cargar la cola de la sesión (máximo 100 elementos), lo que es aceptable.

### Builder — `DjSessionLog` y `AutoDjClient`

**Problem:** `DjSessionLog` tiene constructores solapados `(controlId, value)` y `(controlId, value, action)` más setters para `sessionId`; `AutoDjClient` arma mapas con campos opcionales a mano (`limit` solo si no es null).

**Why this pattern fits:** construcción con campos opcionales y una validación común (valor MIDI 0-127, ids no nulos).

**How to apply:** `DjSessionLog.builder()` con `build()` que valide; un `EngineRequestBuilder` para los cuerpos de `AutoDjClient`.

**Trade-offs to consider:** JPA exige constructor sin argumentos; el builder convive con él. Para objetos de 2-3 campos sería exceso de ceremonia.

### Factory Method — `telemetry/stream/`

**Problem:** el tipo de mensaje SSE (`MidiMessage`, `DecisionMessage`) lo decide cada observador por su cuenta junto con `StreamEventType`.

**Why this pattern fits:** una `StreamMessageFactory.create(type, log, status)` concentra qué mensaje corresponde a cada tipo.

**How to apply:** método estático o bean que reciba el tipo y devuelva la clase de mensaje; los observadores solo piden el mensaje.

**Trade-offs to consider:** con solo dos tipos la ganancia es pequeña; se justifica sobre todo si se añaden más tipos de evento.

### Singleton — estado en memoria

**Problem:** `LoginThrottle` y el registro de emisores SSE son singleton solo por la configuración de Spring; no hay un componente explícito para, por ejemplo, saber si el motor está disponible o dormido.

**Why this pattern fits:** es estado único que debe ser thread-safe (hoy se resuelve con `synchronized` y `CopyOnWriteArraySet`).

**How to apply:** `EngineStatus` con instancia única y creación perezosa segura (holder idiom o `enum`).

**Trade-offs to consider:** el singleton explícito dificulta las pruebas; el propio repo ya prefiere inyección (`Clock` inyectado). Exponerlo también como bean.

### Chain of Responsibility — validaciones de `QueueService.add`

**Problem:** sesión abierta, cola no llena y pista existente se comprueban en secuencia dentro del mismo método.

**How to apply:** `AddToQueueValidator` con `validate(context)` y encadenado; el orden se define en un solo lugar.

**Trade-offs to consider:** son solo 3 comprobaciones; la cadena añade clases a cambio de poder reordenar o ampliar.

### Decorator / Proxy — reintentos duplicados

**Problem:** el reintento por motor dormido existe en `TransientRetryInterceptor` (solo métodos idempotentes), en `TrackClient.analyze` (POST con espera previa) y en `@Retryable` del observador.

**How to apply:** un decorador sobre el cliente que concentre la política, o reutilizar `TransientRetryInterceptor` con una opción para POST idempotentes.

**Trade-offs to consider:** las políticas difieren a propósito (un POST puede duplicar datos); unificar sin perder esa distinción exige cuidado.

---

## Anti-Patterns Observed

| Anti-Pattern | Location | Issue | Recommended Alternative |
|-------------|----------|-------|------------------------|
| Duplicación de política de reintentos | `TrackClient`, `TransientRetryInterceptor` | La misma lógica (estados 502/503/504, `ConnectException`, espera creciente) escrita dos veces | Extraer una política común |
| Respuestas como `String` JSON | `AutoDjClient`, `TrackClient` | El resto de la API pasa JSON sin tipar; cualquier cambio del motor llega al cliente sin validar | DTOs tipados o un Adapter que traduzca |
| Dos mecanismos de notificación | `DjControllerReader` y `ApplicationEventPublisher` | Un evento MIDI se notifica por un camino y la decisión por otro | Documentar la razón o unificar |

---

## Summary

- **Patterns in use:** 8
- **New opportunities identified:** 8
- **High priority recommendations:** 2
- **Anti-patterns observed:** 3

> La arquitectura ya usa bien Observer, Repository y DTO. Las oportunidades de mayor valor y menor riesgo son Strategy en la cola y Builder en los logs y las peticiones al motor; coinciden con el plan de la materia (Observer, Strategy, Builder, Factory Method, Singleton).

---

## Machine-Parsable Summary

```json
{
  "review_date": "2026-10-27",
  "mode": "code",
  "languages": ["java"],
  "scope": "src/main/java/com/soundia",
  "patterns_in_use": [
    {"pattern": "Observer", "category": "behavioral", "location": "telemetry/pattern", "assessment": "well_implemented"},
    {"pattern": "Chain of Responsibility", "category": "behavioral", "location": "auth/JwtAuthFilter, config/TransientRetryInterceptor", "assessment": "well_implemented"},
    {"pattern": "Repository", "category": "modern", "location": "*/repository", "assessment": "well_implemented"},
    {"pattern": "Adapter", "category": "structural", "location": "*Client.java", "assessment": "partially_applied"},
    {"pattern": "Singleton", "category": "creational", "location": "beans de Spring", "assessment": "partially_applied"}
  ],
  "recommendations": [
    {"opportunity": "Orden de la cola fijo en FIFO", "pattern": "Strategy", "location": "queue/service/QueueService.java", "impact": "medium", "priority": "high"},
    {"opportunity": "Constructores solapados y mapas manuales", "pattern": "Builder", "location": "DjSessionLog, AutoDjClient", "impact": "medium", "priority": "high"},
    {"opportunity": "Mensajes SSE por tipo", "pattern": "Factory Method", "location": "telemetry/stream", "impact": "low", "priority": "medium"},
    {"opportunity": "Estado único en memoria", "pattern": "Singleton", "location": "LoginThrottle, LiveStreamService", "impact": "low", "priority": "medium"},
    {"opportunity": "Validaciones encadenadas", "pattern": "Chain of Responsibility", "location": "QueueService.add", "impact": "low", "priority": "medium"}
  ],
  "anti_patterns": [
    {"anti_pattern": "Duplicated retry policy", "location": "TrackClient, TransientRetryInterceptor", "issue": "Misma lógica escrita dos veces"},
    {"anti_pattern": "Untyped JSON strings", "location": "AutoDjClient, TrackClient", "issue": "Sin validación de la respuesta del motor"}
  ],
  "counts": {"patterns_in_use": 8, "opportunities": 8, "high_priority": 2, "anti_patterns": 3}
}
```
