export const feedStrings = {
  loadingEyebrow: 'CARGANDO DISCOVERY',
  loadingTitle: 'Buscando proyectos publicados.',
  loadingLead: 'Estamos consultando el catálogo real de Pulso.',
  errorEyebrow: 'NO SE PUDO CARGAR',
  errorTitle: 'El catálogo no está disponible.',
  catalogFailed:
    'No pudimos cargar los proyectos publicados. Inténtalo de nuevo.',
  progress: (current: number, total: number): string =>
    `Exploración ${String(current).padStart(2, '0')} / ${String(total).padStart(2, '0')}`,
  remaining: (count: number): string => `${count} por ver`,
  skip: 'Pasar',
  interested: 'Me interesa',
  seeProfile: 'Ver perfil',
  save: 'Guardar',
  saved: 'Guardado',
  skipAria: 'Pasar (←)',
  saveAria: 'Guardar (S)',
  interestedAria: 'Me interesa (→)',
  emptyEyebrow: 'CATÁLOGO VACÍO',
  emptyTitle: 'Todavía no hay proyectos publicados.',
  emptyLead:
    'Cuando exista un proyecto publicado aparecerá aquí para descubrirlo.',
  doneEyebrow: 'PRIMERA VUELTA COMPLETA',
  doneTitle: 'Ya viste todo el pulso.',
  doneLead:
    'Tu selección quedó guardada. ¿Quieres explorar con una mirada nueva?',
  restart: 'Volver a explorar',
  toastSaved: 'Guardado en tu selección',
  toastRemoved: 'Quitado de tus guardados',
} as const;

export const cardStrings = {
  openAria: (name: string): string => `Ver perfil de ${name}`,
  saveAria: 'Guardar startup',
  unsaveAria: 'Quitar de guardados',
  openProfile: 'Ver perfil completo',
  descriptionFallback: (city: string, sector: string): string =>
    `Proyecto publicado en ${city}, dentro del sector ${sector}.`,
} as const;
