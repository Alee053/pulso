import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockStartups } from '../data-access/mock-startups'
import { feedStrings } from '../strings/feed.strings'
import { DiscoveryViewComponent } from './discovery-view'

const startup = mockStartups[0]!

describe('DiscoveryViewComponent interactions', () => {
  afterEach(() => vi.useRealTimers())

  it('offers save and decision controls without requiring a gesture', () => {
    vi.useFakeTimers()
    const fixture = TestBed.createComponent(DiscoveryViewComponent)
    fixture.componentRef.setInput('currentStartup', startup)
    const saved = vi.fn()
    const decision = vi.fn()
    fixture.componentInstance.startupSaved.subscribe(saved)
    fixture.componentInstance.decisionMade.subscribe(decision)
    fixture.detectChanges()

    const save = fixture.nativeElement.querySelector('.discovery-action--save') as HTMLButtonElement
    save.click()
    expect(saved).toHaveBeenCalledWith(startup.id)

    fixture.componentRef.setInput('savedIds', [startup.id])
    fixture.detectChanges()
    expect(save.getAttribute('aria-pressed')).toBe('true')
    expect(save.getAttribute('aria-label')).toBe(feedStrings.unsaveAria)

    const interested = fixture.nativeElement.querySelector('.discovery-action--interested') as HTMLButtonElement
    interested.click()
    fixture.detectChanges()
    expect(decision).toHaveBeenCalledExactlyOnceWith('interested')
    expect(interested.disabled).toBe(true)
    interested.click()
    expect(decision).toHaveBeenCalledOnce()
    fixture.destroy()
  })

  it('does not process discovery shortcuts while a project dialog is open', () => {
    const fixture = TestBed.createComponent(DiscoveryViewComponent)
    fixture.componentRef.setInput('currentStartup', startup)
    fixture.componentRef.setInput('isModalOpen', true)
    const decision = vi.fn()
    fixture.componentInstance.decisionMade.subscribe(decision)
    fixture.detectChanges()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }))

    expect(decision).not.toHaveBeenCalled()
  })

  it('keeps failed, empty, and completed feed states distinct', () => {
    const fixture = TestBed.createComponent(DiscoveryViewComponent)
    fixture.componentRef.setInput('errorMessage', 'Request failed')
    const retry = vi.fn()
    const restart = vi.fn()
    fixture.componentInstance.retryRequested.subscribe(retry)
    fixture.componentInstance.restarted.subscribe(restart)
    fixture.detectChanges()
    ;(fixture.nativeElement.querySelector('button') as HTMLButtonElement).click()
    expect(retry).toHaveBeenCalledOnce()

    fixture.componentRef.setInput('errorMessage', null)
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain(feedStrings.emptyTitle)
    expect(fixture.nativeElement.querySelector('button')).toBeNull()

    fixture.componentRef.setInput('totalStartups', 1)
    fixture.detectChanges()
    ;(fixture.nativeElement.querySelector('button') as HTMLButtonElement).click()
    expect(restart).toHaveBeenCalledOnce()
  })
})
