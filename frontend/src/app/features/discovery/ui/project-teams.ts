import { CommonModule } from '@angular/common'
import { Component, effect, inject, OnDestroy, signal, untracked } from '@angular/core'
import { Subscription } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { CurrentProjectsApi } from '../data-access/current-projects-api'
import type { CurrentProjectMembership } from '../domain/member-invitation'
import { invitationStrings } from '../strings/invitation.strings'
import { MemberInvitationFormComponent } from './member-invitation-form'

@Component({
  selector: 'pulso-project-teams',
  standalone: true,
  imports: [CommonModule, MemberInvitationFormComponent],
  templateUrl: './project-teams.html',
})
export class ProjectTeamsComponent implements OnDestroy {
  readonly strings = invitationStrings
  private readonly auth = inject(SupabaseAuthGateway)
  private readonly api = inject(CurrentProjectsApi)
  private request: Subscription | null = null
  private userId: string | null = null
  private nextOffset = 0
  readonly projects = signal<CurrentProjectMembership[]>([])
  readonly loading = signal(false)
  readonly errorMessage = signal<string | null>(null)
  readonly hasMore = signal(false)

  private readonly sessionEffect = effect(() => {
    const userId = this.auth.session()?.id ?? null
    untracked(() => {
      if (this.userId === userId) return
      this.userId = userId
      this.request?.unsubscribe()
      this.projects.set([])
      this.nextOffset = 0
      this.hasMore.set(false)
      this.loading.set(false)
      this.errorMessage.set(null)
      if (userId) this.load()
    })
  })

  ngOnDestroy(): void { this.request?.unsubscribe() }

  refresh(): void {
    if (this.loading()) return
    this.projects.set([])
    this.nextOffset = 0
    this.hasMore.set(false)
    this.load()
  }

  load(): void {
    if (this.loading() || !this.auth.session()) return
    this.errorMessage.set(null)
    this.loading.set(true)
    this.request = this.api.list(this.nextOffset).subscribe({
      next: (page) => {
        this.projects.update((projects) => [...projects, ...page.data])
        this.nextOffset = page.meta.offset + page.meta.limit
        this.hasMore.set(page.meta.hasMore && this.nextOffset <= 100000)
        this.loading.set(false)
      },
      error: () => {
        this.errorMessage.set(this.strings.teamsFailed)
        this.loading.set(false)
      },
    })
  }

  trackProject(_index: number, project: CurrentProjectMembership): string { return project.projectId }
}
