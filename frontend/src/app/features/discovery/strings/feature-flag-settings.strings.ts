import { defineStrings } from '../../../core/i18n/define-strings'

export const featureFlagSettingsStrings = defineStrings(
  {
    title: 'Disponibilidad de funciones',
    description: 'Controla qué funciones están disponibles en Pulso.',
    loading: 'Cargando configuración de funciones...',
    loadFailed: 'No se pudo cargar la configuración de funciones.',
    updateFailed: 'No se pudo actualizar la función. Se restauró el estado anterior.',
    updated: 'Configuración actualizada.',
    languageSwitcher: 'Selector de idioma',
    languageSwitcherDescription: 'Permite cambiar entre español e inglés.',
    projectCreation: 'Creación de proyectos',
    projectCreationDescription: 'Permite iniciar el flujo para crear proyectos.',
  },
  {
    title: 'Feature availability',
    description: 'Control which features are available in Pulso.',
    loading: 'Loading feature settings...',
    loadFailed: 'Feature settings could not be loaded.',
    updateFailed: 'The feature could not be updated. Its previous state was restored.',
    updated: 'Settings updated.',
    languageSwitcher: 'Language switcher',
    languageSwitcherDescription: 'Allows switching between Spanish and English.',
    projectCreation: 'Project creation',
    projectCreationDescription: 'Allows starting the project creation flow.',
  },
)
