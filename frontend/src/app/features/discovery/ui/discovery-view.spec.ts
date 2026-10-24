import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockStartups } from '../data-access/mock-startups'
import { feedStrings, searchStrings } from '../strings/feed.strings'
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

  it('replaces the discovery feed with labelled, selectable search results', () => {
    const fixture = TestBed.createComponent(DiscoveryViewComponent)
    fixture.componentRef.setInput('currentStartup', startup)
    fixture.componentRef.setInput('searchQuery', 'eco')
    fixture.componentRef.setInput('searchResults', [startup])
    fixture.componentRef.setInput('searchTotal', 3)
    fixture.componentRef.setInput('searchHasMore', true)
    const selected = vi.fn()
    const more = vi.fn()
    fixture.componentInstance.startupSelected.subscribe(selected)
    fixture.componentInstance.searchMoreRequested.subscribe(more)
    fixture.detectChanges()

    const element = fixture.nativeElement as HTMLElement
    expect(element.querySelector('label[for="project-search"]')?.textContent)
      .toContain(searchStrings.label)
    expect(element.querySelector('.discovery-action--save')).toBeNull()
    expect(element.textContent).toContain(searchStrings.resultsCount(1, 3))

    const result = element.querySelector('.search-result') as HTMLButtonElement
    expect(result.getAttribute('aria-label')).toBe(searchStrings.openAria(startup.name))
    result.click()
    expect(selected).toHaveBeenCalledWith(startup)

    const loadMore = Array.from(element.querySelectorAll('button'))
      .find((button) => button.textContent?.includes(searchStrings.loadMore)) as HTMLButtonElement
    loadMore.click()
    expect(more).toHaveBeenCalledOnce()
    fixture.destroy()
  })

  it('shows loading, error with retry and empty states for a search', () => {
    const fixture = TestBed.createComponent(DiscoveryViewComponent)
    const retry = vi.fn()
    fixture.componentInstance.searchRetried.subscribe(retry)
    fixture.componentRef.setInput('searchQuery', 'zzz')
    fixture.componentRef.setInput('isSearching', true)
    fixture.detectChanges()
    const element = fixture.nativeElement as HTMLElement
    expect(element.textContent).toContain(searchStrings.loading)

    fixture.componentRef.setInput('isSearching', false)
    fixture.detectChanges()
    expect(element.textContent).toContain(searchStrings.emptyTitle('zzz'))

    fixture.componentRef.setInput('searchError', searchStrings.failed)
    fixture.detectChanges()
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(searchStrings.failed)
    ;(element.querySelector('[role="alert"] button') as HTMLButtonElement).click()
    expect(retry).toHaveBeenCalledOnce()
    fixture.destroy()
  })

  it('emits the typed query and ignores discovery shortcuts while typing', () => {
    const fixture = TestBed.createComponent(DiscoveryViewComponent)
    fixture.componentRef.setInput('currentStartup', startup)
    const changed = vi.fn()
    const decision = vi.fn()
    fixture.componentInstance.searchChanged.subscribe(changed)
    fixture.componentInstance.decisionMade.subscribe(decision)
    fixture.detectChanges()

    const input = fixture.nativeElement.querySelector('#project-search') as HTMLInputElement
    input.value = 'eco'
    input.dispatchEvent(new Event('input'))
    expect(changed).toHaveBeenCalledWith('eco')

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }))
    expect(decision).not.toHaveBeenCalled()
    fixture.destroy()
  })
})
