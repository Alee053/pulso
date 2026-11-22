import { Component, DestroyRef, Input, OnChanges, SimpleChanges, inject, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import type { Subscription } from 'rxjs'
import { commonStrings } from '../../../shared/strings/common.strings'
import { PulsoSession } from '../application/pulso-session'
import { ProjectInteractionsApi, type PersistedFeedbackType } from '../data-access/project-interactions-api'
import type { Decision, Startup } from '../domain/startup'
import { feedStrings } from '../strings/feed.strings'
import { projectDecisionStrings } from '../strings/project-decision.strings'

@Component({
  selector: 'pulso-project-decision',
  template: `
    <section class="panel my-6 grid gap-3 p-4" aria-labelledby="project-decision-title">
      <h2 #heading id="project-decision-title" class="field-label" tabindex="-1">{{ strings.title }}</h2>
      @if (state() === 'loading') {
        <p role="status" class="field-hint">{{ strings.loading }}</p>
      } @else if (state() === 'error') {
        <p role="alert" class="field-error">{{ strings.readFailed }}</p>
        <button type="button" class="contact-button w-fit" (click)="load()">{{ common.retry }}</button>
      } @else {
        @if (feedback(); as decision) {
          <p role="status" class="field-hint">{{ strings[decision] }}</p>
          <button type="button" class="contact-button w-fit" [disabled]="busy()"
                  (click)="change(null, heading)">{{ strings.clear }}</button>
        } @else {
          <div class="flex flex-wrap gap-2">
            <button type="button" class="solid-button" [disabled]="busy()"
                    (click)="change('interested', heading)">{{ feed.interested }}</button>
            <button type="button" class="contact-button" [disabled]="busy()"
                    (click)="change('skip', heading)">{{ feed.skip }}</button>
          </div>
        }
        @if (writeFailed()) {
          <p role="alert" class="field-error">{{ strings.writeFailed }}</p>
        }
      }
    </section>
  `,
})
export class ProjectDecisionComponent implements OnChanges {
  @Input({ required: true }) startup!: Startup
  @Input({ required: true }) profileId!: string

  protected readonly strings = projectDecisionStrings
  protected readonly common = commonStrings
  protected readonly feed = feedStrings
  protected readonly state = signal<'loading' | 'loaded' | 'error'>('loading')
  protected readonly feedback = signal<PersistedFeedbackType | null>(null)
  protected readonly writeFailed = signal(false)

  private readonly interactions = inject(ProjectInteractionsApi)
  private readonly session = inject(PulsoSession)
  private readonly destroyRef = inject(DestroyRef)
  private request?: Subscription

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['startup'] || changes['profileId']) this.load()
  }

  protected busy(): boolean {
    return this.session.isSavingInteraction() || this.session.arePreferencesLoading()
  }

  protected load(): void {
    this.request?.unsubscribe()
    this.writeFailed.set(false)
    this.state.set('loading')
    this.request = this.interactions.getFeedback(this.startup.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ feedbackType }) => {
          this.feedback.set(feedbackType)
          this.state.set('loaded')
        },
        error: () => this.state.set('error'),
      })
  }

  protected async change(decision: Decision | null, heading: HTMLElement): Promise<void> {
    if (this.busy()) return

    const { startup, profileId } = this
    this.writeFailed.set(false)
    const result = await (decision === null
      ? this.session.clearProjectDecision(startup.id, false)
      : this.session.setProjectDecision(startup, decision, false))
    if (this.destroyRef.destroyed || this.profileId !== profileId || this.startup.id !== startup.id) return

    if (result === undefined) {
      this.writeFailed.set(true)
    } else {
      heading.focus()
      this.feedback.set(result)
    }
  }
}
