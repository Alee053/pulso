import { computed, Injectable, signal } from '@angular/core'
import { interestOptions } from '../data-access/interests'
import { mockStartups } from '../data-access/mock-startups'
import {
  adjustPreferenceWeights,
  getActiveSignals,
  getRecommendationReason,
  rankStartups,
  type PreferenceWeights,
} from '../domain/recommendations'
import type { AppView, Decision, InterestId, Startup } from '../domain/startup'

@Injectable({ providedIn: 'root' })
export class PulsoSession {
  readonly interestOptions = interestOptions
  readonly mockStartups = mockStartups

  readonly selectedInterests = signal<InterestId[]>([])
  readonly hasStarted = signal(false)
  readonly activeView = signal<AppView>('discover')
  readonly feed = signal<Startup[]>([])
  readonly savedIds = signal<string[]>([])
  readonly interestedIds = signal<string[]>([])
  readonly preferenceWeights = signal<PreferenceWeights>({})

  readonly currentStartup = computed(() => this.feed()[0])
  readonly savedStartups = computed(() =>
    this.mockStartups.filter((startup) => this.savedIds().includes(startup.id)),
  )
  readonly interestedStartups = computed(() =>
    this.mockStartups.filter((startup) =>
      this.interestedIds().includes(startup.id),
    ),
  )
  readonly activeSignals = computed(() =>
    getActiveSignals(this.selectedInterests()),
  )

  toggleInterest(interest: InterestId): void {
    this.selectedInterests.update((current) =>
      current.includes(interest)
        ? current.filter((item) => item !== interest)
        : [...current, interest],
    )
  }

  startDiscovery(): void {
    this.feed.set(rankStartups(this.mockStartups, this.selectedInterests()))
    this.hasStarted.set(true)
    this.activeView.set('discover')
  }

  restartDiscovery(): void {
    this.feed.set(
      rankStartups(
        this.mockStartups,
        this.selectedInterests(),
        this.preferenceWeights(),
      ),
    )
    this.activeView.set('discover')
  }

  toggleSaved(startupId: string): void {
    this.savedIds.update((current) =>
      current.includes(startupId)
        ? current.filter((id) => id !== startupId)
        : [...current, startupId],
    )
  }

  makeDecision(decision: Decision): void {
    const currentStartup = this.currentStartup()

    if (!currentStartup) {
      return
    }

    const nextWeights = adjustPreferenceWeights(
      currentStartup,
      decision,
      this.preferenceWeights(),
    )

    if (decision === 'interested') {
      this.interestedIds.update((current) =>
        current.includes(currentStartup.id)
          ? current
          : [...current, currentStartup.id],
      )
    }

    this.preferenceWeights.set(nextWeights)
    this.feed.update((current) =>
      rankStartups(
        current.filter((startup) => startup.id !== currentStartup.id),
        this.selectedInterests(),
        nextWeights,
      ),
    )
  }

  goToView(view: AppView): void {
    this.activeView.set(view)
  }

  getReason(startup: Startup): string {
    return getRecommendationReason(
      startup,
      this.selectedInterests(),
      this.preferenceWeights(),
    )
  }
}
