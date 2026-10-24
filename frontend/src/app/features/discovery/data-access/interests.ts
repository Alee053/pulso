import { interestStrings } from '../strings/onboarding.strings'
import type { InterestOption } from '../domain/startup'

// Texts are read when used, so the options follow the current language.
export const interestOptions: readonly InterestOption[] = [
  {
    id: 'discover',
    get label(): string { return interestStrings.discoverLabel },
    get shortLabel(): string { return interestStrings.discoverShort },
    get description(): string { return interestStrings.discoverDescription },
    icon: 'sparkles',
    signals: ['products', 'commerce', 'food', 'technology'],
  },
  {
    id: 'invest',
    get label(): string { return interestStrings.investLabel },
    get shortLabel(): string { return interestStrings.investShort },
    get description(): string { return interestStrings.investDescription },
    icon: 'trending',
    signals: ['investment', 'technology', 'growth', 'clean-energy'],
  },
  {
    id: 'collaborate',
    get label(): string { return interestStrings.collaborateLabel },
    get shortLabel(): string { return interestStrings.collaborateShort },
    get description(): string { return interestStrings.collaborateDescription },
    icon: 'handshake',
    signals: ['collaboration', 'community', 'sustainability', 'technology'],
  },
  {
    id: 'opportunities',
    get label(): string { return interestStrings.opportunitiesLabel },
    get shortLabel(): string { return interestStrings.opportunitiesShort },
    get description(): string { return interestStrings.opportunitiesDescription },
    icon: 'compass',
    signals: ['education', 'mobility', 'commerce', 'agriculture'],
  },
  {
    id: 'inspiration',
    get label(): string { return interestStrings.inspirationLabel },
    get shortLabel(): string { return interestStrings.inspirationShort },
    get description(): string { return interestStrings.inspirationDescription },
    icon: 'heart',
    signals: ['impact', 'sustainability', 'local', 'innovation'],
  },
]
