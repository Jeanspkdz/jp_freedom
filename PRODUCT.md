# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Stack

Android nativo en Kotlin; extensión de Chrome Manifest V3; API de sincronización en Node.js con Neon Postgres. La distribución personal de Android es mediante instalación de APK.

## Users

Una persona que quiere mejorar sus hábitos de estudio y reducir distracciones en su teléfono Android y en Chrome de escritorio.

## Product Purpose

Tally ayuda al usuario a planificar cada día y cumplir bloques de enfoque en Android. Chrome bloquea los sitios seleccionados durante bloques planificados y sesiones de estudio con temporizador. El usuario puede iniciar una sesión con duración elegida desde Chrome; Android aplica las restricciones del teléfono cuando recibe esa sesión. Si no planifica el día siguiente antes de la hora elegida, restringe el uso del teléfono hasta completar el mínimo de bloques configurado. El éxito significa que la planificación y el bloqueo entre dispositivos apoyan una rutina de estudio que el usuario puede sostener.

## Positioning

Tally es una app personal y de código abierto para planificar cada día como rutina y aplicar bloqueos según el plan. Android es la fuente de verdad para planes, ajustes y estados de finalización; la extensión de Chrome aplica el bloqueo de sitios de escritorio compartido y permite editar esa lista.

## Brand Commitments

- Conservar una apariencia medio oscura y la familia cromática actual; dar más viveza e intensidad a los colores existentes sin reemplazarlos por otra paleta.

## Operating Context

- El usuario planifica hoy y mañana desde Android, y puede ajustar sus planes durante el día.
- Cada bloque tiene una actividad, horas de inicio y fin, y un modo de bloqueo del celular.
- El usuario marca los bloques como Finalizado, Parcial o No realizado. Marcar Finalizado termina el bloqueo activo de ese bloque.
- El domingo es libre de bloques. El domingo por la noche se debe planificar el lunes.
- La extensión de Chrome sincroniza planes y sesiones para aplicar el bloqueo de sitios durante bloques planificados y temporizadores de estudio; no aplica la restricción nocturna.
- Android y Chrome sincronizan una lista compartida de sitios bloqueados. La extensión puede editar esa lista, pero solo Android puede editar planes, ajustes y estados de finalización.
- Chrome permite elegir cualquier duración e iniciar una sesión de estudio compartida. Android usa el perfil Solo esenciales para las sesiones iniciadas desde Chrome. La creación de bloques, las revisiones, los demás ajustes y los permisos del teléfono permanecen en Android.
- La primera distribución de Android y Chrome es personal y de instalación manual. La sincronización usa una API conectada a Neon y es de un solo usuario; no hay cuentas multiusuario.
- Los dispositivos usan su hora local y, en la entrega inicial, se espera que compartan zona horaria.

## Capabilities and Constraints

- Los modos de bloqueo del celular son: esenciales; esenciales más ChatGPT; esenciales más ChatGPT y Google Search.
- Llamadas, mensajes, funciones de emergencia y el planificador permanecen disponibles durante la restricción nocturna.
- El mínimo de bloques requeridos es configurable y empieza en tres.
- La hora de corte se puede elegir entre 9, 10 y 11 p.m.; empieza en 9 p.m.
- Los bloques no pueden superponerse. No se requiere duración mínima ni descripción de resultado.
- La planificación y la decisión de restricción deben funcionar sin internet en el teléfono.
- Chrome necesita internet para editar la lista compartida y sincronizar cambios; sin conexión conserva y aplica la última lista sincronizada válida.
- Chrome no restringe la navegación por la noche. El bloqueo de escritorio se aplica a la navegación de nivel superior durante bloques planificados y sesiones de estudio con temporizador.
- Hasta recibir una primera instantánea válida, la extensión no bloquea sitios y muestra que la sincronización no está activa.
- La extensión envía recordatorios de planificación cuando falta el plan requerido y Chrome está abierto; el recordatorio no restringe la navegación.
- El usuario gestiona por separado el administrador del dispositivo que ayuda a evitar la desinstalación o desactivación del bloqueo.
- La lista inicial de escritorio incluye Facebook, Instagram, TikTok, X y Reddit; YouTube y WhatsApp Web quedan disponibles salvo que el usuario los seleccione.
- El mecanismo de filtrado de apps y sitios en Android aún debe validarse en el dispositivo real del usuario.
- El nombre comercial y la identidad visual del producto siguen sin decidir.

## Evidence on Hand

- La especificación aprobada está en el issue #1 de GitHub y desglosada en sus tickets secundarios.
- El glosario del repositorio registra el vocabulario actual del producto en `GLOSSARY.md`.
- El contrato operativo de la extensión y la sincronización está en `docs/sync-and-extension.md`.
- El comportamiento acordado para el temporizador de Chrome está en `docs/specs/chrome-study-session.md`.
- No se han proporcionado testimonios, benchmarks, diseños, ni otros materiales de marca. No inventarlos.

## Product Principles

- Planificar cada día debe ser sencillo y adaptable.
- Android es la fuente de verdad para planes y estados; la sincronización debe preservar cambios concurrentes sin sobrescribirlos silenciosamente.
- El bloqueo sigue el plan y continúa sin conexión con la última política válida recibida por cada dispositivo.
- La planificación permanece accesible durante la restricción, y los bloqueos y la liberación anticipada deben ser explícitos.
- Mantener el alcance personal y de código abierto; no asumir necesidades de equipos o de una empresa comercial.
