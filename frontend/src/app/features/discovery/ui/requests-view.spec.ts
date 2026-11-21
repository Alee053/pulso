import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { afterEach, describe, expect, it } from 'vitest'
import { currentLanguage } from '../../../core/i18n/language'
import type { ConnectionIntent } from '../domain/startup'
import { RequestsViewComponent } from './requests-view'

const inbox = 'http://localhost:8080/api/v1/connection-intents/pending'

function intent(id: string, senderProfileId: string): ConnectionIntent {
  return {
    id, senderProfileId, projectId: 'project', opportunityId: null, reason: 'OTHER',
    message: `Request ${id}`, status: 'PENDING', createdAt: '2026-11-21T12:00:00Z',
  }
}

function setup() {
  TestBed.configureTestingModule({
    imports: [RequestsViewComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(RequestsViewComponent)
  fixture.detectChanges()
  return { fixture, root: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) }
}

describe('RequestsViewComponent', () => {
  afterEach(() => currentLanguage.set('es'))

  it('blocks the API sender and reloads the inbox without declining the requests', () => {
    const { fixture, root, http } = setup()
    const anotherSender = intent('c', 'other')
    http.expectOne(`${inbox}?offset=0&limit=21`).flush([
      intent('a', 'sender'), intent('b', 'sender'), anotherSender,
    ])
    fixture.detectChanges()

    const action = root.querySelector('pulso-profile-block-action')!
    action.querySelector('details')!.open = true
    action.querySelectorAll('button')[1].click()
    http.expectOne({ method: 'POST', url: 'http://localhost:8080/api/v1/profiles/sender/block' })
      .flush(null, { status: 204, statusText: 'No Content' })
    http.expectNone((request) => request.url.endsWith('/decline'))
    http.expectOne(`${inbox}?offset=0&limit=21`).flush([anotherSender])
    fixture.detectChanges()

    expect(root.querySelectorAll('pulso-profile-block-action')).toHaveLength(1)
    expect(root.textContent).toContain('Request c')
    expect(root.textContent).not.toContain('Request a')
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Perfil bloqueado')
    http.verify()
  })

  it('shows a retry after an inbox error and paginates while keeping the response localized', () => {
    currentLanguage.set('en')
    const { fixture, root, http } = setup()
    const firstPage = `${inbox}?offset=0&limit=21`
    http.expectOne(firstPage).flush(null, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Could not load requests')
    root.querySelector('button')!.click()
    http.expectOne(firstPage).flush(Array.from({ length: 21 }, (_, index) => intent(String(index), 'sender')))
    fixture.detectChanges()
    expect(root.querySelectorAll('li')).toHaveLength(20)
    const more = Array.from(root.querySelectorAll('button')).find((button) => button.textContent?.includes('Show more requests'))
    more!.click()
    http.expectOne(`${inbox}?offset=20&limit=21`).flush([intent('20', 'sender')])
    fixture.detectChanges()
    expect(root.querySelectorAll('li')).toHaveLength(21)
    http.verify()
  })
})
