import { CommonModule } from '@angular/common'
import { Component, computed, effect, ElementRef, EventEmitter, inject, Input, OnChanges, OnDestroy,
  Output, signal, untracked, ViewChild } from '@angular/core'
import { Subscription } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { ProjectMembersApi } from '../data-access/project-members-api'
import type { CurrentProjectMembership } from '../domain/member-invitation'
import { allowedMembershipActions, canAdministerMemberships } from '../domain/membership-administration'
import { membershipRoles, type MembershipChange, type MembershipFailure, type MembershipRole,
  type ProjectMember, type ProjectMembersPage, type UpdatedMembership } from '../domain/project-member'
import { teamStrings } from '../strings/team.strings'
import { MemberInvitationFormComponent } from './member-invitation-form'

interface MembershipConfirmation {
  member: ProjectMember
  change: MembershipChange
}

@Component({
  selector: 'pulso-project-members',
  standalone: true,
  imports: [CommonModule, MemberInvitationFormComponent],
  templateUrl: './project-members.html',
})
export class ProjectMembersComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) project!: CurrentProjectMembership
  @Output() ownMembershipChanged = new EventEmitter<Pick<UpdatedMembership, 'projectId' | 'role' | 'membershipStatus'>>()
  @ViewChild('confirmationPanel') set confirmationPanel(panel: ElementRef<HTMLElement> | undefined) {
    panel?.nativeElement.focus()
  }
  readonly strings = teamStrings
  readonly auth = inject(SupabaseAuthGateway)
  private readonly api = inject(ProjectMembersApi)
  private readonly projectState = signal<CurrentProjectMembership | null>(null)
  private readonly actor = signal<ProjectMembersPage['actor'] | null>(null)
  private loadRequest: Subscription | null = null
  private changeRequest: Subscription | null = null
  private userId: string | null = null
  private nextOffset = 0
  private retryOffset = 0
  private readonly memberOffsets = new Map<string, number>()
  private opener: HTMLElement | null = null
  readonly members = signal<ProjectMember[]>([])
  readonly loading = signal(false)
  readonly submitting = signal(false)
  readonly hasMore = signal(false)
  readonly loadError = signal<string | null>(null)
  readonly changeError = signal<string | null>(null)
  readonly succeeded = signal(false)
  readonly confirmation = signal<MembershipConfirmation | null>(null)
  readonly canAdminister = computed(() => !!this.auth.session() && canAdministerMemberships(this.actor()))
  readonly invitationProject = computed(() => {
    const project = this.projectState()
    const actor = this.actor()
    return project && actor ? { ...project, ...actor } : null
  })

  private readonly sessionEffect = effect(() => {
    const userId = this.auth.session()?.id ?? null
    untracked(() => {
      if (this.userId === userId) return
      this.userId = userId
      this.reset()
      if (userId && this.project) this.load()
    })
  })

  ngOnChanges(): void {
    this.projectState.set(this.project)
    this.reset()
    // The first load is owned by the session effect; subsequent input changes reload here.
    if (this.userId) this.load()
  }

  ngOnDestroy(): void {
    this.loadRequest?.unsubscribe()
    this.changeRequest?.unsubscribe()
  }

  refresh(): void {
    if (this.loading() || this.submitting()) return
    this.cancel()
    this.members.set([])
    this.memberOffsets.clear()
    this.nextOffset = 0
    this.hasMore.set(false)
    this.load()
  }

  load(offset = this.nextOffset): void {
    if (this.loading() || this.submitting() || !this.auth.session()) return
    this.cancel()
    this.loadError.set(null)
    this.loading.set(true)
    this.retryOffset = offset
    this.loadRequest = this.api.list(this.project.projectId, offset).subscribe({
      next: (page) => {
        if (this.actor() && this.actor()?.role !== page.actor.role) {
          this.members.set([])
          this.memberOffsets.clear()
          this.nextOffset = 0
          this.hasMore.set(false)
          if (offset > 0) {
            this.loading.set(false)
            this.actor.set(page.actor)
            this.load(0)
            return
          }
        }
        if (this.actor()?.role !== page.actor.role) this.actor.set(page.actor)
        this.members.update((members) => {
          const byId = new Map(members.map((member) => [member.id, member]))
          for (const member of page.data) {
            byId.set(member.id, member)
            this.memberOffsets.set(member.id, offset)
          }
          return [...byId.values()]
        })
        if (page.meta.offset + page.meta.limit >= this.nextOffset) {
          this.nextOffset = page.meta.offset + page.meta.limit
          this.hasMore.set(page.meta.hasMore && this.nextOffset <= 100000)
        }
        this.loading.set(false)
        if (this.project.role !== page.actor.role) {
          this.ownMembershipChanged.emit({ projectId: this.project.projectId, ...page.actor })
        }
      },
      error: (failure: MembershipFailure) => {
        if (failure.refreshPermissions) {
          this.actor.set(null)
          this.members.set([])
          this.nextOffset = 0
          this.retryOffset = 0
          this.memberOffsets.clear()
          this.hasMore.set(false)
        }
        this.loadError.set(failure.message)
        this.loading.set(false)
      },
    })
  }

  retry(): void { this.load(this.retryOffset) }

  isOwn(member: ProjectMember): boolean { return member.profileId === this.auth.session()?.id }

  canActivate(member: ProjectMember): boolean {
    return allowedMembershipActions(this.actor()).activate && member.profileId !== null
      && (member.membershipStatus === 'LEFT' || member.membershipStatus === 'REMOVED')
  }

  canWithdraw(member: ProjectMember): boolean {
    return allowedMembershipActions(this.actor()).withdrawOthers
      && (member.membershipStatus === 'ACTIVE' || member.membershipStatus === 'INVITED')
  }

  roleOptions(member: ProjectMember): readonly MembershipRole[] {
    if (!allowedMembershipActions(this.actor()).changeRole || member.membershipStatus !== 'ACTIVE') return []
    return membershipRoles.filter((role) => !this.isOwn(member)
      || membershipRoles.indexOf(role) >= membershipRoles.indexOf(member.role))
  }

  requestRoleChange(member: ProjectMember, event: Event): void {
    const select = event.target as HTMLSelectElement
    const role = this.roleOptions(member).find((option) => option === select.value)
    select.value = member.role
    if (role && role !== member.role) this.prepare(member, { action: 'changeRole', role }, select)
  }

  prepare(member: ProjectMember, change: MembershipChange, opener: HTMLElement): void {
    if (!this.canAdminister() || this.loading() || this.submitting() || !this.members().includes(member)) return
    if (change.action === 'activate' && !this.canActivate(member)) return
    if (change.action === 'withdraw' && !this.canWithdraw(member)) return
    if (change.action === 'changeRole' && (!this.roleOptions(member).includes(change.role) || change.role === member.role)) return
    this.opener = opener
    this.changeError.set(null)
    this.succeeded.set(false)
    this.confirmation.set({ member, change })
  }

  cancel(): void {
    if (this.submitting()) return
    this.confirmation.set(null)
    if (this.opener?.isConnected) this.opener.focus()
    this.opener = null
  }

  confirm(): void {
    const confirmation = this.confirmation()
    if (!confirmation || !this.canAdminister() || this.loading() || this.submitting()) return
    this.submitting.set(true)
    this.changeRequest = this.api.update(this.project.projectId, confirmation.member.id, confirmation.change).subscribe({
      next: (result) => {
        this.members.update((members) => members.map((member) => member.id === result.id ? {
          ...member, ...result,
          ...(result.membershipStatus !== 'ACTIVE' ? { displayName: null, displayTitle: null } : {}),
        } : member))
        this.submitting.set(false)
        this.succeeded.set(true)
        this.cancel()
        if (this.isOwn(confirmation.member)) {
          this.ownMembershipChanged.emit({ projectId: result.projectId, role: result.role, membershipStatus: result.membershipStatus })
        } else {
          // Re-read this page for public fields without discarding other loaded pages.
          this.load(this.memberOffsets.get(result.id) ?? 0)
        }
      },
      error: (failure: MembershipFailure) => {
        this.submitting.set(false)
        this.cancel()
        this.changeError.set(failure.message)
        // A failed response can follow a committed request. Reconcile rather than resubmit blindly.
        this.actor.set(null)
        this.refresh()
      },
    })
  }

  trackMember(_index: number, member: ProjectMember): string { return member.id }

  private reset(): void {
    this.loadRequest?.unsubscribe()
    this.changeRequest?.unsubscribe()
    this.members.set([])
    this.memberOffsets.clear()
    this.actor.set(null)
    this.nextOffset = 0
    this.retryOffset = 0
    this.loading.set(false)
    this.submitting.set(false)
    this.hasMore.set(false)
    this.loadError.set(null)
    this.changeError.set(null)
    this.succeeded.set(false)
    this.confirmation.set(null)
    this.opener = null
  }
}
