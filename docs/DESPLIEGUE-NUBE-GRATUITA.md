# Despliegue en la nube gratuita (sin tarjeta)

Para que lo prueben unas pocas personas sin tener un computador encendido ni pagar un servidor. Cada pieza va en una
plataforma distinta:

| Pieza | Plataforma | Qué guarda |
|---|---|---|
| Frontend (Angular) | Cloudflare Pages | Nada: son archivos estáticos |
| API Gateway (Spring) | Render (plan gratuito) | Nada: usa Neon |
| Motor de IA (Django) | Render (plan gratuito) | Nada: usa Neon y Supabase |
| Bases de datos (2) | Neon | Datos del API y análisis de las pistas |
| Audios | Supabase Storage | Los archivos de audio subidos |

> **Nada de esto se ha probado contra las plataformas reales.** Los planes gratuitos cambian: revisa sus límites antes de
> empezar. Si algo falla, copia el log del servicio (Render > Logs) y revisa «Si algo falla» al final.

## Límites que debes conocer

- Los servicios de Render **se duermen** tras 15 minutos sin tráfico. La primera petición después tarda cerca de un
  minuto (hay que despertar los dos). Antes de una sesión, abre las URLs del API y del motor para calentarlos.
- El disco de Render se borra en cada reinicio: por eso las bases y los audios viven fuera, en Neon y Supabase.
- 512 MB de RAM por servicio: el análisis de voz va apagado y las pistas muy largas pueden quedarse sin memoria.
- Supabase **pausa** los proyectos gratuitos tras una semana sin actividad (se reactivan desde su panel).
- Todos comparten un solo usuario y una sola contraseña (el login del API).

## 1. Neon (bases de datos)

1. Crea una cuenta en [neon.tech](https://neon.tech) y un proyecto nuevo.
2. Crea **dos bases de datos** dentro del proyecto: `soundia_api` (para Spring) y `soundia_motor` (para Django). Así sus
   tablas nunca chocan.
3. Copia el servidor, el usuario y la contraseña. Usa la conexión **directa** (sin `-pooler` en el nombre del servidor).
4. Con eso arma dos valores:
   - Para Django: `postgresql://USUARIO:CLAVE@SERVIDOR/soundia_motor?sslmode=require` (será `DATABASE_URL`).
   - Para Spring: `jdbc:postgresql://SERVIDOR/soundia_api?sslmode=require` (será `DB_URL`), con `DB_USERNAME` y `DB_PASSWORD` aparte.

## 2. Supabase (audios)

1. Crea una cuenta en [supabase.com](https://supabase.com) y un proyecto nuevo (elige la región más cercana).
2. Storage > New bucket: llámalo `audios` y déjalo **privado** (el motor sirve el audio, no el bucket).
3. Project Settings > Storage > S3 Connection: copia el **Endpoint** (termina en `/storage/v1/s3`) y la **Region**, y crea un
   **Access key**: guarda el *Access key ID* y el *Secret access key* (solo se muestra una vez).

## 3. Render: el motor de IA (Django)

Primero genera una **clave compartida** larga y aleatoria (por ejemplo `openssl rand -base64 36`, o cualquier cadena de 40
caracteres). La usarás dos veces: aquí y en el API.

1. En [render.com](https://render.com), «New > Blueprint», conecta GitHub y elige el repo `Soundia-IA-Engine`. Render lee su
   `render.yaml`.
2. Rellena las variables que pide:

   | Variable | Valor |
   |---|---|
   | `SERVICE_TOKEN` | la clave compartida |
   | `DATABASE_URL` | la de Django (paso 1) |
   | `S3_BUCKET` | `audios` |
   | `S3_ENDPOINT_URL` | el endpoint de Supabase |
   | `S3_ACCESS_KEY_ID` / `S3_SECRET_ACCESS_KEY` | las claves de Supabase |
   | `S3_REGION` | la región de Supabase |

3. Espera a que termine el despliegue y anota la URL (`https://soundia-motor.onrender.com`, o la que te dé Render). Abrirla
   en el navegador debe responder `401`: es lo correcto, falta la clave.

## 4. Render: el API Gateway (Spring)

1. «New > Blueprint» con el repo `SoundIA-API-Core`.
2. Rellena:

   | Variable | Valor |
   |---|---|
   | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | los de Spring (paso 1) |
   | `SOUNDIA_IA_ENGINE_BASE_URL` | la URL del motor del paso 3 (sin barra final) |
   | `SOUNDIA_IA_ENGINE_KEY` | la **misma** clave compartida |
   | `SOUNDIA_AUTH_USERNAME` / `SOUNDIA_AUTH_PASSWORD` | el usuario y la contraseña con que entrarán los testers (larga) |
   | `SOUNDIA_CORS_ALLOWED_ORIGINS` | déjala vacía por ahora: se completa en el paso 6 |

3. Anota su URL (`https://soundia-api.onrender.com`). El API completo es esa URL más `/api/v1`.

## 5. Cloudflare Pages: el frontend

1. En [dash.cloudflare.com](https://dash.cloudflare.com), Workers & Pages > Create > Pages > Connect to Git, y elige
   `SoundIA-Web-DJ`.
2. Configuración de build:
   - Comando de build: `npm ci && npm run build:pages`
   - Directorio de salida: `dist/soundia-web-dj/browser`
3. Variables de entorno: `NODE_VERSION` = `24` y `SOUNDIA_API_URL` = `https://soundia-api.onrender.com/api/v1` (la URL del
   paso 4 más `/api/v1`).
4. Despliega y anota la dirección (`https://soundia-xxx.pages.dev`).

## 6. Cerrar el círculo (CORS)

En Render, servicio del API > Environment: `SOUNDIA_CORS_ALLOWED_ORIGINS` = la dirección de Pages, con `https://` y sin
barra final. Guarda; Render reinicia el servicio.

## 7. Probar

1. Abre las URLs del motor y del API para despertarlos (espera hasta un minuto).
2. Abre la dirección de Pages, entra con el usuario y la contraseña y sube una pista de 3 a 5 minutos.
3. Comprueba que la pista aparece analizada, que suena en el deck y que sigue ahí tras reiniciar el motor (Render >
   Manual Deploy). Si desaparece, el audio no está yendo a Supabase.
4. Pasa la dirección, el usuario y la contraseña a los testers.

## Si algo falla

- **El login da error de red / CORS:** revisa `SOUNDIA_CORS_ALLOWED_ORIGINS` (paso 6) y que `SOUNDIA_API_URL` termine en `/api/v1`.
- **El API arranca pero las pistas fallan con 502/401:** `SOUNDIA_IA_ENGINE_KEY` y `SERVICE_TOKEN` deben ser idénticas, y la URL del
  motor no debe terminar en `/`.
- **El motor no arranca:** Render > Logs. Con `S3_BUCKET` definido faltan variables `S3_*` si el log lo dice; con `DATABASE_URL`
  revisa que acabe en `?sslmode=require`.
- **Se queda sin memoria al analizar:** prueba con una pista más corta; el plan gratuito da 512 MB.
- **Tarda mucho la primera vez:** es normal, los servicios estaban dormidos.
