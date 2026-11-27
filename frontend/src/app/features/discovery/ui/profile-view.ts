import { Component, EventEmitter, computed, inject, Input, OnInit, Output, signal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import { SupabaseAuthGateway } from '../../../core/auth/supabase-auth.gateway'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import {
    ProfileApi,
    type CurrentUserDto,
    type ProfilePatchRequest,
} from '../data-access/profile-api'
import { profileContract } from '../data-access/project.contract'
import type { IntentionOption, OnboardingIntention } from '../domain/preferences'
import type { TagCatalogItem } from '../domain/catalog'
import { profileStrings } from '../strings/profile.strings'
import { FeatureFlagSettingsComponent } from './feature-flag-settings'
import { ProfileBlocksComponent } from './profile-blocks'
import { ProjectReviewsComponent } from './project-reviews'

@Component({
    selector: 'pulso-profile-view',
    standalone: true,
    imports: [IconComponent, FeatureFlagSettingsComponent, ProjectReviewsComponent, ProfileBlocksComponent],
    templateUrl: './profile-view.html',
    styleUrl: './profile-view.css',
})
export class ProfileViewComponent implements OnInit {
    readonly strings = profileStrings
    readonly common = commonStrings
    readonly profileContract = profileContract

    @Input() intentionOptions: readonly IntentionOption[] = []
    @Input() selectedIntentions: OnboardingIntention[] = []
    @Input() preferencesBusy = false
    @Input() selectedTags: readonly TagCatalogItem[] = []
    @Output() preferencesEditRequested = new EventEmitter<void>()

    readonly auth = inject(SupabaseAuthGateway)
    private readonly profileApi = inject(ProfileApi)
    readonly profile = signal<CurrentUserDto | null>(null)
    readonly isLoading = signal(true)
    readonly isSaving = signal(false)
    readonly errorMessage = signal<string | null>(null)
    readonly validationMessage = signal<string | null>(null)
    readonly signingOut = signal(false)
    readonly signOutFailed = signal(false)
    private readonly section = signal<'profile' | 'administration'>('profile')
    readonly administrationVisited = signal(false)
    readonly canReview = computed(() => {
        const profile = this.profile()?.profile
        return profile?.accountStatus === 'ACTIVE'
            && (profile.platformRole === 'MODERATOR' || profile.platformRole === 'ADMIN')
    })
    readonly hasAdministration = computed(() => this.canReview() || this.profile()?.profile.platformRole === 'ADMIN')
    readonly administrationOpen = computed(() => this.hasAdministration() && this.section() === 'administration')
    readonly feedbackStatus = signal<'saved' | 'saveFailed' | null>(null)
    readonly feedbackMessage = computed(() => {
        const status = this.feedbackStatus()
        return status ? this.strings[status] : null
    })

    displayName = ''
    shortBio = ''
    city = ''
    department = ''

    ngOnInit(): void {
        void this.loadProfile()
    }

    get selectedOptions(): readonly IntentionOption[] {
        return this.intentionOptions.filter((option) =>
            this.selectedIntentions.includes(option.id),
        )
    }

    get avatarInitial(): string {
        return (this.profile()?.profile.displayName?.trim().charAt(0) || this.strings.account.charAt(0)).toUpperCase()
    }

    async loadProfile(): Promise<void> {
        if (this.isSaving() || this.signingOut()) {
            return
        }

        this.isLoading.set(true)
        this.errorMessage.set(null)
        this.feedbackStatus.set(null)

        try {
            const currentUser = await firstValueFrom(this.profileApi.getCurrent())
            this.profile.set(currentUser)
            this.hydrateForm(currentUser)
        } catch {
            this.profile.set(null)
            this.errorMessage.set(this.strings.loadFailed)
        } finally {
            this.isLoading.set(false)
        }
    }

    async saveProfile(event?: SubmitEvent): Promise<void> {
        event?.preventDefault()
        if (this.isSaving() || this.signingOut()) {
            return
        }

        const normalizedDisplayName = this.displayName.trim()
        if (!normalizedDisplayName) {
            this.validationMessage.set(this.strings.displayNameRequired)
            return
        }

        this.validationMessage.set(null)
        this.feedbackStatus.set(null)
        this.isSaving.set(true)

        try {
            const patch: ProfilePatchRequest = {
                displayName: normalizedDisplayName,
            }
            addOptionalField(patch, 'shortBio', this.shortBio)
            addOptionalField(patch, 'city', this.city)
            addOptionalField(patch, 'department', this.department)

            const currentUser = await firstValueFrom(this.profileApi.update(patch))
            this.profile.set(currentUser)
            this.hydrateForm(currentUser)
            this.feedbackStatus.set('saved')
        } catch {
            this.feedbackStatus.set('saveFailed')
        } finally {
            this.isSaving.set(false)
        }
    }

    selectSection(section: 'profile' | 'administration'): void {
        this.section.set(section)
        if (section === 'administration') this.administrationVisited.set(true)
    }

    async signOut(): Promise<void> {
        if (this.isSaving() || this.signingOut()) {
            return
        }

        this.signingOut.set(true)
        this.signOutFailed.set(false)
        try {
            await this.auth.signOut()
        } catch {
            this.signOutFailed.set(true)
        } finally {
            this.signingOut.set(false)
        }
    }

    private hydrateForm(currentUser: CurrentUserDto): void {
        this.displayName = currentUser.profile.displayName ?? ''
        this.shortBio = currentUser.profile.shortBio ?? ''
        this.city = currentUser.profile.city ?? ''
        this.department = currentUser.profile.department ?? ''
        this.validationMessage.set(null)
    }
}

function addOptionalField(
    patch: ProfilePatchRequest,
    field: 'shortBio' | 'city' | 'department',
    value: string,
): void {
    const normalized = value.trim()
    if (normalized) {
        patch[field] = normalized
    }
}
