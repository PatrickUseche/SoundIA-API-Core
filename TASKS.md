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
