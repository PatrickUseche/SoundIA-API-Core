# TASKS — SoundIA API Core (Spring Boot)

Plan de mejoras del API Gateway. Cada task es un commit; el mensaje del commit es el texto de la task con su sufijo `[x.y]`.
Al terminarla se marca `[x]` en ese mismo commit. Las fases se pueden hacer en orden o intercalar con los otros repos.

## Fase 1 — Estabilizar lo existente

- [x] fix(deps): reemplazar spring-boot-starter-aop 3.2.0 por spring-boot-starter-aspectj gestionado por Boot 4.1 [1.1]
- [x] feat(validation): validar DjSessionLogDto (campos no nulos, valor MIDI entre 0 y 127) [1.2]
- [x] fix(telemetry): unificar el flujo de /event y /ai/predict y guardar la decisión de la IA [1.3]
- [x] refactor(config): mover credenciales de PostgreSQL a variables de entorno [1.4]
- [x] refactor(config): bean de RestTemplate con timeouts y URL de Django configurable [1.5]
- [x] feat(errors): manejo global de errores con @ControllerAdvice [1.6]
- [x] test(telemetry): pruebas unitarias de observers y controlador, y contextLoads sin PostgreSQL real [1.7]
- [x] chore: limpiar pom.xml (metadatos vacíos e indentación) [1.9]

## Fase 2 — Tiempo real

- [x] feat(stream): endpoint SSE (GET /api/v1/midi/stream) que emite eventos MIDI y decisiones de la IA [2.1]
- [x] feat(observer): observador que publica cada evento MIDI y cada decisión al canal en vivo [2.2]
- [x] feat(cors): configurar los orígenes permitidos por propiedad [2.3]

## Fase 3 — Sesiones y biblioteca de pistas

- [x] feat(session): entidad DjSession para agrupar eventos por sesión [3.1]
- [x] feat(tracks): consulta de pistas (BPM, tonalidad, energía) como pasarela hacia el motor de IA, que es el dueño de los datos [3.2]
- [x] feat(tracks): endpoint de subida de audio y reenvío al motor de análisis [3.3]
- [x] feat(tracks): PUT /tracks/{id}/grid para fijar a mano el primer tiempo de una pista, reenviado al motor de IA [3.4]
- [x] feat(recordings): rutas /api/v1/recordings que reenvían al motor de IA las grabaciones de mezclas, para aprender el estilo de un DJ [3.5]

## Fase 4 — Auto-DJ

- [x] feat(autodj): endpoint que pide al motor la siguiente pista y la transición [4.1]
- [x] feat(autodj): cola de reproducción por sesión [4.2]
- [x] feat(autodj): valoración de transiciones (me gustó / no me gustó) reenviada al motor, que aprende sus pesos [4.3]

## Fase 5 — Despliegue y seguridad

- [x] feat(security): autenticación con un usuario DJ y JWT, apagada por defecto (SOUNDIA_AUTH_ENABLED); el front se adapta en su repositorio [5.1] (filtro propio con Nimbus + BCrypt, no Spring Security: éste rompería las pruebas de los controladores)
- [x] chore(docker): Dockerfile y docker-compose con PostgreSQL, Django y Spring [5.2] (escritos pero sin probar: no hay Docker en la máquina de desarrollo)
- [x] chore(ci): pipeline de GitHub Actions con build y tests [5.3]
- [ ] chore(db): reemplazar ddl-auto=update por migraciones con Flyway (V1 esquema actual, V2 columna action) [5.4]

## Fase 6 — Pendiente

- [ ] test(docker): construir y probar el Dockerfile y el docker-compose en una máquina con Docker y PostgreSQL [6.1]
- [ ] chore(security): quitar la contraseña antigua del historial de git, o rotarla (sigue en commits antiguos) [6.2]
- [ ] test(integration): probar el flujo completo contra Django y la web reales, no solo con dobles de prueba [6.3]

## Fase 7 — Despliegue en el navegador (pendiente)

Encontrado al probar el sistema completo en un navegador real (2026-10-22); nada de esto se ha hecho todavía.

- [x] chore(deploy): encender `SOUNDIA_AUTH_ENABLED` con una contraseña larga y una clave de firma aleatoria de 32 o más caracteres, y poner `soundia.cors.allowed-origins` en el dominio real del front [7.1]
- [x] chore(deploy): detrás de un proxy inverso el límite de intentos de login ve la dirección del proxy y todos los clientes comparten el contador: configurar las cabeceras reenviadas de confianza (`server.forward-headers-strategy`) o limitarlo en el proxy [7.2]
- [x] chore(deploy): el motor de IA (Django) no tiene autenticación: dejarlo solo en la red interna, sin puerto publicado, de modo que únicamente este API lo alcance [7.3]

## Fase 9 — Nube gratuita sin tarjeta (en curso)

