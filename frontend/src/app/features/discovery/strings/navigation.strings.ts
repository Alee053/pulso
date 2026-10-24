import { defineStrings } from '../../../core/i18n/define-strings'

export const navigationStrings = defineStrings(
  {
    menuLabel: 'TU ESPACIO',
    exampleShortLabel: 'Ejemplo',
    discover: 'Descubrir',
    createProject: 'Crear proyecto',
    teams: 'Mis equipos',
    saved: 'Guardados',
    profile: 'Perfil',
    aria: 'Navegación principal',
    sessionEyebrow: 'SESIÓN ACTIVA',
    savedSignals: (n: number): string => `${n} señales guardadas`,
  },
  {
    menuLabel: 'YOUR SPACE',
    exampleShortLabel: 'Example',
    discover: 'Discover',
    createProject: 'Create project',
    teams: 'My teams',
    saved: 'Saved',
    profile: 'Profile',
    aria: 'Main navigation',
    sessionEyebrow: 'ACTIVE SESSION',
    savedSignals: (n: number): string => `${n} saved ${n === 1 ? 'signal' : 'signals'}`,
  },
)
