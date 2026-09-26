import { cardStrings } from '../strings/feed.strings'
import type { Startup, StartupSummary } from '../domain/startup'

const sectorSignals: Record<string, string[]> = {
  agrotech: ['agriculture', 'technology'],
  alimentos: ['food', 'products'],
  comercio: ['commerce', 'technology'],
  educación: ['education', 'community'],
  'energía limpia': ['clean-energy', 'sustainability'],
  movilidad: ['mobility', 'community'],
  sostenibilidad: ['sustainability', 'impact'],
  tecnología: ['technology', 'innovation'],
}

export function toStartupViewModel(summary: StartupSummary): Startup {
  const tags = sectorSignals[summary.sector.trim().toLowerCase()] ?? []

  return {
    id: summary.id,
    name: summary.name,
    logoMark: getLogoMark(summary.name),
    city: summary.city,
    sector: summary.sector,
    pitch: summary.tagline,
    description: cardStrings.descriptionFallback(summary.city, summary.sector),
    stage: summary.stage,
    tags,
    badges: [summary.stage, summary.sector],
    image: '',
    imageAlt: '',
    accent: '#a9e1ff',
    founders: [],
    lookingFor: [],
    founded: '',
    teamSize: '',
    traction: '',
    website: '',
    socials: [],
    recommendation: '',
    metrics: [],
    popularity: 0.5,
    exploration: 0.5,
  }
}

function getLogoMark(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) {
    return '?'
  }

  return words
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('')
}
