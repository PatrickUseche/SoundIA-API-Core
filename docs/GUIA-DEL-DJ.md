# Guía del DJ: cómo usar SoundIA

Esta guía es para quien va a mezclar con SoundIA y, de paso, enseñarle su estilo a la IA. Dura una página.

## Antes de empezar (5 minutos, mejor en casa)

1. **Abre la dirección de la aplicación** en Chrome o Edge (en el portátil). Entra con el usuario y la contraseña que te dieron.
2. **Espera a ver «Servidores listos»** arriba a la derecha. Si lleva un rato sin usarse, los servidores están dormidos y pueden tardar hasta tres minutos en despertar: aparece un aviso amarillo. **No cierres la página.**
3. **Sube tus canciones de la sesión** (ver abajo) antes de ir a tocar. Subirlas delante del público es mala idea: cada una tarda unos segundos.

### Comprobar que todo está listo (opcional, recomendable)

Quien administra SoundIA puede ejecutar `scripts/verificar-nube.ps1` en PowerShell: despierta los servidores y comprueba en un minuto el API, el login, el motor de IA y la biblioteca. Si dice «Todo listo», se puede ir a tocar.

## 1. Subir canciones

- En **Biblioteca**, «Analizar pistas nuevas»: puedes elegir **varias a la vez**. Se analizan de una en una; verás «Analizando la pista 2 de 5».
- Usa **MP3** (o WAV/FLAC/OGG). Lo ideal: **menos de 12 MB** por canción. Más de 50 MB no se aceptan.
- Al terminar dice «5 pistas analizadas». Si alguna falla, la lista de abajo explica por qué; vuelve a subir solo esa.
- La biblioteca guarda unas **100 canciones** (1 GB). Al terminar la sesión puedes **borrar** las que no necesites con el botón «Borrar» de cada fila: libera espacio y **no pierdes tus grabaciones**.

## 2. Poner el género

En la columna **Género** de cada canción escribe merengue, bachata, salsa, despecho, discobar… (sale una lista de sugerencias). Se guarda solo al salir del campo. Ayuda a la IA a entender tu estilo en cada ritmo.

## 3. Mezclar

1. En la biblioteca pulsa **Deck A** en una canción y **Deck B** en otra. Cada deck tiene su color (A cian, B magenta).
2. **Play** arranca la canción. **Marcar cue** guarda un punto y **Ir al cue** vuelve a él.
3. **Sync** iguala el tempo de este deck al del otro. El deslizador **Pitch** lo ajusta a mano.
4. Mueve el **Crossfader** (centro) y los faders de **volumen y ecualizador** (graves, medios, agudos) de cada deck.
5. Si el «1» del compás está mal detectado, **Marcar el 1** fija el primer tiempo donde estás ahora.
6. ¿Prefieres el Auto-DJ? Actívalo en su panel; elige la siguiente canción y mezcla solo. Puedes forzarlo con **Mezclar ahora**.

## 4. Enseñarle tu estilo a la IA (lo más importante)

1. Antes de mezclar, enciende **«Grabar mis mezclas»** en el panel **Aprendizaje**.
2. Mezcla **a mano**: pon una canción, deja que suene y entra con la siguiente. Cuando terminas, aparece «Mezcla grabada: N compases de solape».
3. **No se graba audio**, solo cuándo entra la canción, cuánto solapan y cómo mueves crossfader y EQ. Las mezclas del Auto-DJ no se graban.
4. Si el servidor no responde en ese momento, la mezcla **no se pierde**: queda pendiente y se envía sola. **No cierres la página** hasta que desaparezca el aviso de pendientes.

## 5. Revisar tus mezclas

En **Grabaciones** (menú de arriba) verás cada mezcla. Márcala **Buena** o **Descartar** (solo las buenas se usarán para entrenar) y escribe una nota si quieres («entró tarde», «esta me encantó»). Usa **Descargar copia** al final de la sesión: es tu copia de seguridad.

## Si algo pasa

| Lo que ves | Qué hacer |
|---|---|
| Aviso amarillo «Esto está tardando más de lo normal» | Los servidores despiertan. Espera hasta 3 minutos. No recargues. |
| «Sin respuesta del servidor» | Espera un minuto; la app reintenta sola. Si sigue, recarga la página. |
| «N mezclas esperan a guardarse» | Normal si el servidor estaba dormido. Se guardan solas; no cierres la página. |
| Una canción no se analiza | Mira el motivo en rojo. Suele ser un formato raro o un archivo muy grande. |
| «La sesión caducó» | Vuelve a entrar con tu usuario y contraseña. Las mezclas pendientes se guardan después. |
| La música se corta o suena mal | Recarga la página y vuelve a cargar las canciones en los decks. Avísanos qué pasó. |

## Importante

- **La música suena en tu navegador**, no en el servidor: una vez cargadas las canciones en los decks, aunque el servidor se duerma, sigues mezclando. Lo que necesita el servidor es cargar canciones nuevas y guardar tus mezclas.
- Usa **Chrome o Edge** actualizados y conecta el audio **antes** de abrir la página.
- Esto es una **versión de prueba**: cuéntanos todo lo que se sienta raro o incómodo.
