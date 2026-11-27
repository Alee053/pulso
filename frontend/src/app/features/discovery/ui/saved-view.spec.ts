import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { mockStartups } from '../data-access/mock-startups'
import { savedStrings } from '../strings/saved.strings'
import { SavedViewComponent } from './saved-view'

describe('SavedViewComponent', () => {
  it('removes a saved project independently of an interest decision', () => {
    const startup = mockStartups[0]!
    const fixture = TestBed.createComponent(SavedViewComponent)
    fixture.componentRef.setInput('savedStartups', [startup])
    fixture.componentRef.setInput('savedCount', 1)
    const removed = vi.fn()
    const interest = vi.fn()
    fixture.componentInstance.savedRemoved.subscribe(removed)
    fixture.componentInstance.interestCleared.subscribe(interest)
    fixture.detectChanges()
    fixture.nativeElement.querySelector('.collection-card__remove').click()
    expect(removed).toHaveBeenCalledExactlyOnceWith(startup.id)
    expect(interest).not.toHaveBeenCalled()
  })

  it('allows removing an interested project without exposing public popularity', () => {
    const startup = mockStartups[0]!
    const fixture = TestBed.createComponent(SavedViewComponent)
    fixture.componentRef.setInput('interestedStartups', [startup])
    fixture.componentRef.setInput('interestedCount', 1)
    fixture.componentRef.setInput('hasLoadedSavedProjects', true)
    fixture.componentRef.setInput('hasLoadedInterestedProjects', true)
    const cleared = vi.fn()
    fixture.componentInstance.interestCleared.subscribe(cleared)
    fixture.detectChanges()

    const card = fixture.nativeElement.querySelector('.collection-card--interested') as HTMLElement
    const button = Array.from<HTMLButtonElement>(
      card.querySelectorAll('button'),
    ).find((element) => element.classList.contains('collection-card__remove'))
    expect(card).not.toBeNull()
    expect(button).toBeDefined()
    expect(button!.getAttribute('aria-label')).toBe(`${savedStrings.clearInterest}: ${startup.name}`)
    expect(fixture.nativeElement.querySelector('button button')).toBeNull()
    button!.click()
    expect(cleared).toHaveBeenCalledExactlyOnceWith(startup.id)

    fixture.componentRef.setInput('isSavingInteraction', true)
    fixture.detectChanges()
    expect(button!.disabled).toBe(true)
    fixture.destroy()
  })
})
