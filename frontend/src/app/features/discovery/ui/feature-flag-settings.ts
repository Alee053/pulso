import { CommonModule } from '@angular/common'
import { Component, inject, OnInit, signal } from '@angular/core'
import { FeatureFlagsService } from '../../../core/feature-flags/feature-flags.service'
import type { FeatureFlagKey } from '../../../core/feature-flags/feature-flags-api'
import { commonStrings } from '../../../shared/strings/common.strings'
import { featureFlagSettingsStrings } from '../strings/feature-flag-settings.strings'

@Component({
  selector: 'pulso-feature-flag-settings',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './feature-flag-settings.html',
})
export class FeatureFlagSettingsComponent implements OnInit {
  readonly strings = featureFlagSettingsStrings
  readonly common = commonStrings
  readonly flags = inject(FeatureFlagsService)
  readonly updateError = signal(false)
  readonly isSaved = signal(false)
  readonly savingKey = signal<FeatureFlagKey | null>(null)
  readonly pendingSwitch = signal<{ key: FeatureFlagKey; enabled: boolean } | null>(null)
  readonly options = [
    {
      key: 'language_switcher',
      label: () => this.strings.languageSwitcher,
      description: () => this.strings.languageSwitcherDescription,
    },
    {
      key: 'project_creation',
      label: () => this.strings.projectCreation,
      description: () => this.strings.projectCreationDescription,
    },
  ] as const

  ngOnInit(): void {
    if (!this.flags.isLoaded() && !this.flags.isLoading()) {
      void this.flags.load()
    }
  }

  async retryLoad(): Promise<void> {
    await this.flags.load()
  }

  async updateFlag(key: FeatureFlagKey, event: Event): Promise<void> {
    const enabled = (event.target as HTMLInputElement).checked
    this.updateError.set(false)
    this.isSaved.set(false)
    this.pendingSwitch.set({ key, enabled })
    this.savingKey.set(key)
    try {
      await this.flags.update(key, enabled)
      this.isSaved.set(true)
    } catch {
      this.updateError.set(true)
    } finally {
      this.pendingSwitch.set(null)
      this.savingKey.set(null)
    }
  }

  isChecked(key: FeatureFlagKey): boolean {
    const pending = this.pendingSwitch()
    return pending?.key === key ? pending.enabled : this.flags.isEnabled(key)
  }
}
