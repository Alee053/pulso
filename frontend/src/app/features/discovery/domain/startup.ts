export type InterestId =
  | 'discover'
  | 'invest'
  | 'collaborate'
  | 'opportunities'
  | 'inspiration'

export type AppView = 'discover' | 'saved' | 'profile' | 'detail'

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



export interface PublicSector {
    id: string
    slug: string
    name: string
}

export interface PublicTag {
    id: string
    slug: string
    name: string
}

export interface PublicExternalLink {
    type: string
    url: string
    label: string
}

export interface PublicMember {
    profileId: string
    displayName: string
    avatarStoragePath: string | null
    displayTitle: string | null
    isFeatured: boolean
}



export interface PublicProjectDetail {
    id: string
    slug: string
    name: string
    tagline: string
    description: string | null
    valueProposition: string | null
    sector: PublicSector
    stage: string
    city: string | null
    department: string | null
    tags: PublicTag[]
    externalLinks: PublicExternalLink[]
    members: PublicMember[]
}

export type PublicProjectState =
    | { status: 'loading' }
    | { status: 'loaded'; project: PublicProjectDetail }
    | { status: 'empty' }
    | { status: 'error'; message: string; retryable: boolean }