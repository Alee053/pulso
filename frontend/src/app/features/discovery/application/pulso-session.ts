import { computed, inject, Injectable, signal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { CatalogsApi } from '../data-access/catalogs-api'
import { interestOptions } from '../data-access/interests'
import { PreferencesApi } from '../data-access/preferences-api'
import { StartupsApi } from '../data-access/startups-api'
import { toStartupViewModel } from '../data-access/startup-view-model'

import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'

import {
    adjustPreferenceWeights,
    getActiveSignals,
    getRecommendationReason,
    rankStartups,
    type PreferenceWeights,
} from '../domain/recommendations'
import { emptyReferenceCatalogs } from '../domain/catalog'
import {
    projectStageOptions,
    toInterestIds,
    toOnboardingPreferencesPayload,
    toProjectStages,
    type OnboardingPreferences,
    type ProjectStage,
} from '../domain/preferences'
import type { AppView, Decision, InterestId, Startup } from '../domain/startup'
import { feedStrings } from '../strings/feed.strings'
import { onboardingStrings } from '../strings/onboarding.strings'

@Injectable({ providedIn: 'root' })
export class PulsoSession {
    private readonly startupsApi = inject(StartupsApi)
    private readonly catalogsApi = inject(CatalogsApi)
    private readonly preferencesApi = inject(PreferencesApi)
    private readonly auth = inject(SupabaseAuthGateway)

    readonly interestOptions = interestOptions
    readonly stageOptions = projectStageOptions

    readonly referenceCatalogs = signal(emptyReferenceCatalogs())
    readonly areCatalogsLoading = signal(false)
    readonly catalogsErrorMessage = signal<string | null>(null)
    readonly hasLoadedReferenceCatalogs = signal(false)
    readonly selectedSectorIds = signal<string[]>([])
    readonly selectedTagIds = signal<string[]>([])
    readonly selectedSkillIds = signal<string[]>([])
    readonly selectedStageIds = signal<ProjectStage[]>([])
    readonly arePreferencesLoading = signal(false)
    readonly preferencesErrorMessage = signal<string | null>(null)
    readonly hasLoadedPreferences = signal(false)
    readonly arePreferencesSaving = signal(false)
    readonly preferencesSaveErrorMessage = signal<string | null>(null)
    readonly preferencesSavedMessage = signal<string | null>(null)

    readonly selectedInterests = signal<InterestId[]>([])
    readonly hasStarted = signal(false)
    readonly activeView = signal<AppView>('discover')
    readonly feed = signal<Startup[]>([])
    readonly catalog = signal<Startup[]>([])
    readonly isLoading = signal(false)
    readonly errorMessage = signal<string | null>(null)
    readonly hasLoadedCatalog = signal(false)
    readonly savedIds = signal<string[]>([])
    readonly interestedIds = signal<string[]>([])
    readonly preferenceWeights = signal<PreferenceWeights>({})

    readonly selectedProjectId = signal<string | null>(null)

    readonly currentUser = computed(() => this.auth.session())

    private preferencesSavedMessageTimeout: number | undefined

    readonly currentStartup = computed(() => this.feed()[0])
    readonly savedStartups = computed(() =>
        this.catalog().filter((startup) => this.savedIds().includes(startup.id)),
    )
    readonly interestedStartups = computed(() =>
        this.catalog().filter((startup) =>
            this.interestedIds().includes(startup.id),
        ),
    )
    readonly activeSignals = computed(() =>
        getActiveSignals(this.selectedInterests()),
    )

    resetForUserChange(): void {
        this.referenceCatalogs.set(emptyReferenceCatalogs())
        this.areCatalogsLoading.set(false)
        this.catalogsErrorMessage.set(null)
        this.hasLoadedReferenceCatalogs.set(false)
        this.selectedSectorIds.set([])
        this.selectedTagIds.set([])
        this.selectedSkillIds.set([])
        this.selectedStageIds.set([])
        this.arePreferencesLoading.set(false)
        this.preferencesErrorMessage.set(null)
        this.hasLoadedPreferences.set(false)
        this.arePreferencesSaving.set(false)
        this.preferencesSaveErrorMessage.set(null)
        this.clearPreferencesSavedMessage()
        this.selectedInterests.set([])
        this.hasStarted.set(false)
        this.activeView.set('discover')
        this.feed.set([])
        this.catalog.set([])
        this.isLoading.set(false)
        this.errorMessage.set(null)
        this.hasLoadedCatalog.set(false)
        this.savedIds.set([])
        this.interestedIds.set([])
        this.preferenceWeights.set({})
    }

    toggleSector(sectorId: string): void {
        this.selectedSectorIds.update((current) => toggleId(current, sectorId))
    }

    toggleTag(tagId: string): void {
        this.selectedTagIds.update((current) => toggleId(current, tagId))
    }

    toggleSkill(skillId: string): void {
        this.selectedSkillIds.update((current) => toggleId(current, skillId))
    }

    toggleStage(stageId: ProjectStage): void {
        this.selectedStageIds.update((current) => toggleId(current, stageId))
    }

    async loadReferenceCatalogs(): Promise<void> {
        if (this.areCatalogsLoading()) {
            return
        }

        this.areCatalogsLoading.set(true)
        this.catalogsErrorMessage.set(null)

        try {
            const catalogs = await firstValueFrom(this.catalogsApi.load())
            this.referenceCatalogs.set(catalogs)
            this.hasLoadedReferenceCatalogs.set(true)
            this.retainAvailableCatalogSelections()
        } catch {
            this.referenceCatalogs.set(emptyReferenceCatalogs())
            this.hasLoadedReferenceCatalogs.set(false)
            this.catalogsErrorMessage.set(onboardingStrings.catalogsFailed)
        } finally {
            this.areCatalogsLoading.set(false)
        }
    }

    retryReferenceCatalogs(): void {
        if (this.areCatalogsLoading()) {
            return
        }

        void this.loadReferenceCatalogs()
    }

    async loadOnboardingPreferences(): Promise<void> {
        if (this.arePreferencesLoading()) {
            return
        }

        this.arePreferencesLoading.set(true)
        this.preferencesErrorMessage.set(null)

        try {
            const preferences = await firstValueFrom(this.preferencesApi.load())
            this.applyPreferences(preferences)
            this.hasLoadedPreferences.set(true)
        } catch {
            this.hasLoadedPreferences.set(false)
            this.preferencesErrorMessage.set(onboardingStrings.preferencesLoadFailed)
        } finally {
            this.arePreferencesLoading.set(false)
        }
    }

    retryOnboardingPreferences(): void {
        if (this.arePreferencesLoading()) {
            return
        }

        void this.loadOnboardingPreferences()
    }

    toggleInterest(interest: InterestId): void {
        this.selectedInterests.update((current) =>
            current.includes(interest)
                ? current.filter((item) => item !== interest)
                : [...current, interest],
        )
    }

    async startDiscovery(): Promise<void> {
        if (
            this.isLoading() ||
            this.arePreferencesLoading() ||
            this.arePreferencesSaving() ||
            !this.hasLoadedPreferences() ||
            this.selectedInterests().length === 0
        ) {
            return
        }

        this.arePreferencesSaving.set(true)
        this.preferencesSaveErrorMessage.set(null)
        this.clearPreferencesSavedMessage()

        try {
            const savedPreferences = await firstValueFrom(
                this.preferencesApi.save(
                    toOnboardingPreferencesPayload(
                        this.selectedInterests(),
                        this.selectedSectorIds(),
                        this.selectedStageIds(),
                    ),
                ),
            )
            this.applyPreferences(savedPreferences)
            this.showPreferencesSavedMessage()
            this.hasStarted.set(true)
            this.activeView.set('discover')
            await this.loadCatalog()
        } catch {
            this.preferencesSaveErrorMessage.set(onboardingStrings.preferencesSaveFailed)
        } finally {
            this.arePreferencesSaving.set(false)
        }
    }

    retryPreferencesSave(): void {
        if (this.arePreferencesSaving()) {
            return
        }

        void this.startDiscovery()
    }

    restartDiscovery(): void {
        if (this.isLoading()) {
            return
        }

        this.feed.set(
            rankStartups(
                this.catalog(),
                this.selectedInterests(),
                this.preferenceWeights(),
            ),
        )
        this.activeView.set('discover')
    }

    retryDiscovery(): void {
        if (this.isLoading()) {
            return
        }

        void this.loadCatalog()
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

    openProjectDetail(projectId: string): void {
        this.selectedProjectId.set(projectId)
        this.activeView.set('detail')
    }

    closeProjectDetail(): void {
        this.selectedProjectId.set(null)
        this.activeView.set('discover')
    }

    getReason(startup: Startup): string {
        return getRecommendationReason(
            startup,
            this.selectedInterests(),
            this.preferenceWeights(),
        )
    }

    private retainAvailableCatalogSelections(): void {
        const catalogs = this.referenceCatalogs()
        const sectorIdsByReference = new Map<string, string>()
        for (const sector of catalogs.sectors) {
            sectorIdsByReference.set(sector.id.toLowerCase(), sector.id)
            sectorIdsByReference.set(sector.slug.toLowerCase(), sector.id)
        }
        const tagIds = new Set(catalogs.tags.map((item) => item.id))
        const skillIds = new Set(catalogs.skills.map((item) => item.id))

        this.selectedSectorIds.update((current) =>
            unique(
                current
                    .map((id) => sectorIdsByReference.get(id.toLowerCase()))
                    .filter((id): id is string => id !== undefined),
            ),
        )
        this.selectedTagIds.update((current) =>
            current.filter((id) => tagIds.has(id)),
        )
        this.selectedSkillIds.update((current) =>
            current.filter((id) => skillIds.has(id)),
        )
    }

    private applyPreferences(preferences: OnboardingPreferences): void {
        this.selectedInterests.set(toInterestIds(preferences.intentions))
        this.selectedSectorIds.set([...preferences.preferredSectors])
        this.selectedStageIds.set(toProjectStages(preferences.preferredStages))

        if (this.hasLoadedReferenceCatalogs()) {
            this.retainAvailableCatalogSelections()
        }
    }

    private showPreferencesSavedMessage(): void {
        this.preferencesSavedMessage.set(onboardingStrings.preferencesSaved)
        if (this.preferencesSavedMessageTimeout !== undefined) {
            window.clearTimeout(this.preferencesSavedMessageTimeout)
        }
        this.preferencesSavedMessageTimeout = window.setTimeout(() => {
            this.preferencesSavedMessage.set(null)
            this.preferencesSavedMessageTimeout = undefined
        }, 3000)
    }

    private clearPreferencesSavedMessage(): void {
        this.preferencesSavedMessage.set(null)
        if (this.preferencesSavedMessageTimeout !== undefined) {
            window.clearTimeout(this.preferencesSavedMessageTimeout)
            this.preferencesSavedMessageTimeout = undefined
        }
    }

    private async loadCatalog(): Promise<void> {
        this.isLoading.set(true)
        this.errorMessage.set(null)
        this.hasLoadedCatalog.set(false)
        this.feed.set([])
        this.catalog.set([])

        try {
            const response = await firstValueFrom(this.startupsApi.list())
            const catalog = response.data.map(toStartupViewModel)
            this.catalog.set(catalog)
            this.feed.set(
                rankStartups(catalog, this.selectedInterests(), this.preferenceWeights()),
            )
            this.hasLoadedCatalog.set(true)
        } catch {
            this.errorMessage.set(feedStrings.catalogFailed)
        } finally {
            this.isLoading.set(false)
        }
    }
}

function toggleId<T>(current: T[], id: T): T[] {
    return current.includes(id)
        ? current.filter((item) => item !== id)
        : [...current, id]
}

function unique<T>(values: T[]): T[] {
    return [...new Set(values)]
}