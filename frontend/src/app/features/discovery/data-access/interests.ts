import { interestStrings } from '../strings/onboarding.strings'
import type { InterestOption } from '../domain/startup'

export const interestOptions: readonly InterestOption[] = [
  {
    id: 'discover',
    label: interestStrings.discoverLabel,
    shortLabel: interestStrings.discoverShort,
    description: interestStrings.discoverDescription,
    icon: 'sparkles',
    signals: ['products', 'commerce', 'food', 'technology'],
  },
  {
    id: 'invest',
    label: interestStrings.investLabel,
    shortLabel: interestStrings.investShort,
    description: interestStrings.investDescription,
    icon: 'trending',
    signals: ['investment', 'technology', 'growth', 'clean-energy'],
  },
  {
    id: 'collaborate',
    label: interestStrings.collaborateLabel,
    shortLabel: interestStrings.collaborateShort,
    description: interestStrings.collaborateDescription,
    icon: 'handshake',
    signals: ['collaboration', 'community', 'sustainability', 'technology'],
  },
  {
    id: 'opportunities',
    label: interestStrings.opportunitiesLabel,
    shortLabel: interestStrings.opportunitiesShort,
    description: interestStrings.opportunitiesDescription,
    icon: 'compass',
    signals: ['education', 'mobility', 'commerce', 'agriculture'],
  },
  {
    id: 'inspiration',
    label: interestStrings.inspirationLabel,
    shortLabel: interestStrings.inspirationShort,
    description: interestStrings.inspirationDescription,
    icon: 'heart',
    signals: ['impact', 'sustainability', 'local', 'innovation'],
  },
]
