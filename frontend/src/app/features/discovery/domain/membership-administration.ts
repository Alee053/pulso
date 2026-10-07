import { membershipStrings } from '../strings/membership.strings'

export type MembershipAdminErrorKind =
  | 'permission'
  | 'invalidTransition'
  | 'ownerInvariant'

export interface MembershipActorView {
  role: string
  membershipStatus: string
}

export interface MembershipActionMatrix {
  activate: boolean
  changeRole: boolean
  withdrawOthers: boolean
  leave: boolean
}

export interface MembershipAdministrationError {
  readonly kind: MembershipAdminErrorKind
  readonly code: string
  readonly title: string
  readonly message: string
}

const permissionError = {
  kind: 'permission',
  code: 'membership_permission',
  title: membershipStrings.permissionTitle,
  message: membershipStrings.permissionDetail,
} as const satisfies MembershipAdministrationError

const selfElevationError = {
  kind: 'permission',
  code: 'membership_permission',
  title: membershipStrings.selfElevationTitle,
  message: membershipStrings.selfElevationDetail,
} as const satisfies MembershipAdministrationError

const invalidTransitionError = {
  kind: 'invalidTransition',
  code: 'membership_invalid_transition',
  title: membershipStrings.invalidTransitionTitle,
  message: membershipStrings.invalidTransitionDetail,
} as const satisfies MembershipAdministrationError

const ownerInvariantError = {
  kind: 'ownerInvariant',
  code: 'membership_owner_required',
  title: membershipStrings.ownerRequiredTitle,
  message: membershipStrings.ownerRequiredDetail,
} as const satisfies MembershipAdministrationError

export const membershipAdministrationErrors = {
  permission: permissionError,
  invalidTransition: invalidTransitionError,
  ownerInvariant: ownerInvariantError,
} as const

export function canAdministerMemberships(actor: MembershipActorView | null): boolean {
  return (
    actor != null &&
    actor.membershipStatus === 'ACTIVE' &&
    (actor.role === 'OWNER' || actor.role === 'ADMIN')
  )
}

export function allowedMembershipActions(
  actor: MembershipActorView | null,
): MembershipActionMatrix {
  const canAdminister = canAdministerMemberships(actor)
  return {
    activate: canAdminister,
    changeRole: canAdminister,
    withdrawOthers: canAdminister,
    leave: actor?.membershipStatus === 'ACTIVE',
  }
}

export function membershipAdministrationError(
  kind: MembershipAdminErrorKind,
  detail?: string,
): MembershipAdministrationError {
  if (kind === 'permission' && detail === 'cannot elevate own role.') {
    return selfElevationError
  }
  return membershipAdministrationErrors[kind]
}

export function membershipAdministrationErrorFromDetail(
  detail: string | null | undefined,
): MembershipAdministrationError {
  if (detail === 'cannot elevate own role.') {
    return selfElevationError
  }
  if (detail === 'active OWNER or ADMIN membership is required.') {
    return permissionError
  }
  if (detail === 'project must keep at least one active OWNER.') {
    return ownerInvariantError
  }
  return invalidTransitionError
}
