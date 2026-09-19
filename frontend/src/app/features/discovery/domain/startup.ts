export type InterestId =
  | 'discover'
  | 'invest'
  | 'collaborate'
  | 'opportunities'
  | 'inspiration'

export type AppView = 'discover' | 'create-project' | 'saved' | 'profile'

export type Decision = 'skip' | 'interested'

export interface InterestOption {
  id: InterestId
  label: string
  shortLabel: string
  description: string
  icon: 'sparkles' | 'trending' | 'handshake' | 'compass' | 'heart'
  signals: string[]
}

export interface Founder {
  name: string
  role: string
  initials: string
}

export interface StartupMetric {
  label: string
  value: string
}

export interface Startup {
  id: string
  name: string
  logoMark: string
  city: string
  sector: string
  pitch: string
  description: string
  stage: string
  tags: string[]
  badges: string[]
  image: string
  imageAlt: string
  accent: string
  founders: Founder[]
  lookingFor: string[]
  founded: string
  teamSize: string
  traction: string
  website: string
  socials: string[]
  recommendation: string
  metrics: StartupMetric[]
  popularity: number
  exploration: number
}

export interface StartupSummary {
  id: string
  slug: string
  name: string
  tagline: string
  sector: string
  city: string
  stage: string
}