Arquitectura acordada con el usuario (2026-10-24): Angular en Cloudflare Pages, este API y Django en Render (gratis, se duermen y su disco se borra), base de datos en Neon (Postgres) y audios de Django en Supabase Storage (S3). Nada de esto se ha probado contra las plataformas reales.

- [x] feat(security): enviar al motor de IA la clave compartida `SOUNDIA_IA_ENGINE_KEY` (cabecera `X-Soundia-Key`) [9.1]
- [x] chore(deploy): `render.yaml`, escuchar en `$PORT` y limitar la memoria de la JVM para 512 MB; PostgreSQL de Neon con `DB_URL` [9.2]
- [x] docs(deploy): guía paso a paso de la nube gratuita (Neon, Supabase, Render, Cloudflare Pages) [9.3]

## Fase 10 — Aprender desde la nube con el repertorio del DJ (en curso)

Acordado con el usuario (2026-10-24): el DJ usa la versión de la nube, por sesiones (sube las pistas de la sesión, mezcla con el modo aprendizaje y borra las pistas al terminar; las grabaciones se conservan). El almacenamiento gratuito de Supabase es de 1 GB.

- [x] feat(tracks): `DELETE /api/v1/tracks/{id}`: borrar una pista y su audio, y quitarla de las colas [10.1]
- [x] fix(tracks): despertar al motor de IA antes de subir una pista y reintentar si responde 502, 503 o 504, porque en la nube gratuita se duerme y el proxy cortaba la subida [9.6]
- [x] feat(recordings): `GET /api/v1/recordings/export` y `POST /api/v1/recordings/import` (copia de seguridad de las grabaciones) [10.4]
- [x] feat(tracks): `PUT /api/v1/tracks/{id}` para poner el género de una pista [10.5]
- [x] fix(deploy): reintentar las consultas idempotentes al motor de IA (GET, PUT, DELETE) si responde 502, 503 o 504 mientras despierta o se redespliega [9.7]
- [x] fix(tracks): la subida de una pista reintenta hasta 5 veces con una espera que crece (5, 10, 15 y 20 s), para sobrevivir al reinicio del motor [9.8]
- [x] docs(user): guía de una página para el DJ (subir, mezclar, grabar, revisar y qué hacer si algo falla) [11.7]
- [x] chore(deploy): `scripts/verificar-nube.ps1`, que despierta los servidores y comprueba API, login, motor y biblioteca antes de una sesión; los avisos pasan a «hasta tres minutos» (medido: el API tarda ~135 s en despertar) [11.11]

## Fase 12 — Patrones de diseño (materia Patrones de Software)

Acordado con el usuario (2026-10-27): implementar 5 patrones en la lógica del backend Java, un PR por patrón. Revisión previa en `docs/review/2026-10-27-soundia-api-core-pattern-review.md`.

- [x] docs(review): informe de patrones de diseño del API Core [12.1]
- [x] docs(patterns): Observer ya existe en `telemetry/pattern/` (`DjControllerReader` + `MidiObserver`); solo se documenta, junto con los otros cuatro patrones y sus diagramas en `docs/PATRONES-DE-DISENO.md` [12.2]
- [x] feat(queue): Strategy: orden de la cola (`QueueOrderStrategy`: FIFO, más reciente primero, aleatorio) elegible con `?order=` [12.3]
- [x] refactor(telemetry): Builder para `DjSessionLog` y los cuerpos de petición de `AutoDjClient` [12.4]
- [x] feat(stream): Factory Method para los mensajes SSE [12.5]
- [x] feat(engine): Singleton explícito y thread-safe con el estado del motor de IA (`EngineStatus`) [12.6]
- [x] feat(engine): `EngineStatus.track(...)`: `AutoDjClient` y `TrackClient` también informan del estado del motor (un 4xx cuenta como motor arriba, un 5xx o una conexión caída como motor caído) [12.7]
- [x] feat(autodj): rutas `GET` y `POST /api/v1/autodj/style-model` para ver y encender o apagar el modelo del estilo del DJ del motor [12.8]
- [x] feat(autodj): ruta `POST /api/v1/autodj/choices` para registrar lo que el DJ eligió como siguiente pista frente a lo que proponía la IA [12.9]

## Fase 13 — Rendimiento y usabilidad

Revisión del 2026-10-27 con las skills `lemmaly` (complejidad) y `ui-ux-pro-max`.

- [x] perf(telemetry): índices en `dj_session_logs` (`created_in` y `session_id, created_in`): las consultas de los últimos 50 movimientos y de los eventos de una sesión pasan de O(N) a O(log N + k) [13.1]
- [x] perf(tracks): `Cache-Control: private, max-age=3600` en `GET /tracks/{id}/audio`, para que el navegador no vuelva a bajar ni a despertar al motor por una pista ya cargada [13.2]

