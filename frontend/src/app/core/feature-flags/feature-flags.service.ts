import { inject, Injectable, signal } from '@angular/core'
import { firstValueFrom } from 'rxjs'
import {
  FeatureFlagsApi,
  featureFlagKeys,
  type FeatureFlagKey,
  type FeatureFlagListDto,
  type FeatureFlagValues,
} from './feature-flags-api'

const disabledFeatureFlags: FeatureFlagValues = {
  language_switcher: false,
  project_creation: false,
}

@Injectable({ providedIn: 'root' })
export class FeatureFlagsService {
  private readonly api = inject(FeatureFlagsApi)
  private readonly valuesState = signal<FeatureFlagValues>({ ...disabledFeatureFlags })
  private readonly loadedState = signal(false)
  private readonly loadingState = signal(false)
  private readonly errorState = signal(false)

  readonly values = this.valuesState.asReadonly()
  readonly isLoaded = this.loadedState.asReadonly()
  readonly isLoading = this.loadingState.asReadonly()
  readonly hasError = this.errorState.asReadonly()

  isEnabled(key: FeatureFlagKey): boolean {
    return this.valuesState()[key]
  }

  async load(): Promise<void> {
    this.loadingState.set(true)
    this.errorState.set(false)
    try {
      const response = await firstValueFrom(this.api.load())
      this.valuesState.set(parseFeatureFlags(response))
    } catch {
      this.valuesState.set({ ...disabledFeatureFlags })
      this.errorState.set(true)
    } finally {
      this.loadedState.set(true)
      this.loadingState.set(false)
    }
  }

  async update(key: FeatureFlagKey, enabled: boolean): Promise<void> {
    const previousValue = this.valuesState()[key]
    this.valuesState.update(current => ({ ...current, [key]: enabled }))
    this.errorState.set(false)
    try {
      const response = await firstValueFrom(this.api.update(key, enabled))
      if (!response || response.key !== key || typeof response.enabled !== 'boolean') {
        throw new Error('Invalid feature flag update response')
      }
      this.valuesState.update(current => ({ ...current, [key]: response.enabled }))
    } catch (error) {
      this.valuesState.update(current => ({ ...current, [key]: previousValue }))
      this.errorState.set(true)
      throw error
    }
  }
}

function parseFeatureFlags(response: FeatureFlagListDto): FeatureFlagValues {
  if (!response || !Array.isArray(response.flags)) {
    throw new Error('Invalid feature flags response')
  }

  const values: FeatureFlagValues = { ...disabledFeatureFlags }
  const found = new Set<FeatureFlagKey>()
  for (const flag of response.flags as Array<{ key?: unknown; enabled?: unknown }>) {
    if (typeof flag?.key !== 'string' || !isFeatureFlagKey(flag.key)) {
      continue
    }
    if (found.has(flag.key) || typeof flag.enabled !== 'boolean') {
      throw new Error('Invalid feature flags response')
    }
    found.add(flag.key)
    values[flag.key] = flag.enabled
  }
  if (found.size !== featureFlagKeys.length) {
    throw new Error('Feature flags response is incomplete')
  }
  return values
}

function isFeatureFlagKey(value: string): value is FeatureFlagKey {
  return (featureFlagKeys as readonly string[]).includes(value)
}
