export const feedStrings = {
  pageEyebrow: 'EL ECOSISTEMA, MÁS CERCA',
  pageTitle: 'Ideas con pulso.',
  pageLead: 'Descubre proyectos, conoce sus ideas y guarda los que te inspiran.',
  feedAria: 'Proyectos por descubrir',
  actionsAria: 'Acciones del proyecto',
  contextAria: 'Tu exploración',
  contextTitle: 'Una mirada propia',
  contextLead: 'Tus intereses orientan los proyectos que descubres.',
  actionsTitle: 'Explora a tu ritmo',
  actionsLead: 'Descarta un proyecto o expresa tu interés para personalizar tu exploración. También puedes guardarlo para después.',
  unsaveAria: 'Quitar de guardados (S)',
  loadingEyebrow: 'PREPARANDO TU EXPLORACIÓN',
  loadingTitle: 'Buscando proyectos publicados.',
  loadingLead: 'Estamos preparando los proyectos publicados para ti.',
  errorEyebrow: 'NO SE PUDO CARGAR',
  errorTitle: 'El catálogo no está disponible.',
  catalogFailed:
    'No pudimos cargar los proyectos publicados. Inténtalo de nuevo.',
  progress: (current: number, total: number): string =>
    `Exploración ${String(current).padStart(2, '0')} / ${String(total).padStart(2, '0')}`,
  remaining: (count: number): string => `${count} por ver`,
  skip: 'Descartar',
  interested: 'Me interesa',
  seeProfile: 'Ver perfil',
  save: 'Guardar',
  saved: 'Guardado',
  skipAria: 'Descartar proyecto (←)',
  saveAria: 'Guardar (S)',
  interestedAria: 'Me interesa (→)',
  emptyEyebrow: 'CATÁLOGO VACÍO',
  emptyTitle: 'Todavía no hay proyectos publicados.',
  emptyLead:
    'Cuando exista un proyecto publicado aparecerá aquí para descubrirlo.',
  doneEyebrow: 'PRIMERA VUELTA COMPLETA',
  doneTitle: 'Ya viste todo el pulso.',
  doneLead:
    'Tus guardados e intereses quedan vinculados a tu cuenta. Puedes volver a explorar cuando quieras.',
  restart: 'Volver a explorar',
  toastSaved: 'Guardado en tu selección',
  toastRemoved: 'Quitado de tus guardados',
  interactionSaveFailed: 'No pudimos actualizar tus guardados. Inténtalo de nuevo.',
  interactionFeedbackFailed: 'No pudimos guardar tu decisión. Inténtalo de nuevo.',
  retryInteraction: 'Reintentar',
} as const;

export const cardStrings = {
  openAria: (name: string): string => `Ver perfil de ${name}`,
  saveAria: 'Guardar proyecto',
  unsaveAria: 'Quitar de guardados',
  openProfile: 'Ver perfil completo',
  descriptionFallback: (city: string, sector: string): string =>
    `Proyecto publicado en ${city}, dentro del sector ${sector}.`,
} as const;
