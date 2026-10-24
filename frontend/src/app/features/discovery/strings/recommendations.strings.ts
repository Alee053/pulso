import { defineStrings } from '../../../core/i18n/define-strings'

export const recommendationStrings = defineStrings(
  {
    labelJoiner: ' y ',
    matched: (labels: string, city: string): string =>
      `Tus intereses conectan con ${labels} en ${city}.`,
    explore: (name: string, city: string): string =>
      `Te mostramos ${name} como una exploración nueva en ${city}, cerca de proyectos que están creciendo.`,
    signalLabels: {
      agriculture: 'Agrotech',
      collaboration: 'Colaboración',
      'clean-energy': 'Energía limpia',
      commerce: 'Comercio',
      community: 'Comunidad',
      education: 'Educación',
      food: 'Alimentos',
      growth: 'Crecimiento',
      impact: 'Impacto',
      innovation: 'Innovación',
      investment: 'Inversión',
      local: 'Local',
      mobility: 'Movilidad',
      products: 'Productos',
      sustainability: 'Sostenibilidad',
      technology: 'Tecnología',
      opportunities: 'Oportunidades',
    },
  },
  {
    labelJoiner: ' and ',
    matched: (labels: string, city: string): string =>
      `Your interests connect with ${labels} in ${city}.`,
    explore: (name: string, city: string): string =>
      `We are showing you ${name} as a new exploration in ${city}, near projects that are growing.`,
    signalLabels: {
      agriculture: 'Agrotech',
      collaboration: 'Collaboration',
      'clean-energy': 'Clean energy',
      commerce: 'Commerce',
      community: 'Community',
      education: 'Education',
      food: 'Food',
      growth: 'Growth',
      impact: 'Impact',
      innovation: 'Innovation',
      investment: 'Investment',
      local: 'Local',
      mobility: 'Mobility',
      products: 'Products',
      sustainability: 'Sustainability',
      technology: 'Technology',
      opportunities: 'Opportunities',
    },
  },
)
