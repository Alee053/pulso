# PUL-109 — Diseño de Feature Toggles

## Intención

Permitir que personal autorizado cambie, sin despliegue ni reinicio del backend, la disponibilidad del selector de idioma y del flujo existente para crear proyectos. La interfaz debe leer un estado persistido y compartirlo entre sus componentes.

## Alcance

- Flags booleanas iniciales: `language_switcher` y `project_creation`, ambas activas por defecto para conservar el comportamiento actual.
- API Spring Boot y persistencia PostgreSQL administrada por una migración Supabase incremental.
- Lectura pública únicamente de las dos claves y sus valores; escritura solo para perfiles con `platform_role = 'ADMIN'`.
- Controles en Angular: selector de idioma, navegación/flujo de creación de proyectos y panel de interruptores visible en el perfil de administradores.
- La API de creación de proyectos rechaza nuevas escrituras cuando la flag está apagada. Las lecturas y cambios de proyectos existentes mantienen sus políticas.

La creación de oportunidades permanece fuera de esta flag: el código tiene una API de administración de oportunidades, pero no presenta una acción de creación en Angular. La flag de creación apunta a la única acción de creación expuesta en la aplicación: crear proyectos.

## No objetivos y restricciones

No se vuelve a implementar i18n, no se usa Firebase, no se agrega una nueva jerarquía de administradores y los roles de membresía de proyecto no autorizan cambios globales. La identidad llega del JWT validado; el perfil y su rol se consultan en backend. El navegador solo usa HTTP para datos de producto. Las flags no reemplazan permisos de proyectos ni controles de autenticación.

## Enfoques considerados

1. Tabla PostgreSQL, API Spring y estado central de Angular: mantiene el backend como dueño del contrato y la base actual como fuente persistente; requiere una migración pequeña y pruebas JDBC.
2. Configuración local o variables de entorno: sencilla, pero no permite cambiar valores en ejecución ni persistir modificaciones desde la interfaz.
3. Servicio externo de configuración: añade infraestructura y dependencia que la issue no necesita.

Se selecciona el primer enfoque, consistente con la arquitectura existente y el flujo solicitado. La lectura entrega únicamente las flags públicas conocidas; las escrituras comprueban `profiles.platform_role` en el backend.

## Comportamiento

Angular inicia con las funciones no disponibles mientras carga la configuración para no exponer por un instante una función apagada. Si falla la lectura, conserva un shell funcional y oculta ambas acciones controladas. Una modificación actualiza el estado central solo después de una respuesta exitosa; si falla, el control recupera el valor anterior y muestra el error. El servidor consulta PostgreSQL en cada operación protegida, por lo que el cambio no requiere reiniciar servicios. Al recargar la aplicación se consultan de nuevo las flags.
