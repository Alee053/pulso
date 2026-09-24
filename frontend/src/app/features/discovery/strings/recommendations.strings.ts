export const recommendationStrings = {
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
} as const;
