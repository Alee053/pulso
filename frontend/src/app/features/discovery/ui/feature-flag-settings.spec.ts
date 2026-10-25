import { signal } from '@angular/core'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { FeatureFlagsService } from '../../../core/feature-flags/feature-flags.service'
import type { FeatureFlagKey, FeatureFlagValues } from '../../../core/feature-flags/feature-flags-api'
import { FeatureFlagSettingsComponent } from './feature-flag-settings'

describe('FeatureFlagSettingsComponent', () => {
  it('loads and displays the persisted flag values as accessible switches', async () => {
    const service = createFeatureFlagsStub({ language_switcher: true, project_creation: false }, false)
    let resolveLoad!: () => void
    service.load.mockImplementation(() => new Promise<void>((resolve) => {
      resolveLoad = () => {
        service.values.set({ language_switcher: true, project_creation: false })
        service.isLoaded.set(true)
        resolve()
      }
    }))
    const fixture = createFixture(service)

    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('Cargando')
    resolveLoad()
    await fixture.whenStable()
    fixture.detectChanges()

    const switches = Array.from(fixture.nativeElement.querySelectorAll('[role="switch"]')) as HTMLInputElement[]
    expect(service.load).toHaveBeenCalledOnce()
    expect(switches).toHaveLength(2)
    expect(switches.map(input => input.checked)).toEqual([true, false])
    expect(switches.map(input => input.getAttribute('aria-label'))).toEqual([
      'Selector de idioma', 'Creación de proyectos',
    ])
  })

  it('saves changes through the shared feature flag service and confirms success', async () => {
    const service = createFeatureFlagsStub({ language_switcher: false, project_creation: true })
    const fixture = createFixture(service)
    fixture.detectChanges()

    const languageSwitch = fixture.nativeElement.querySelector('[aria-label="Selector de idioma"]') as HTMLInputElement
    languageSwitch.checked = true
    languageSwitch.dispatchEvent(new Event('change'))
    await fixture.whenStable()
    fixture.detectChanges()

    expect(service.update).toHaveBeenCalledWith('language_switcher', true)
    expect(service.values().language_switcher).toBe(true)
    expect(fixture.nativeElement.querySelector('[role="status"]')?.textContent).toContain('actualizada')
  })

  it('offers a retry after a load failure and then displays the recovered values', async () => {
    const service = createFeatureFlagsStub({ language_switcher: false, project_creation: false }, false)
    service.load
      .mockImplementationOnce(async () => {
        service.isLoaded.set(true)
        service.hasLoadError.set(true)
      })
      .mockImplementationOnce(async () => {
        service.values.set({ language_switcher: true, project_creation: true })
        service.hasLoadError.set(false)
        service.isLoaded.set(true)
      })
    const fixture = createFixture(service)
    await fixture.whenStable()
    fixture.detectChanges()

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('No se pudo cargar')
    await fixture.componentInstance.retryLoad()
    fixture.detectChanges()

    expect(service.load).toHaveBeenCalledTimes(2)
    expect(Array.from(fixture.nativeElement.querySelectorAll('[role="switch"]') as NodeListOf<HTMLInputElement>).map(input => input.checked))
      .toEqual([true, true])
  })

  it('restores the previous switch state and reports a failed update', async () => {
    const service = createFeatureFlagsStub({ language_switcher: false, project_creation: true })
    let rejectUpdate!: (reason: Error) => void
    service.update.mockImplementation(async (key, enabled) => {
      await new Promise<void>((_resolve, reject) => { rejectUpdate = reject })
    })
    const fixture = createFixture(service)
    fixture.detectChanges()

    const update = fixture.componentInstance.updateFlag('language_switcher', {
      target: { checked: true },
    } as unknown as Event)
    fixture.detectChanges()
    expect(service.values().language_switcher).toBe(false)
    expect((fixture.nativeElement.querySelector('[aria-label="Selector de idioma"]') as HTMLInputElement).checked).toBe(true)
    rejectUpdate(new Error('request failed'))
    await update
    fixture.detectChanges()

    expect(service.update).toHaveBeenCalledWith('language_switcher', true)
    expect(service.values().language_switcher).toBe(false)
    expect(fixture.componentInstance.updateError()).toBe(true)
    expect((fixture.nativeElement.querySelector('[aria-label="Selector de idioma"]') as HTMLInputElement).checked).toBe(false)
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('No se pudo actualizar')
  })
})

function createFixture(service: ReturnType<typeof createFeatureFlagsStub>) {
  TestBed.configureTestingModule({
    imports: [FeatureFlagSettingsComponent],
    providers: [{ provide: FeatureFlagsService, useValue: service }],
  })
  const fixture = TestBed.createComponent(FeatureFlagSettingsComponent)
  fixture.detectChanges()
  return fixture
}

function createFeatureFlagsStub(initial: FeatureFlagValues, loaded = true) {
  const values = signal<FeatureFlagValues>(initial)
  const isLoaded = signal(loaded)
  const load = vi.fn(async () => { isLoaded.set(true) })
  const update = vi.fn(async (key: FeatureFlagKey, enabled: boolean) => {
    values.update(current => ({ ...current, [key]: enabled }))
  })
  return {
    values,
    isLoaded,
    isLoading: signal(false),
    hasError: signal(false),
    hasLoadError: signal(false),
    isEnabled: (key: FeatureFlagKey) => values()[key],
    load,
    update,
  }
}
