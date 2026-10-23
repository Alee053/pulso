import { computed, inject, Injectable, signal, type WritableSignal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { CatalogsApi } from '../data-access/catalogs-api'
import { preferenceContract } from '../data-access/discovery.catalog.contract'
import { PreferencesApi } from '../data-access/preferences-api'
import { ProjectInteractionsApi } from '../data-access/project-interactions-api'
import { ProjectSearchApi, type SearchFilters } from '../data-access/project-search-api'

import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'

import {
    adjustPreferenceWeights,
    getActiveSignals,
    getRecommendationReason,
    type PreferenceWeights,
} from '../domain/recommendations'
import { emptyReferenceCatalogs } from '../domain/catalog'
import {
    intentionOptions,
    isProficiency,
    projectStageOptions,
    toInterestIds,
    toOnboardingPreferencesPayload,
    toProjectStages,
    type OnboardingPreferences,
    type ProjectStage,
    type OnboardingIntention,
    type ProfileSkill,
} from '../domain/preferences'
import type { AppView, Decision, Startup } from '../domain/startup'
import { feedStrings } from '../strings/feed.strings'
import { onboardingStrings } from '../strings/onboarding.strings'
import { savedStrings } from '../strings/saved.strings'

@Injectable({ providedIn: 'root' })
export class PulsoSession {
    private readonly projectInteractionsApi = inject(ProjectInteractionsApi)
    private readonly catalogsApi = inject(CatalogsApi)
    private readonly preferencesApi = inject(PreferencesApi)
    private readonly projectSearchApi = inject(ProjectSearchApi)
    private readonly auth = inject(SupabaseAuthGateway)

    readonly intentionOptions = computed(() => intentionOptions.filter(
        (option) => this.referenceCatalogs().intentions.includes(option.id),
    ))
    readonly stageOptions = computed(() => projectStageOptions.filter(
        (option) => this.referenceCatalogs().stages.includes(option.id),
    ))

    readonly referenceCatalogs = signal(emptyReferenceCatalogs())
    readonly areCatalogsLoading = signal(false)
    readonly catalogsErrorMessage = signal<string | null>(null)
    readonly hasLoadedReferenceCatalogs = signal(false)
    readonly selectedSectorIds = signal<string[]>([])
    readonly selectedTagIds = signal<string[]>([])
    readonly selectedTags = computed(() => this.referenceCatalogs().tags.filter(
        (tag) => this.selectedTagIds().includes(tag.id),
    ))
    readonly selectedSkills = signal<ProfileSkill[]>([])
    readonly selectedSkillIds = computed(() => this.selectedSkills().map((skill) => skill.skillId))
    readonly selectedStageIds = signal<ProjectStage[]>([])
    readonly arePreferencesLoading = signal(false)
    readonly preferencesErrorMessage = signal<string | null>(null)
    readonly hasLoadedPreferences = signal(false)
    readonly arePreferencesSaving = signal(false)
    readonly preferencesSaveErrorMessage = signal<string | null>(null)
    readonly preferencesSavedMessage = signal<string | null>(null)

    readonly selectedIntentions = signal<OnboardingIntention[]>([])
    readonly selectedInterests = computed(() => toInterestIds(this.selectedIntentions()))
    readonly onboardingCompletedAt = signal<string | null>(null)
    readonly isEditingPreferences = signal(false)
    readonly preferenceValidationMessage = signal<string | null>(null)
    private savedPreferences: OnboardingPreferences | null = null
    readonly hasStarted = signal(false)
    readonly activeView = signal<AppView>('discover')
    readonly feed = signal<Startup[]>([])
    readonly catalog = signal<Startup[]>([])
    readonly isLoading = signal(false)
    readonly errorMessage = signal<string | null>(null)
    readonly hasLoadedCatalog = signal(false)
    readonly feedHasMore = signal(false)
    readonly savedIds = signal<string[]>([])
    readonly interestedIds = signal<string[]>([])
    readonly dismissedIds = signal<string[]>([])
    readonly savedProjects = signal<Startup[]>([])
    readonly interestedProjects = signal<Startup[]>([])
    readonly savedCount = signal(0)
    readonly interestedCount = signal(0)
    readonly savedHasMore = signal(false)
    readonly interestedHasMore = signal(false)
    readonly hasLoadedSavedProjects = signal(false)
    readonly hasLoadedInterestedProjects = signal(false)
    readonly isLoadingSavedProjects = signal(false)
    readonly isLoadingInterestedProjects = signal(false)
    readonly savedProjectsErrorMessage = signal<string | null>(null)
    readonly interestedProjectsErrorMessage = signal<string | null>(null)
    readonly isSavingInteraction = signal(false)
    readonly interactionErrorMessage = signal<string | null>(null)
    readonly preferenceWeights = signal<PreferenceWeights>({})

    // === Búsqueda y Filtros ===
    readonly searchQuery = signal<string>('')
    readonly isSearching = signal(false)
    readonly searchResults = signal<Startup[]>([])
    readonly searchHasMore = signal(false)
    readonly searchError = signal<string | null>(null)
    private searchOffset = 0

    readonly selectedProjectId = signal<string | null>(null)

    readonly currentUser = computed(() => this.auth.session())

    private preferencesSavedMessageTimeout: number | undefined
    private pendingInteractionAction: (() => Promise<void>) | null = null
    private requestGeneration = 0
    private feedNextCursor: string | null = null
    private savedProjectsRevision = 0
    private interestedProjectsRevision = 0

    readonly currentStartup = computed(() => this.feed()[0])
    readonly savedStartups = computed(() => this.savedProjects())
    readonly interestedStartups = computed(() => this.interestedProjects())
    readonly activeSignals = computed(() =>
        getActiveSignals(this.selectedInterests()),
    )

    resetForUserChange(): void {
        this.requestGeneration += 1
        this.savedProjectsRevision += 1
        this.interestedProjectsRevision += 1
        this.referenceCatalogs.set(emptyReferenceCatalogs())
        this.areCatalogsLoading.set(false)
        this.catalogsErrorMessage.set(null)
        this.hasLoadedReferenceCatalogs.set(false)
        this.selectedSectorIds.set([])
        this.selectedTagIds.set([])
        this.selectedSkills.set([])
        this.selectedStageIds.set([])
        this.arePreferencesLoading.set(false)
        this.preferencesErrorMessage.set(null)
        this.hasLoadedPreferences.set(false)
        this.arePreferencesSaving.set(false)
        this.preferencesSaveErrorMessage.set(null)
        this.clearPreferencesSavedMessage()
        this.selectedIntentions.set([])
        this.onboardingCompletedAt.set(null)
        this.isEditingPreferences.set(false)
        this.preferenceValidationMessage.set(null)
        this.savedPreferences = null
        this.hasStarted.set(false)
        this.activeView.set('discover')
        this.feed.set([])
        this.catalog.set([])
        this.isLoading.set(false)
        this.errorMessage.set(null)
        this.hasLoadedCatalog.set(false)
        this.savedIds.set([])
        this.interestedIds.set([])
        this.dismissedIds.set([])
        this.feedHasMore.set(false)
        this.feedNextCursor = null
        this.savedProjects.set([])
        this.interestedProjects.set([])
        this.savedCount.set(0)
        this.interestedCount.set(0)
        this.savedHasMore.set(false)
        this.interestedHasMore.set(false)
        this.hasLoadedSavedProjects.set(false)
        this.hasLoadedInterestedProjects.set(false)
        this.isLoadingSavedProjects.set(false)
        this.isLoadingInterestedProjects.set(false)
        this.savedProjectsErrorMessage.set(null)
        this.interestedProjectsErrorMessage.set(null)
        this.isSavingInteraction.set(false)
        this.interactionErrorMessage.set(null)
        this.pendingInteractionAction = null
        this.preferenceWeights.set({})
        this.selectedProjectId.set(null)
        this.clearSearch()
    }

    async loadProjectCollections(): Promise<void> {
        const generation = this.requestGeneration
        await this.loadSavedProjects(false)
        if (generation !== this.requestGeneration) {
            return
        }
        await this.loadInterestedProjects(false)
    }

    async loadSavedProjects(loadMore = false): Promise<void> {
        if (this.isLoadingSavedProjects()) {
            return
        }
        const generation = this.requestGeneration
        const revision = this.savedProjectsRevision
        const offset = loadMore ? this.savedProjects().length : 0
        if (!loadMore) {
            this.savedProjects.set([])
            this.savedIds.set([])
            this.savedCount.set(0)
            this.savedHasMore.set(false)
            this.savedProjectsErrorMessage.set(null)
        }
        this.isLoadingSavedProjects.set(true)
        this.savedProjectsErrorMessage.set(null)
        try {
            const page = await firstValueFrom(
                this.projectInteractionsApi.loadSavedProjects(20, offset),
            )
            if (generation !== this.requestGeneration || revision !== this.savedProjectsRevision) {
                return
            }
            this.savedProjects.update((current) => mergeProjects(current, page.data, loadMore))
            this.savedIds.set(unique(this.savedProjects().map((startup) => startup.id)))
            this.savedCount.set(page.meta.totalCount)
            this.savedHasMore.set(page.meta.hasMore)
            this.hasLoadedSavedProjects.set(true)
        } catch {
            if (generation === this.requestGeneration && revision === this.savedProjectsRevision) {
                this.savedProjectsErrorMessage.set(savedStrings.savedLoadFailed)
            }
        } finally {
            if (generation === this.requestGeneration) {
                this.isLoadingSavedProjects.set(false)
                if (revision !== this.savedProjectsRevision) {
                    void this.loadSavedProjects(false)
                }
            }
        }
    }

    async loadInterestedProjects(loadMore = false): Promise<void> {
        if (this.isLoadingInterestedProjects()) {
            return
        }
        const generation = this.requestGeneration
        const revision = this.interestedProjectsRevision
        const offset = loadMore ? this.interestedProjects().length : 0
        if (!loadMore) {
            this.interestedProjects.set([])
            this.interestedIds.set([])
            this.interestedCount.set(0)
            this.interestedHasMore.set(false)
            this.interestedProjectsErrorMessage.set(null)
        }
        this.isLoadingInterestedProjects.set(true)
        this.interestedProjectsErrorMessage.set(null)
        try {
            const page = await firstValueFrom(
                this.projectInteractionsApi.loadInterestedProjects(20, offset),
            )
            if (generation !== this.requestGeneration || revision !== this.interestedProjectsRevision) {
                return
            }
            this.interestedProjects.update((current) => mergeProjects(current, page.data, loadMore))
            this.interestedIds.set(unique(this.interestedProjects().map((startup) => startup.id)))
            this.interestedCount.set(page.meta.totalCount)
            this.interestedHasMore.set(page.meta.hasMore)
            this.hasLoadedInterestedProjects.set(true)
        } catch {
            if (generation === this.requestGeneration && revision === this.interestedProjectsRevision) {
                this.interestedProjectsErrorMessage.set(savedStrings.interestedLoadFailed)
            }
        } finally {
            if (generation === this.requestGeneration) {
                this.isLoadingInterestedProjects.set(false)
                if (revision !== this.interestedProjectsRevision) {
                    void this.loadInterestedProjects(false)
                }
            }
        }
    }

    retrySavedProjects(): void {
        void this.loadSavedProjects(this.hasLoadedSavedProjects())
    }

    retryInterestedProjects(): void {
        void this.loadInterestedProjects(this.hasLoadedInterestedProjects())
    }

    loadMoreSavedProjects(): void {
        void this.loadSavedProjects(true)
    }

    loadMoreInterestedProjects(): void {
        void this.loadInterestedProjects(true)
    }

    toggleSector(sectorId: string): void {
        if (this.canEditPreferences()) this.toggleSelection(this.selectedSectorIds, sectorId)
    }

    toggleTag(tagId: string): void {
        if (this.canEditPreferences()) this.toggleSelection(this.selectedTagIds, tagId)
    }

    toggleSkill(skillId: string): void {
        if (!this.canEditPreferences()) return
        if (!this.selectedSkillIds().includes(skillId) && this.selectedSkills().length >= preferenceContract.maxItemsPerField) {
            this.preferenceValidationMessage.set(onboardingStrings.selectionLimit(preferenceContract.maxItemsPerField))
            return
        }
        this.selectedSkills.update((current) => current.some((skill) => skill.skillId === skillId)
            ? current.filter((skill) => skill.skillId !== skillId)
            : [...current, { skillId, proficiency: null }])
        this.preferenceValidationMessage.set(null)
    }

    toggleStage(stageId: ProjectStage): void {
        if (this.canEditPreferences()) this.toggleSelection(this.selectedStageIds, stageId)
    }

    async loadReferenceCatalogs(): Promise<void> {
        if (this.areCatalogsLoading()) {
            return
        }

        const generation = this.requestGeneration
        this.areCatalogsLoading.set(true)
        this.catalogsErrorMessage.set(null)

        try {
            const catalogs = await firstValueFrom(this.catalogsApi.load())
            if (generation !== this.requestGeneration) {
                return
            }
            this.referenceCatalogs.set(catalogs)
            this.hasLoadedReferenceCatalogs.set(true)
            this.retainAvailableCatalogSelections()
        } catch {
            if (generation !== this.requestGeneration) {
                return
            }
            this.referenceCatalogs.set(emptyReferenceCatalogs())
            this.hasLoadedReferenceCatalogs.set(false)
            this.catalogsErrorMessage.set(onboardingStrings.catalogsFailed)
        } finally {
            if (generation === this.requestGeneration) {
                this.areCatalogsLoading.set(false)
            }
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

        const generation = this.requestGeneration
        this.arePreferencesLoading.set(true)
        this.preferencesErrorMessage.set(null)

        try {
            const preferences = await firstValueFrom(this.preferencesApi.load())
            if (generation !== this.requestGeneration) {
                return
            }
            this.savedPreferences = preferences
            this.applyPreferences(preferences)
            this.hasLoadedPreferences.set(true)
            if (preferences.onboardingCompletedAt) {
                this.hasStarted.set(true)
                await this.loadCatalog()
            }
        } catch {
            if (generation !== this.requestGeneration) {
                return
            }
            this.hasLoadedPreferences.set(false)
            this.preferencesErrorMessage.set(onboardingStrings.preferencesLoadFailed)
        } finally {
            if (generation === this.requestGeneration) {
                this.arePreferencesLoading.set(false)
            }
        }
    }

    retryOnboardingPreferences(): void {
        if (this.arePreferencesLoading()) {
            return
        }

        void this.loadOnboardingPreferences()
    }

    toggleIntention(intention: OnboardingIntention): void {
        if (this.canEditPreferences()) this.toggleSelection(this.selectedIntentions, intention)
    }

    changeSkillProficiency(change: { skillId: string; proficiency: string }): void {
        if (!this.canEditPreferences() || !isProficiency(change.proficiency)) return
        const proficiency = change.proficiency
        this.selectedSkills.update((current) => current.map((skill) => skill.skillId === change.skillId
            ? { ...skill, proficiency } : skill))
        this.preferenceValidationMessage.set(null)
    }

    editPreferences(): void {
        if (!this.savedPreferences || this.arePreferencesSaving()) return
        this.applyPreferences(this.savedPreferences)
        this.preferencesSaveErrorMessage.set(null)
        this.preferenceValidationMessage.set(null)
        this.isEditingPreferences.set(true)
        void this.loadReferenceCatalogs()
    }

    cancelPreferencesEditing(): void {
        if (this.arePreferencesSaving() || !this.savedPreferences) return
        this.applyPreferences(this.savedPreferences)
        this.preferencesSaveErrorMessage.set(null)
        this.preferenceValidationMessage.set(null)
        this.isEditingPreferences.set(false)
        this.activeView.set('profile')
    }

    private canEditPreferences(): boolean {
        return this.hasLoadedPreferences() && this.hasLoadedReferenceCatalogs()
            && !this.arePreferencesLoading() && !this.areCatalogsLoading() && !this.arePreferencesSaving()
    }

    private toggleSelection<T>(selection: WritableSignal<T[]>, id: T): void {
        if (!selection().includes(id) && selection().length >= preferenceContract.maxItemsPerField) {
            this.preferenceValidationMessage.set(onboardingStrings.selectionLimit(preferenceContract.maxItemsPerField))
            return
        }
        selection.update((current) => toggleId(current, id))
        this.preferenceValidationMessage.set(null)
    }

    async search(query: string, loadMore = false): Promise<void> {
        this.searchQuery.set(query)

        if (!query.trim()) {
            this.isSearching.set(false)
            this.searchResults.set([])
            this.searchError.set(null)
            return
        }

        this.isSearching.set(true)
        this.searchError.set(null)

        if (!loadMore) {
            this.searchOffset = 0
            this.searchResults.set([])
        }

        try {
            const response = await firstValueFrom(
                this.projectSearchApi.search({ query }, this.searchOffset, 20)
            )

            const mappedResults: Startup[] = response.data.map(item => ({
                id: item.id,
                name: item.name,
                logoMark: item.name.charAt(0).toUpperCase(),
                city: item.city ?? '',
                sector: item.sector,
                pitch: item.tagline,
                description: '',
                stage: item.stage,
                tags: [],
                badges: [],
                image: item.imageUrl ?? '',
                imageAlt: item.imageAlt ?? item.name,
                accent: '#a9e1ff',
                founders: [],
                lookingFor: [],
                founded: '',
                teamSize: '',
                traction: '',
                website: '',
                socials: [],
                recommendation: '',
                metrics: [],
                popularity: 0,
                exploration: 0
            }))

            this.searchResults.update(current => loadMore ? [...current, ...mappedResults] : mappedResults)
            this.searchOffset += response.meta.limit
            this.searchHasMore.set(this.searchResults().length < response.meta.totalCount)
        } catch {
            this.searchError.set('No pudimos realizar la búsqueda. Intenta de nuevo.')
        } finally {
            this.isSearching.set(false)
        }
    }

    clearSearch(): void {
        this.searchQuery.set('')
        this.isSearching.set(false)
        this.searchResults.set([])
        this.searchError.set(null)
        this.searchOffset = 0
    }

    async startDiscovery(): Promise<void> {
        if (
            this.isLoading() ||
            this.arePreferencesLoading() ||
            this.arePreferencesSaving() ||
            !this.hasLoadedPreferences() ||
            !this.hasLoadedReferenceCatalogs() || this.areCatalogsLoading() ||
            this.selectedIntentions().length === 0
        ) {
            return
        }

        if (this.selectedSkills().some((skill) => !isProficiency(skill.proficiency))) {
            this.preferenceValidationMessage.set(onboardingStrings.proficiencyRequired)
            return
        }
        this.preferenceValidationMessage.set(null)
        const editing = this.isEditingPreferences()
        const generation = this.requestGeneration
        this.arePreferencesSaving.set(true)
        this.preferencesSaveErrorMessage.set(null)
        this.clearPreferencesSavedMessage()

        try {
            const savedPreferences = await firstValueFrom(
                this.preferencesApi.save(
                    toOnboardingPreferencesPayload(
                        this.selectedIntentions(),
                        this.selectedSectorIds(),
                        this.selectedStageIds(),
                        this.selectedTagIds(),
                        this.selectedSkills(),
                    ),
                ),
            )
            if (generation !== this.requestGeneration) {
                return
            }
            if (!savedPreferences.onboardingCompletedAt) throw new Error('Onboarding completion was not confirmed')
            this.savedPreferences = savedPreferences
            this.applyPreferences(savedPreferences)
            this.isEditingPreferences.set(false)
            this.showPreferencesSavedMessage()
            this.hasStarted.set(true)
            this.activeView.set(editing ? 'profile' : 'discover')
            this.feedNextCursor = null
            await this.loadCatalog()
        } catch {
            if (generation === this.requestGeneration) {
                this.preferencesSaveErrorMessage.set(onboardingStrings.preferencesSaveFailed)
            }
        } finally {
            if (generation === this.requestGeneration) {
                this.arePreferencesSaving.set(false)
            }
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

        if (this.feed().length === 0 && this.feedHasMore()) {
            void this.loadCatalog()
            return
        }

        this.feed.set(
            this.catalog().filter(
                (startup) => !this.interestedIds().includes(startup.id)
                    && !this.dismissedIds().includes(startup.id),
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

    async toggleSaved(startupId: string): Promise<void> {
        if (this.isSavingInteraction()) {
            return
        }
        const startup = this.catalog().find((item) => item.id === startupId)
            ?? this.savedProjects().find((item) => item.id === startupId)
        if (!startup) {
            return
        }
        const shouldSave = !this.savedIds().includes(startupId)
        const generation = this.requestGeneration
        await this.runInteraction(async () => {
            if (shouldSave) {
                await firstValueFrom(this.projectInteractionsApi.saveProject(startupId))
                if (generation !== this.requestGeneration) {
                    return
                }
                this.savedProjectsRevision += 1
                this.savedProjects.update((current) => prependProject(current, startup))
                this.savedIds.update((current) => unique([...current, startupId]))
                this.savedCount.update((count) => count + 1)
            } else {
                await firstValueFrom(this.projectInteractionsApi.unsaveProject(startupId))
                if (generation !== this.requestGeneration) {
                    return
                }
                this.savedProjectsRevision += 1
                this.savedProjects.update((current) => current.filter((item) => item.id !== startupId))
                this.savedIds.update((current) => current.filter((id) => id !== startupId))
                this.savedCount.update((count) => Math.max(0, count - 1))
            }
        }, feedStrings.interactionSaveFailed)
    }

    async makeDecision(decision: Decision): Promise<void> {
        const currentStartup = this.currentStartup()

        if (!currentStartup || this.isSavingInteraction()) {
            return
        }

        const nextWeights = adjustPreferenceWeights(
            currentStartup,
            decision,
            this.preferenceWeights(),
        )

        const feedbackType = decision === 'interested' ? 'INTERESTED' : 'DISMISSED'
        const generation = this.requestGeneration
        await this.runInteraction(async () => {
            await firstValueFrom(this.projectInteractionsApi.setFeedback(
                currentStartup.id,
                feedbackType,
                nextWeights,
            ))
            if (generation !== this.requestGeneration) {
                return
            }
            this.preferenceWeights.set(nextWeights)
            if (decision === 'interested') {
                if (!this.interestedIds().includes(currentStartup.id)) {
                    this.interestedProjectsRevision += 1
                    this.interestedIds.update((current) => [...current, currentStartup.id])
                    this.interestedProjects.update((current) => prependProject(current, currentStartup))
                    this.interestedCount.update((count) => count + 1)
                }
            } else {
                this.dismissedIds.update((current) => unique([...current, currentStartup.id]))
            }
            this.feed.set(
                this.feed().filter((startup) => startup.id !== currentStartup.id),
            )
            if (this.feed().length === 0 && this.feedHasMore()) {
                void this.loadCatalog()
            }
        }, feedStrings.interactionFeedbackFailed)
    }

    async clearInterest(projectId: string): Promise<void> {
        if (this.isSavingInteraction() || !this.interestedIds().includes(projectId)) {
            return
        }
        const generation = this.requestGeneration
        await this.runInteraction(async () => {
            await firstValueFrom(this.projectInteractionsApi.clearFeedback(projectId))
            if (generation !== this.requestGeneration) {
                return
            }
            this.interestedProjectsRevision += 1
            this.interestedIds.update((current) => current.filter((id) => id !== projectId))
            this.interestedProjects.update((current) => current.filter((item) => item.id !== projectId))
            this.interestedCount.update((count) => Math.max(0, count - 1))
            this.feedNextCursor = null
            void this.loadCatalog()
        }, savedStrings.clearInterestFailed)
    }

    retryInteraction(): void {
        if (this.pendingInteractionAction) {
            void this.runInteraction(
                this.pendingInteractionAction,
                this.interactionErrorMessage() ?? feedStrings.interactionFeedbackFailed,
            )
        }
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
        this.selectedSkills.update((current) => current.filter((skill) => skillIds.has(skill.skillId)))
        this.selectedIntentions.update((current) => current.filter((id) => catalogs.intentions.includes(id)))
        this.selectedStageIds.update((current) => current.filter((id) => catalogs.stages.includes(id)))
    }

    private applyPreferences(preferences: OnboardingPreferences): void {
        this.selectedIntentions.set([...preferences.intentions])
        this.onboardingCompletedAt.set(preferences.onboardingCompletedAt)
        this.selectedSectorIds.set([...preferences.preferredSectors])
        this.selectedStageIds.set(toProjectStages(preferences.preferredStages))
        this.selectedTagIds.set([...preferences.preferredTags])
        this.selectedSkills.set(preferences.skills.map((skill) => ({ ...skill })))
        this.preferenceWeights.set({ ...preferences.recommendationWeights })

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
        const generation = this.requestGeneration
        this.isLoading.set(true)
        this.errorMessage.set(null)
        this.hasLoadedCatalog.set(false)
        this.feedHasMore.set(false)
        this.feed.set([])
        this.catalog.set([])

        try {
            const response = await firstValueFrom(
                this.projectInteractionsApi.loadDiscoveryFeed(undefined, this.feedNextCursor),
            )
            if (generation !== this.requestGeneration) {
                return
            }
            const catalog = response.data.map((item) => item.startup)
            this.catalog.set(catalog)
            this.savedIds.update((current) => unique([
                ...current,
                ...response.data.filter((item) => item.saved).map((item) => item.startup.id),
            ]))
            this.feed.set(catalog)
            this.feedHasMore.set(response.meta.hasMore)
            this.feedNextCursor = response.meta.nextCursor
            this.hasLoadedCatalog.set(true)
        } catch {
            if (generation === this.requestGeneration) {
                this.errorMessage.set(feedStrings.catalogFailed)
            }
        } finally {
            if (generation === this.requestGeneration) {
                this.isLoading.set(false)
            }
        }
    }

    private async runInteraction(
        action: () => Promise<void>,
        failureMessage: string,
    ): Promise<void> {
        if (this.isSavingInteraction()) {
            return
        }
        const generation = this.requestGeneration
        this.isSavingInteraction.set(true)
        this.interactionErrorMessage.set(null)
        this.pendingInteractionAction = action
        try {
            await action()
            if (generation === this.requestGeneration) {
                this.pendingInteractionAction = null
            }
        } catch {
            if (generation === this.requestGeneration) {
                this.interactionErrorMessage.set(failureMessage)
            }
        } finally {
            if (generation === this.requestGeneration) {
                this.isSavingInteraction.set(false)
            }
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

function mergeProjects(current: Startup[], incoming: Startup[], append: boolean): Startup[] {
    const projects = append ? [...current, ...incoming] : incoming
    const seen = new Set<string>()
    return projects.filter((project) => {
        if (seen.has(project.id)) {
            return false
        }
        seen.add(project.id)
        return true
    })
}

function prependProject(current: Startup[], incoming: Startup): Startup[] {
    return [incoming, ...current.filter((startup) => startup.id !== incoming.id)].slice(0, 20)
}