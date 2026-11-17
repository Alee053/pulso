import { CommonModule } from '@angular/common'
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { HttpErrorResponse } from '@angular/common/http'
import { ProjectReviewsApi } from '../data-access/project-reviews-api'
import type { ProjectReviewCandidate, ProjectReviewDecision } from '../domain/project-review'
import { projectReviewStrings } from '../strings/project-review.strings'
import { commonStrings } from '../../../shared/strings/common.strings'
import { projectReviewContract } from '../data-access/discovery.catalog.contract'
import { projectStageStrings } from '../strings/project.strings'

@Component({
  selector: 'pulso-project-reviews',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './project-reviews.html',
})
export class ProjectReviewsComponent implements OnInit {
  readonly strings = projectReviewStrings
  readonly common = commonStrings
  private readonly api = inject(ProjectReviewsApi)
  private readonly destroyRef = inject(DestroyRef)
  readonly projects = signal<ProjectReviewCandidate[]>([])
  readonly loading = signal(false)
  readonly saving = signal<string | null>(null)
  readonly hasMore = signal(false)
  readonly noteMaxLength = signal<number>(projectReviewContract.noteMaxLength)
  readonly error = signal<string | null>(null)
  readonly saved = signal(false)
  readonly validation = signal<string | null>(null)
  readonly drafts = new Map<string, { note: string; verified: boolean }>()
  private nextOffset = 0

  ngOnInit(): void { this.load() }

  draft(projectId: string): { note: string; verified: boolean } {
    let draft = this.drafts.get(projectId)
    if (!draft) {
      draft = { note: '', verified: false }
      this.drafts.set(projectId, draft)
    }
    return draft
  }

  refresh(): void {
    if (this.loading() || this.saving()) return
    this.nextOffset = 0
    this.hasMore.set(false)
    this.projects.set([])
    this.saved.set(false)
    this.validation.set(null)
    this.load()
  }

  load(): void {
    if (this.loading() || this.saving()) return
    this.loading.set(true)
    this.error.set(null)
    this.api.list(this.nextOffset).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.projects.update(projects => [...projects, ...page.data])
        this.nextOffset = page.meta.offset + page.meta.limit
        this.hasMore.set(page.meta.hasMore && this.nextOffset <= projectReviewContract.maxOffset)
        this.noteMaxLength.set(page.meta.noteMaxLength)
        this.loading.set(false)
      },
      error: error => {
        if (error instanceof HttpErrorResponse && [401, 403].includes(error.status)) {
          this.projects.set([])
          this.hasMore.set(false)
          this.error.set(this.strings.forbidden)
        } else this.error.set(this.strings.loadFailed)
        this.loading.set(false)
      },
    })
  }

  stageLabel(stage: string): string {
    return stage in projectStageStrings ? projectStageStrings[stage as keyof typeof projectStageStrings] : stage
  }

  decide(project: ProjectReviewCandidate, decision: 'APPROVE' | 'REJECT'): void {
    if (this.loading() || this.saving() || !project.canReview) return
    const draft = this.draft(project.id)
    const note = draft.note.trim()
    this.validation.set(null)
    this.saved.set(false)
    if (decision === 'REJECT' && !note) {
      this.validation.set(project.id)
      return
    }
    if (note.length > this.noteMaxLength()) {
      this.validation.set(project.id)
      return
    }
    const request: ProjectReviewDecision = decision === 'APPROVE'
      ? { decision, verificationStatus: draft.verified ? 'VERIFIED' : 'UNVERIFIED', ...(note ? { note } : {}) }
      : { decision, note }
    this.saving.set(project.id)
    this.error.set(null)
    this.api.decide(project.id, request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving.set(null)
        this.drafts.delete(project.id)
        // Decisions remove rows from an offset-paginated queue; reload its first window.
        this.refresh()
        this.saved.set(true)
      },
      error: error => {
        this.saving.set(null)
        if (error instanceof HttpErrorResponse && error.error?.code === 'project_review_own_project') {
          this.projects.update(projects => projects.map(row => row.id === project.id ? { ...row, canReview: false } : row))
          this.error.set(this.strings.ownProject)
        } else if (error instanceof HttpErrorResponse && [401, 403].includes(error.status)) {
          this.projects.set([])
          this.hasMore.set(false)
          this.error.set(this.strings.forbidden)
        } else this.error.set(error instanceof HttpErrorResponse && error.status === 409
          ? this.strings.changed : this.strings.saveFailed)
      },
    })
  }
}
