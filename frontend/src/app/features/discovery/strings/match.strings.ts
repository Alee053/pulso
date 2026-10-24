import type { MatchFactorKey, MatchFactorStatus } from '../domain/opportunity-match'

export const matchStrings = {
  title: 'Compatibilidad contigo',
  scoreAria: (title: string, score: number): string =>
    `Compatibilidad con ${title}: ${score} de 100`,
  scoreLabel: (score: number): string => `${score}%`,
  showDetails: 'Ver por qué',
  hideDetails: 'Ocultar detalles',
  points: (points: number, weight: number): string => `${points} de ${weight} puntos`,
  signedOut: 'Inicia sesión para ver qué tan compatible eres con cada oportunidad.',
  incompleteProfile:
    'Tu perfil está incompleto, por eso esta compatibilidad puede ser menor de lo real.',
  missingIntro: 'Completa tu perfil para comparar:',
  explanation:
    'Se calcula con tus habilidades, intereses, intención, etapa y ubicación. No usa aprendizaje automático.',
  matchedLabel: 'Coincide',
  unmatchedLabel: 'No coincide',
  expectedLabel: 'La oportunidad pide',
  factors: {
    SKILLS: 'Habilidades',
    INTENTION: 'Intención',
    INTERESTS: 'Intereses',
    SECTOR: 'Sector',
    STAGE: 'Etapa',
    LOCATION: 'Ubicación',
  } satisfies Record<MatchFactorKey, string>,
  statuses: {
    MATCHED: 'Coincide',
    PARTIAL: 'Coincide en parte',
    NOT_MATCHED: 'No coincide',
    MISSING_PROFILE_DATA: 'Falta en tu perfil',
    NOT_APPLICABLE: 'No aplica',
  } satisfies Record<MatchFactorStatus, string>,
  reasons: {
    REMOTE_ALLOWED: 'La oportunidad permite trabajar a distancia.',
    SAME_CITY: 'Estás en la misma ciudad.',
    SAME_DEPARTMENT: 'Estás en el mismo departamento.',
    DIFFERENT_LOCATION: 'Estás en otra ubicación.',
  } as Record<string, string>,
} as const
