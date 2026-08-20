import type { InterestOption } from '../domain/startup'

export const interestOptions: readonly InterestOption[] = [
  {
    id: 'discover',
    label: 'Descubrir productos',
    shortLabel: 'Productos',
    description: 'Ideas nuevas para usar, probar y compartir.',
    icon: 'sparkles',
    signals: ['products', 'commerce', 'food', 'technology'],
  },
  {
    id: 'invest',
    label: 'Invertir',
    shortLabel: 'Invertir',
    description: 'Equipos con potencial y una historia que seguir.',
    icon: 'trending',
    signals: ['investment', 'technology', 'growth', 'clean-energy'],
  },
  {
    id: 'collaborate',
    label: 'Colaborar',
    shortLabel: 'Colaborar',
    description: 'Proyectos que buscan talento, aliados y comunidad.',
    icon: 'handshake',
    signals: ['collaboration', 'community', 'sustainability', 'technology'],
  },
  {
    id: 'opportunities',
    label: 'Encontrar oportunidades',
    shortLabel: 'Oportunidades',
    description: 'Conecta con lo que está pasando cerca de ti.',
    icon: 'compass',
    signals: ['education', 'mobility', 'commerce', 'agriculture'],
  },
  {
    id: 'inspiration',
    label: 'Inspirarme',
    shortLabel: 'Inspirarme',
    description: 'Historias locales que abren nuevas posibilidades.',
    icon: 'heart',
    signals: ['impact', 'sustainability', 'local', 'innovation'],
  },
]
