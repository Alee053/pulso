import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockStartups } from '../data-access/mock-startups'
import { detailStrings } from '../strings/detail.strings'
import { StartupDetailModalComponent } from './startup-detail-modal'

const startup = mockStartups[0]!

describe('StartupDetailModalComponent accessibility', () => {
  afterEach(() => vi.useRealTimers())

  it('focuses the close control, contains Tab navigation, and restores the opener', () => {
    vi.useFakeTimers()
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const previousOverflow = document.body.style.overflow
    const fixture = TestBed.createComponent(StartupDetailModalComponent)
    fixture.componentRef.setInput('startup', startup)
    fixture.detectChanges()
    vi.runOnlyPendingTimers()

    const close = fixture.nativeElement.querySelector(`[aria-label="${detailStrings.closeAria}"]`) as HTMLButtonElement
    const actions = fixture.nativeElement.querySelectorAll('button, a[href]') as NodeListOf<HTMLElement>
    const last = actions[actions.length - 1]!
    expect(document.activeElement).toBe(close)
    expect(document.body.style.overflow).toBe('hidden')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true }))
    expect(document.activeElement).toBe(last)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', cancelable: true }))
    expect(document.activeElement).toBe(close)

    fixture.destroy()
    expect(document.activeElement).toBe(opener)
    expect(document.body.style.overflow).toBe(previousOverflow)
    opener.remove()
  })

  it('dismisses on Escape or backdrop click while preserving content clicks', () => {
    const fixture = TestBed.createComponent(StartupDetailModalComponent)
    fixture.componentRef.setInput('startup', startup)
    const closed = vi.fn()
    fixture.componentInstance.closed.subscribe(closed)
    fixture.detectChanges()

    ;(fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement).click()
    expect(closed).not.toHaveBeenCalled()
    ;(fixture.nativeElement.querySelector('.modal-backdrop') as HTMLElement).click()
    expect(closed).toHaveBeenCalledOnce()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))
    expect(closed).toHaveBeenCalledTimes(2)
  })
})
