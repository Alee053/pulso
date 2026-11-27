# Diseño de la interfaz de Pulso

Consulta del 27 de noviembre de 2026. Pulso ayuda a evaluar proyectos y encontrar oportunidades de colaboración. La investigación de octubre detectó texto pequeño de baja opacidad y acciones ocultas tras gestos en móvil. [Contexto de Pulso](../../CONTEXT.md) · [Investigación de octubre](ui-ux-refactor.md)

## Observaciones en fuentes primarias

- **YC Startup Directory** permite buscar y filtrar por industria y región. Sus resultados reúnen nombre, ubicación y una descripción breve. La ficha de Rio amplía ese resumen con actividad, sector, datos de la empresa y equipo fundador. [Directorio de YC](https://www.ycombinator.com/companies/?type=all) · [Ficha de Rio](https://www.ycombinator.com/companies/rio)
- **Kickstarter Discover** muestra filtros por categoría, ubicación y estado. En Diseño, el resultado de Keychron Orca echo incluye un resumen breve y metadatos de campaña. Su ficha presenta la acción de apoyo junto a los accesos a campaña, preguntas frecuentes y comentarios. [Discover](https://www.kickstarter.com/discover) · [Diseño](https://www.kickstarter.com/discover/categories/design) · [Campaña Orca echo](https://www.kickstarter.com/projects/keytron/keychron-orca-echo-split-keyboard-with-built-in-trackball)
- **Zulip** documenta la apertura de un hilo desde la lista web de conversaciones recientes y el regreso con Atrás del navegador. En el archivo público de la comunidad Lean se puede pasar del canal a un tema con título propio y mensajes fechados. [Conversaciones recientes](https://zulip.com/help/recent-conversations) · [Hilo de la comunidad Lean](https://leanprover-community.github.io/archive/stream/187764-Lean-for-teaching/topic/Automatic.20marking.20with.20moodle.html)
- **Microsoft Teams** muestra en su guía de producto una barra de destinos junto a la lista de chats y el hilo en escritorio. La captura móvil da el ancho a la lista y la guía de TalkBack documenta cómo abrir un hilo y volver con Atrás. [Capturas oficiales, p. 9](https://adoption.microsoft.com/files/microsoft-teams/chat-and-channels/Teams-new-chat-and-channels-user-guide.pdf#page=9) · [Guía de navegación de Teams](https://support.microsoft.com/en-us/accessibility/teams/basic-tasks-using-a-screen-reader-with-microsoft-teams)

## De las referencias a las pantallas

- **Descubrir y evaluar.** Mostrar la búsqueda y los filtros activos junto a los resultados. Repetir nombre, resumen, etapa, sector y ubicación donde estén disponibles para comparar proyectos. Explicar la recomendación con una razón vinculada a los intereses de la persona. Mantener Guardar, Me interesa y Descartar a la vista, también en pantallas táctiles. [Filtros de Pulso](../../frontend/src/app/features/discovery/ui/search-filters.ts) · [WCAG 2.2, alternativas a arrastrar](https://www.w3.org/TR/WCAG22/#dragging-movements)
- **Ficha editorial.** Abrir con nombre, resumen y datos clave, con la imagen adjunta en su propio espacio cuando exista. Dar a la descripción y la propuesta de valor una columna de lectura y a las oportunidades abiertas una sección visible, seguida de equipo y enlaces. Ubicar la solicitud de contacto junto al proyecto o la oportunidad correspondiente. Al volver de la ficha, recuperar la consulta y posición de partida para seguir comparando. [Ficha pública actual](../../frontend/src/app/features/discovery/ui/public-project-detail.html)
- **Navegación persistente.** Usar el lateral rotulado en escritorio. En móvil, la barra inferior da acceso a Descubrir, Guardados y Mensajes. El cuarto botón, Más, contiene Solicitudes, Mis equipos, Crear proyecto cuando está habilitado y Mi perfil. Los avisos conservan su lugar en la cabecera. El destino actual lleva un tratamiento de selección y `aria-current="page"`. [Navegación de Pulso](../../frontend/src/app/features/discovery/ui/navigation-bar.html) · [W3C, técnica `aria-current`](https://www.w3.org/WAI/WCAG22/Techniques/aria/ARIA26.html)
- **Chat adaptativo.** Cuando haya ancho, mostrar la lista junto al hilo. Alimentarla con las filas del historial de solicitudes que ya incluyen `conversationId` para el participante activo. En móvil, mostrar una vista a la vez y devolver el foco a la fila al volver. Mantener el acceso desde Avisos y `/conversaciones/{id}`. Mostrar proyecto y oportunidad cuando el historial los facilite, autor y hora por mensaje y un campo de escritura rotulado. Distinguir carga, hilo vacío, error con reintento e historial de solo lectura. [Patrón lista-detalle de Android](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts#list-detail) · [Contrato de Pulso](../api.md) · [Mensajes](../architecture/conversation-messages.md)

## Color y accesibilidad

El cobalto concentra las acciones primarias y los enlaces subrayados. Las superficies opacas de piedra clara y grafito dan prioridad al contenido. La paleta sigue los pares fondo-contenido de Material y la asignación de valores por tema de Atlassian, que también distingue selección y foco. [Tokens de Pulso](../../frontend/src/index.css) · [Material 3](https://developer.android.com/codelabs/m3-design-theming) · [Atlassian Design](https://atlassian.design/foundations/color/)

| Rol | Claro | Oscuro |
| --- | --- | --- |
| Lienzo / panel / fondo secundario | `#F3F2EE` / `#FCFBF8` / `#EEEDE8` | `#171918` / `#202322` / `#292D2B` |
| Superficie al pasar el puntero | `#E9E7E1` | `#323735` |
| Texto / texto secundario | `#202421` / `#565A55` | `#F4F3EE` / `#BBC0B9` |
| Acción / texto sobre acción | `#2347C9` / `#FFFFFF` | `#8FA8FF` / `#121B3A` |
| Selección / texto seleccionado | `#E5EBFF` / `#2347C9` | `#27345F` / `#8FA8FF` |
| Borde de control / anillo de foco | `#727872` / `#2347C9` | `#89928B` / `#A9BCFF` |

En las cuatro superficies neutras de la tabla, el contraste mínimo del texto secundario entre ambos temas es 5,68∶1. El tono de texto más tenue alcanza 4,77∶1, el texto de los botones primarios 7,41∶1 y el borde de los controles 3,66∶1. El anillo de foco de 3 px, separado 4 px del control, alcanza 6,03∶1 sobre las superficies neutras y seleccionadas. Los valores se calcularon con luminancia relativa sRGB. [Contraste de texto](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) · [Contraste no textual](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)

## Sistema visual e interacción

- Bricolage Grotesque da forma a los títulos y a la marca. DM Sans se usa en párrafos, formularios y navegación. Ambas fuentes variables se sirven con el proyecto. Las descripciones conservan una columna de lectura y los controles una etiqueta visible. [Fuentes y licencias](../../frontend/src/assets/fonts)
- El espaciado parte de 4 px. Los paneles usan radios de 20 px y un borde discreto. Los campos usan un borde de mayor contraste. Texto, icono y color distinguen confirmación, selección y error. [Estilos compartidos](../../frontend/src/index.css)
- La apariencia sigue al dispositivo en la primera visita. La elección de Claro, Oscuro o Sistema se guarda en ese navegador y se aplica también al acceso, las preferencias y las invitaciones. [Selector de apariencia](../../frontend/src/app/shared/theme-switcher/theme-switcher.ts)
- La navegación lateral aparece desde 64 rem. La bandeja de Mensajes decide cuántos paneles caben según su propio ancho. Desde 45 rem presenta el historial junto a la conversación. Por debajo de ese ancho, Volver a mensajes recupera la lista. El atajo de escritorio es Alt + Mayús + M. [Bandeja de conversaciones](../../frontend/src/app/features/discovery/ui/messages-view.html)
- Todas las decisiones por gesto tienen un botón. Los diálogos contienen el foco, admiten Escape y devuelven el foco al control de origen. La navegación fija deja espacio para el contenido y respeta el área segura del dispositivo. [Reflujo](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html) · [Foco visible](https://www.w3.org/WAI/WCAG22/Understanding/focus-visible.html)

Las capturas muestran los temas claro y oscuro, las variantes de escritorio y móvil y los principales recorridos con datos de prueba. [Galería de Pulso](../evidence/interfaz/README.md)
