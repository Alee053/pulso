import { defineStrings } from '../../../core/i18n/define-strings'

export const filtersStrings = defineStrings(
  {
    compact: 'Filtros',
    toggleShow: 'Mostrar filtros',
    toggleHide: 'Ocultar filtros',
    clear: 'Limpiar búsqueda y filtros',
    sector: 'Sector',
    allSectors: 'Todos los sectores',
    stage: 'Etapa',
    allStages: 'Todas las etapas',
    location: 'Ubicación',
    locationPlaceholder: 'Ciudad o departamento',
    tags: 'Etiquetas',
    tagLimit: (limit: number): string => `Puedes seleccionar hasta ${limit} etiquetas.`,
    openOpportunities: 'Solo con oportunidades abiertas',
  },
  {
    compact: 'Filters',
    toggleShow: 'Show filters',
    toggleHide: 'Hide filters',
    clear: 'Clear search and filters',
    sector: 'Sector',
    allSectors: 'All sectors',
    stage: 'Stage',
    allStages: 'All stages',
    location: 'Location',
    locationPlaceholder: 'City or department',
    tags: 'Tags',
    tagLimit: (limit: number): string => `You can select up to ${limit} tags.`,
    openOpportunities: 'Only with open opportunities',
  },
)
