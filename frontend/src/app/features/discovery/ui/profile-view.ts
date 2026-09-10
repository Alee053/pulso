import { CommonModule } from '@angular/common'
import { Component, Input, inject, OnInit, effect } from '@angular/core'
import { FormBuilder, ReactiveFormsModule, Validators, FormGroup } from '@angular/forms'

import { IconComponent } from '../../../shared/icon/icon'
import { PulsoSession } from '../application/pulso-session'
import { ProfileValidation } from '../domain/profile'
import type { InterestId, InterestOption } from '../domain/startup'

@Component({
  selector: 'pulso-profile-view',
  standalone: true,
    imports: [CommonModule, IconComponent, ReactiveFormsModule],
  templateUrl: './profile-view.html',
})
export class ProfileViewComponent implements OnInit {
  @Input() interestOptions: readonly InterestOption[] = []
  @Input() selectedInterests: InterestId[] = []

    private readonly session = inject(PulsoSession)
    private readonly fb = inject(FormBuilder)

    profileForm!: FormGroup
    readonly profile = this.session.profile
    readonly isLoading = this.session.isProfileLoading
    readonly isSaving = this.session.isProfileSaving
    readonly errorMessage = this.session.profileError
    readonly successMessage = this.session.profileSuccess

  get selectedOptions(): readonly InterestOption[] {
    return this.interestOptions.filter((option) =>
      this.selectedInterests.includes(option.id),
    )
    }

    ngOnInit(): void {
        this.initForm()
        this.loadProfile()

        effect(() => {
            const profile = this.session.profile()
            if (profile && !this.profileForm.dirty) {
                this.profileForm.patchValue({
                    visibleName: profile.visibleName,
                    bio: profile.bio,
                    city: profile.city,
                    department: profile.department,
                })
            }
        })
    }
        

    private initForm(): void {
        this.profileForm = this.fb.group({
            visibleName: [
                '',
                [
                    Validators.required,
                    Validators.minLength(ProfileValidation.visibleName.minLength),
                    Validators.maxLength(ProfileValidation.visibleName.maxLength),
                ],
            ],
            bio: [
                '',
                [Validators.maxLength(ProfileValidation.bio.maxLength)],
            ],
            city: [
                '',
                [
                    Validators.required,
                    Validators.maxLength(ProfileValidation.city.maxLength),
                ],
            ],
            department: [
                '',
                [
                    Validators.required,
                    Validators.maxLength(ProfileValidation.department.maxLength),
                ],
            ],
        })
    }

    private async loadProfile(): Promise<void> {
        await this.session.loadProfile()
    }

    async onSubmit(): Promise<void> {
        if (this.profileForm.invalid) {
            this.markFormGroupTouched(this.profileForm)
            return
        }

        const currentProfile = this.profile()
        if (!currentProfile) {
            this.session.profileError.set('No se encontró el perfil')
            return
        }

        const updatedProfile = {
            ...currentProfile,
            ...this.profileForm.value,
        }

        await this.session.saveProfile(updatedProfile)
    }

    onRetry(): void {
        this.session.clearProfileMessages()
        void this.loadProfile()
    }

    clearError(): void {
        this.session.clearProfileMessages()
    }

    private markFormGroupTouched(formGroup: FormGroup): void {
        Object.values(formGroup.controls).forEach((control) => {
            control.markAsTouched()
            if (control instanceof FormGroup) {
                this.markFormGroupTouched(control)
            }
        })
    }

    getErrorMessage(controlName: string): string {
        const control = this.profileForm.get(controlName)
        if (!control) return ''

        if (control.hasError('required')) return 'Campo obligatorio'
        if (control.hasError('minlength')) {
            return `Mínimo ${control.errors?.['minlength'].requiredLength} caracteres`
        }
        if (control.hasError('maxlength')) {
            return `Máximo ${control.errors?.['maxlength'].requiredLength} caracteres`
        }

        return 'Campo inválido'
    }
}

