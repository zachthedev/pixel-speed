# Política de privacidad

Pixel Speed no recopila datos.

## Qué lee la app

La app lee los contadores de tráfico de red del propio teléfono a través de la interfaz `TrafficStats`
de Android, calcula una velocidad a partir de ellos y la dibuja en pantalla. Los contadores nunca
salen del teléfono.

Con el cambio de lado activado, un servicio de accesibilidad lee la ventana de la barra de estado: qué
iconos muestra y dónde están. No lee ninguna otra ventana ni contenido. Eso también se queda en el
teléfono.

Android también avisa al servicio cuando cambian las ventanas en pantalla, incluidas las de otras
aplicaciones. Esa señal solo le indica cuándo volver a mirar la barra de estado. De cualquier
ventana en la parte superior de la pantalla lee solo a qué aplicación pertenece, para distinguir la
barra de estado. No lee contenido de ninguna ventana salvo la barra de estado.

La app no declara ningún permiso de red, así que no puede enviar nada a ningún sitio. No contiene
analítica, ni servicio de informes de fallos, ni publicidad, ni ningún SDK de datos de terceros.

## Qué guarda la app

Tus ajustes (perfiles, posición, apariencia, comportamiento, estado de pausa) se guardan en un archivo
dentro del almacenamiento privado de la app en el teléfono. La copia de seguridad de Android puede
incluir ese archivo según los ajustes de copia de tu dispositivo. Nada en él te identifica.

Exportar escribe tus ajustes en un archivo que eliges. Importar lee un archivo que eliges. Ambos
ocurren solo cuando tú los eliges.

Si la app falla, guarda un registro de ese fallo en su almacenamiento privado: la hora, la versión
de la app, la traza de la pila y las últimas líneas de registro de la propia app. Ayuda > Informar
de un problema crea un archivo de diagnóstico cuando lo pides: la versión de la app, el modelo del
teléfono y la versión de Android, la geometría de la pantalla, qué permisos están concedidos, el
estado de la lectura, tu exportación de ajustes, las últimas líneas de registro de la app y ese
registro de fallo si existe. No contiene ninguna cuenta, id de dispositivo, número de serie ni
ubicación, y nada de otras apps. La app nunca lo envía: lo lees y luego lo compartes tú mismo si
quieres.

## Permisos

Cada permiso que declara la app y para qué sirve:

- Mostrar sobre otras apps (SYSTEM_ALERT_WINDOW): dibuja la lectura.
- Notificaciones (POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS): la notificación del servicio en
  primer plano que lleva pausar y reanudar, y su forma Live Update en Android 16 y posteriores.
- Servicio en primer plano (FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE): mantiene la lectura en
  marcha mientras la superposición está activada.
- Servicio de accesibilidad (BIND_ACCESSIBILITY_SERVICE): opcional, desactivado por defecto. Lee los
  iconos de la barra de estado y su posición para que la lectura pueda moverse al lado de la cámara
  con espacio. Nada más.
- Estado de la red (ACCESS_NETWORK_STATE): indica si el teléfono está conectado y qué enlace
  está activo, para que la lectura muestre guiones sin conexión y cuente la interfaz correcta.
  No es acceso a internet.
- Ejecutar al iniciar (RECEIVE_BOOT_COMPLETED): inicia la superposición tras un reinicio cuando lo
  activas.
- Ignorar optimizaciones de batería (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS): se ofrece en teléfonos cuyo
  fabricante cierra las apps en segundo plano, para que la superposición siga en marcha. Opcional.

No hay permiso de red.

## Contacto

Preguntas sobre esta política: abre un issue en https://github.com/zachthedev/pixel-speed/issues.

Esta política se aplica a Pixel Speed desde la versión 1.0.0. Los cambios se publican en este archivo.
