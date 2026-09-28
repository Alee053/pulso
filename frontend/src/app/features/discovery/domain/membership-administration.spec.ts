import { describe, expect, it } from 'vitest'

import {
  allowedMembershipActions,
  canAdministerMemberships,
  membershipAdministrationError,
  membershipAdministrationErrorFromDetail,
} from './membership-administration'

describe('membership administration', () => {
  it('lets only an active owner or admin administer memberships', () => {
    expect(canAdministerMemberships({ role: 'OWNER', membershipStatus: 'ACTIVE' })).toBe(true)
    expect(canAdministerMemberships({ role: 'ADMIN', membershipStatus: 'ACTIVE' })).toBe(true)
    expect(canAdministerMemberships({ role: 'MEMBER', membershipStatus: 'ACTIVE' })).toBe(false)
    expect(canAdministerMemberships({ role: 'OWNER', membershipStatus: 'INVITED' })).toBe(false)
    expect(canAdministerMemberships(null)).toBe(false)
  })

  it('documents owner, admin, and member actions for the interface', () => {
    expect(allowedMembershipActions({ role: 'OWNER', membershipStatus: 'ACTIVE' })).toEqual({
      activate: true,
      changeRole: true,
      withdrawOthers: true,
      leave: true,
    })
    expect(allowedMembershipActions({ role: 'ADMIN', membershipStatus: 'ACTIVE' })).toEqual({
      activate: true,
      changeRole: true,
      withdrawOthers: true,
      leave: true,
    })
    expect(allowedMembershipActions({ role: 'MEMBER', membershipStatus: 'ACTIVE' })).toEqual({
      activate: false,
      changeRole: false,
      withdrawOthers: false,
      leave: true,
    })
    expect(allowedMembershipActions({ role: 'OWNER', membershipStatus: 'INVITED' })).toEqual({
      activate: false,
      changeRole: false,
      withdrawOthers: false,
      leave: false,
    })
  })

  it('exposes permission, invalid-transition, and owner-invariant copy', () => {
    expect(membershipAdministrationError('permission')).toEqual({
      kind: 'permission',
      code: 'membership_permission',
      title: 'Membership permission',
      message: 'Solo un integrante activo con rol owner o admin puede administrar membresías.',
    })
    expect(membershipAdministrationError('invalidTransition')).toEqual({
      kind: 'invalidTransition',
      code: 'membership_invalid_transition',
      title: 'Invalid membership transition',
      message: 'Esa membresía no admite esta acción.',
    })
    expect(membershipAdministrationError('ownerInvariant')).toEqual({
      kind: 'ownerInvariant',
      code: 'membership_owner_required',
      title: 'Membership owner required',
      message: 'El proyecto debe conservar al menos un owner activo.',
    })
  })

  it('maps endpoint details onto the three interface errors', () => {
    expect(membershipAdministrationErrorFromDetail(
      'active OWNER or ADMIN membership is required.',
    ).message).toBe(
      'Solo un integrante activo con rol owner o admin puede administrar membresías.',
    )
    expect(membershipAdministrationError('permission', 'cannot elevate own role.')).toEqual({
      kind: 'permission',
      code: 'membership_permission',
      title: 'Membership permission',
      message: 'No puedes elevar tu propio rol.',
    })
    expect(membershipAdministrationErrorFromDetail('cannot elevate own role.').kind).toBe(
      'permission',
    )
    expect(membershipAdministrationErrorFromDetail(
      'cannot activate a membership with status INVITED.',
    ).kind).toBe('invalidTransition')
    expect(membershipAdministrationErrorFromDetail(
      'project must keep at least one active OWNER.',
    ).kind).toBe('ownerInvariant')
  })
})
