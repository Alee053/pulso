import { computed, inject, Injectable, signal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { CatalogsApi } from '../data-access/catalogs-api'
import { interestOptions } from '../data-access/interests'
import { StartupsApi } from '../data-access/startups-api'
import { toStartupViewModel } from '../data-access/startup-view-model'

import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway';
import { ProfileApi } from '../data-access/profile-api';
import type { Profile } from '../domain/profile';

import {
  adjustPreferenceWeights,
  getActiveSignals,
  getRecommendationReason,
  rankStartups,
  type PreferenceWeights,
} from '../domain/recommendations'
import { emptyReferenceCatalogs } from '../domain/catalog'
import type { AppView, Decision, InterestId, Startup } from '../domain/startup'

@Injectable({ providedIn: 'root' })
export class PulsoSession {
  private readonly startupsApi = inject(StartupsApi)
  private readonly catalogsApi = inject(CatalogsApi)

    private readonly profileApi = inject(ProfileApi);
    private readonly auth = inject(SupabaseAuthGateway);

  readonly interestOptions = interestOptions

  readonly referenceCatalogs = signal(emptyReferenceCatalogs())
  readonly areCatalogsLoading = signal(false)
  readonly catalogsErrorMessage = signal<string | null>(null)
  readonly hasLoadedReferenceCatalogs = signal(false)
  readonly selectedSectorIds = signal<string[]>([])
  readonly selectedTagIds = signal<string[]>([])
  readonly selectedSkillIds = signal<string[]>([])

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

    // Profile
    readonly profile = signal<Profile | null>(null);
    readonly isProfileLoading = signal(false);
    readonly isProfileSaving = signal(false);
    readonly profileError = signal<string | null>(null);
    readonly profileSuccess = signal<string | null>(null);

    readonly currentUser = computed(() => this.auth.session());

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

  toggleSector(sectorId: string): void {
    this.selectedSectorIds.update((current) => toggleId(current, sectorId))
  }

  toggleTag(tagId: string): void {
    this.selectedTagIds.update((current) => toggleId(current, tagId))
  }

  toggleSkill(skillId: string): void {
    this.selectedSkillIds.update((current) => toggleId(current, skillId))
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
      this.catalogsErrorMessage.set(
        'No pudimos cargar sectores, temas y habilidades. Comprueba tu sesión y que el backend esté disponible.',
      )
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

  toggleInterest(interest: InterestId): void {
    this.selectedInterests.update((current) =>
      current.includes(interest)
        ? current.filter((item) => item !== interest)
        : [...current, interest],
    )
  }

  async startDiscovery(): Promise<void> {
    if (this.isLoading()) {
      return
    }

    this.hasStarted.set(true)
    this.activeView.set('discover')
    await this.loadCatalog()
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
    const sectorIds = new Set(catalogs.sectors.map((item) => item.id))
    const tagIds = new Set(catalogs.tags.map((item) => item.id))
    const skillIds = new Set(catalogs.skills.map((item) => item.id))

    this.selectedSectorIds.update((current) =>
      current.filter((id) => sectorIds.has(id)),
    )
    this.selectedTagIds.update((current) =>
      current.filter((id) => tagIds.has(id)),
    )
    this.selectedSkillIds.update((current) =>
      current.filter((id) => skillIds.has(id)),
    )
  }

    // ============ Profile Methods ============

    async loadProfile(): Promise<void> {
        const user = this.currentUser();
        if (!user) {
            this.profileError.set('Usuario no autenticado');
            return;
        }

        this.isProfileLoading.set(true);
        this.profileError.set(null);
        this.profileSuccess.set(null);

        try {
            const profile = await firstValueFrom(this.profileApi.getProfile(user.id));
            this.profile.set(profile);
        } catch (error) {
            this.profileError.set(
                error instanceof Error ? error.message : 'Error al cargar el perfil'
            );
        } finally {
            this.isProfileLoading.set(false);
        }
    }

    async saveProfile(updatedProfile: Profile): Promise<void> {
        this.isProfileSaving.set(true);
        this.profileError.set(null);
        this.profileSuccess.set(null);

        try {
            const profile = await firstValueFrom(
                this.profileApi.updateProfile(updatedProfile)
            );
            this.profile.set(profile);
            this.profileSuccess.set('Perfil actualizado exitosamente');
            setTimeout(() => {
                this.profileSuccess.set(null);
            }, 3000);
        } catch (error) {
            this.profileError.set(
                error instanceof Error ? error.message : 'Error al guardar el perfil'
            );
        } finally {
            this.isProfileSaving.set(false);
        }
    }

    clearProfileMessages(): void {
        this.profileError.set(null);
        this.profileSuccess.set(null);
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
      this.errorMessage.set(
        'No pudimos cargar los proyectos publicados. Comprueba que el backend esté disponible e inténtalo de nuevo.',
      )
    } finally {
      this.isLoading.set(false)
    }
  }
}

function toggleId(current: string[], id: string): string[] {
  return current.includes(id)
    ? current.filter((item) => item !== id)
    : [...current, id]
}