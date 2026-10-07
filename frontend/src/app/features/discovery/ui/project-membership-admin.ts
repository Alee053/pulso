import { CommonModule } from '@angular/common'
import { HttpErrorResponse } from '@angular/common/http'
import { Component, computed, inject, input, OnDestroy, output, signal } from '@angular/core'
import { Subscription } from 'rxjs'
import { ProjectMembershipAdministrationApi } from '../data-access/project-membership-administration-api'
import type {
  ActorMembershipChange,
  ManagedProjectMember,
  ProjectMembershipRole,
} from '../domain/project-membership-administration'
import type { CurrentProjectMembership } from '../domain/member-invitation'
import {
  membershipAdministrationError,
  membershipAdministrationErrorFromDetail,
} from '../domain/membership-administration'
import { invitationStrings } from '../strings/invitation.strings'
import { membershipStrings } from '../strings/membership.strings'

const roleRank: Record<ProjectMembershipRole, number> = { MEMBER: 0, ADMIN: 1, OWNER: 2 }
const roles: ProjectMembershipRole[] = ['OWNER', 'ADMIN', 'MEMBER']

@Component({
  selector: 'pulso-project-membership-admin',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './project-membership-admin.html',
})
export class ProjectMembershipAdminComponent implements OnDestroy {
  private readonly api = inject(ProjectMembershipAdministrationApi)
  readonly project = input.required<CurrentProjectMembership>()
  readonly actorProfileId = input.required<string>()
  readonly membershipChanged = output<ActorMembershipChange>()
  readonly strings = membershipStrings
  readonly roleLabels = invitationStrings.role
  readonly roleOptions = roles.map((value) => ({ value, label: invitationStrings.role[value] }))
  readonly canManage = computed(() => this.project().membershipStatus === 'ACTIVE'
    && (this.project().role === 'OWNER' || this.project().role === 'ADMIN'))
  readonly expanded = signal(false)
  readonly members = signal<ManagedProjectMember[]>([])
  readonly loading = signal(false)
  readonly hasMore = signal(false)
  readonly errorMessage = signal<string | null>(null)
  readonly busyMemberId = signal<string | null>(null)
  private readonly selectedRoles = signal<Record<string, ProjectMembershipRole>>({})
  private nextOffset = 0
  private request: Subscription | null = null

  ngOnDestroy(): void { this.request?.unsubscribe() }

  toggle(): void {
    if (!this.canManage()) return
    if (this.expanded()) {
      this.expanded.set(false)
      return
    }
    this.expanded.set(true)
    if (this.members().length === 0) this.load(true)
  }

  refresh(): void { this.load(true) }

  load(reset = false): void {
    if (!this.canManage() || this.loading()) return
    if (reset) {
      this.members.set([])
      this.nextOffset = 0
      this.hasMore.set(false)
      this.selectedRoles.set({})
    }
    this.errorMessage.set(null)
    this.loading.set(true)
    const offset = this.nextOffset
    this.request = this.api.list(this.project().projectId, offset).subscribe({
      next: (page) => {
        this.members.update((members) => [...members, ...page.data])
        this.nextOffset = page.meta.offset + page.meta.limit
        this.hasMore.set(page.meta.hasMore && this.nextOffset <= 100000)
        this.loading.set(false)
      },
      error: () => {
        this.errorMessage.set(this.strings.membersFailed)
        this.loading.set(false)
      },
    })
  }

  memberLabel(member: ManagedProjectMember): string {
    if (member.profileId === this.actorProfileId()) return this.strings.you
    if (member.displayName?.trim()) return member.displayName
    if (member.membershipStatus === 'INVITED' && member.inviteeEmail) return member.inviteeEmail
    if (member.membershipStatus === 'INVITED') return this.strings.invitedMember
    return this.strings.memberFallback
  }

  selectedRole(member: ManagedProjectMember): ProjectMembershipRole {
    return this.selectedRoles()[member.id] ?? member.role
  }

  availableRoles(member: ManagedProjectMember): ProjectMembershipRole[] {
    return this.roleOptions
      .map((option) => option.value)
      .filter((role) => member.profileId !== this.actorProfileId()
        || roleRank[role] <= roleRank[member.role])
  }

  setRole(member: ManagedProjectMember, event: Event): void {
    const role = (event.target as HTMLSelectElement).value as ProjectMembershipRole
    if (!roles.includes(role) || !this.availableRoles(member).includes(role)) return
    this.selectedRoles.update((selected) => ({ ...selected, [member.id]: role }))
  }

  updateRole(member: ManagedProjectMember): void {
    this.apply(member, 'changeRole', this.selectedRole(member))
  }

  activate(member: ManagedProjectMember): void { this.apply(member, 'activate') }

  withdraw(member: ManagedProjectMember): void { this.apply(member, 'withdraw') }

  trackMember(_index: number, member: ManagedProjectMember): string { return member.id }

  private apply(
    member: ManagedProjectMember,
    action: 'activate' | 'withdraw' | 'changeRole',
    role?: ProjectMembershipRole,
  ): void {
    if (!this.canManage() || this.busyMemberId()) return
    this.busyMemberId.set(member.id)
    this.errorMessage.set(null)
    this.request = this.api.administer(this.project().projectId, member.id, action, role).subscribe({
      next: (updated) => {
        if (updated.profileId === this.actorProfileId()) {
          this.members.set([])
          this.expanded.set(false)
          this.nextOffset = 0
          this.hasMore.set(false)
          this.membershipChanged.emit({ projectId: updated.projectId, role: updated.role })
          return
        }
        this.members.update((members) => members.map((current) => current.id === updated.id
          ? { ...current, role: updated.role, membershipStatus: updated.membershipStatus }
          : current))
        this.selectedRoles.update((selected) => {
          const next = { ...selected }
          delete next[updated.id]
          return next
        })
      },
      error: (error: unknown) => {
        this.errorMessage.set(actionFailureMessage(error))
        this.busyMemberId.set(null)
      },
      complete: () => this.busyMemberId.set(null),
    })
  }
}

function actionFailureMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return membershipStrings.actionFailed
  if (error.status === 401) return membershipStrings.sessionExpired
  const problem = error.error as { code?: unknown; detail?: unknown } | null
  const code = typeof problem?.code === 'string' ? problem.code : ''
  const detail = typeof problem?.detail === 'string' ? problem.detail : ''
  if (code === 'membership_owner_required') {
    return membershipAdministrationError('ownerInvariant').message
  }
  if (code === 'membership_permission') {
    return membershipAdministrationError('permission', detail).message
  }
  if (code === 'membership_invalid_transition') {
    return membershipAdministrationError('invalidTransition').message
  }
  if (code === 'membership_conflict') {
    return membershipAdministrationError('conflict').message
  }
  if (error.status === 403) {
    return detail
      ? membershipAdministrationErrorFromDetail(detail).message
      : membershipAdministrationError('permission').message
  }
  if (error.status === 409) {
    return membershipAdministrationErrorFromDetail(detail).message
  }
  return membershipStrings.actionFailed
}
