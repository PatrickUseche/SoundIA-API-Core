# TASKS — SoundIA API Core (Spring Boot)

Plan de mejoras del API Gateway. Cada task es un commit; el mensaje del commit es el texto de la task con su sufijo `[x.y]`.
Al terminarla se marca `[x]` en ese mismo commit. Las fases se pueden hacer en orden o intercalar con los otros repos.

## Fase 1 — Estabilizar lo existente

- [x] fix(deps): reemplazar spring-boot-starter-aop 3.2.0 por spring-boot-starter-aspectj gestionado por Boot 4.1 [1.1]
- [ ] feat(validation): validar DjSessionLogDto (campos no nulos, valor MIDI entre 0 y 127) [1.2]
- [ ] fix(telemetry): unificar el flujo de /event y /ai/predict y guardar la decisión de la IA [1.3]
- [ ] refactor(config): mover credenciales de PostgreSQL a variables de entorno [1.4]
- [ ] refactor(config): bean de RestTemplate con timeouts y URL de Django configurable [1.5]
- [ ] feat(errors): manejo global de errores con @ControllerAdvice [1.6]
- [ ] test(telemetry): pruebas unitarias de observers y controlador, y contextLoads sin PostgreSQL real [1.7]
- [ ] chore(db): reemplazar ddl-auto=update por migraciones con Flyway [1.8]
- [ ] chore: limpiar pom.xml (metadatos vacíos e indentación) [1.9]

## Fase 2 — Tiempo real

- [ ] feat(stream): endpoint SSE o WebSocket que emita eventos MIDI y decisiones de la IA [2.1]
- [ ] feat(observer): nuevo observador que publique cada evento al canal en vivo [2.2]
- [ ] feat(cors): configurar origen del frontend por propiedad según perfil [2.3]

## Fase 3 — Sesiones y biblioteca de pistas

- [ ] feat(session): entidad DjSession para agrupar eventos por sesión [3.1]
- [ ] feat(tracks): entidad Track y CRUD con BPM, tonalidad y energía [3.2]
- [ ] feat(tracks): endpoint de subida de audio y reenvío al motor de análisis [3.3]

## Fase 4 — Auto-DJ

- [ ] feat(autodj): endpoint que pide al motor la siguiente pista y la transición [4.1]
- [ ] feat(autodj): cola de reproducción por sesión [4.2]

## Fase 5 — Despliegue y seguridad

- [ ] feat(security): autenticación con Spring Security y JWT [5.1]
- [ ] chore(docker): Dockerfile y docker-compose con PostgreSQL, Django y Spring [5.2]
- [ ] chore(ci): pipeline de GitHub Actions con build y tests [5.3]
