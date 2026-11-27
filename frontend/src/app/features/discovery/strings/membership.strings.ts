import { defineStrings } from '../../../core/i18n/define-strings'

export const membershipStrings = defineStrings(
  {
    permissionDetail: 'Solo un integrante activo con rol owner o admin puede administrar membresías.',
    selfElevationDetail: 'No puedes elevar tu propio rol.',
    invalidTransitionDetail: 'Esa membresía no admite esta acción.',
    ownerRequiredDetail: 'El proyecto debe conservar al menos un owner activo.',
  },
  {
    permissionDetail: 'An active owner or admin can manage team memberships.',
    selfElevationDetail: 'You cannot upgrade your own role.',
    invalidTransitionDetail: 'This action is unavailable for this membership.',
    ownerRequiredDetail: 'The project must have at least one active owner.',
  },
)
