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
  private readonly loadErrorState = signal(false)
  private readonly pendingUpdates = new Set<Promise<void>>()
  private loadGeneration = 0
  private mutationGeneration = 0

  readonly values = this.valuesState.asReadonly()
  readonly isLoaded = this.loadedState.asReadonly()
  readonly isLoading = this.loadingState.asReadonly()
  readonly hasError = this.errorState.asReadonly()
  readonly hasLoadError = this.loadErrorState.asReadonly()

  isEnabled(key: FeatureFlagKey): boolean {
    return this.valuesState()[key]
  }

  async load(): Promise<void> {
    const generation = ++this.loadGeneration
    this.loadingState.set(true)
    this.errorState.set(false)
    this.loadErrorState.set(false)
    try {
      while (generation === this.loadGeneration) {
        if (this.pendingUpdates.size > 0) {
          await Promise.all([...this.pendingUpdates])
        }
        if (generation !== this.loadGeneration) {
          return
        }

        const mutationGeneration = this.mutationGeneration
        let response: FeatureFlagListDto
        try {
          response = await firstValueFrom(this.api.load())
        } catch (error) {
          if (generation !== this.loadGeneration) {
            return
          }
          if (mutationGeneration !== this.mutationGeneration) {
            continue
          }
          throw error
        }

        if (generation !== this.loadGeneration) {
          return
        }
        if (mutationGeneration !== this.mutationGeneration || this.pendingUpdates.size > 0) {
          continue
        }
        this.valuesState.set(parseFeatureFlags(response))
        return
      }
    } catch {
      if (generation === this.loadGeneration) {
        this.valuesState.set({ ...disabledFeatureFlags })
        this.errorState.set(true)
        this.loadErrorState.set(true)
      }
    } finally {
      if (generation === this.loadGeneration) {
        this.loadedState.set(true)
        this.loadingState.set(false)
      }
    }
  }

  async update(key: FeatureFlagKey, enabled: boolean): Promise<void> {
    let completeUpdate!: () => void
    const pendingUpdate = new Promise<void>(resolve => { completeUpdate = resolve })
    this.pendingUpdates.add(pendingUpdate)
    this.mutationGeneration += 1
    this.errorState.set(false)
    try {
      const response = await firstValueFrom(this.api.update(key, enabled))
      if (!response || response.key !== key || typeof response.enabled !== 'boolean') {
        throw new Error('Invalid feature flag update response')
      }
      this.valuesState.update(current => ({ ...current, [key]: response.enabled }))
    } catch (error) {
      this.errorState.set(true)
      throw error
    } finally {
      this.mutationGeneration += 1
      this.pendingUpdates.delete(pendingUpdate)
      completeUpdate()
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
