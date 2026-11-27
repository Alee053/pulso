import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it, vi } from 'vitest'
import { type ConnectionInboxEntry } from '../domain/connection'
import { MessagesViewComponent } from './messages-view'

const requests = 'http://localhost:8080/api/v1/connection-intents'
const sent = requests + '/sent?offset=0&limit=21'
const received = requests + '/received?offset=0&limit=21'

function entry(id: string, name: string, conversationId: string | null): ConnectionInboxEntry {
  return {
    projectName: name, opportunityTitle: null, conversationId,
    request: {
      id, senderProfileId: 'sender', projectId: 'project', opportunityId: null,
      status: 'ACCEPTED', reason: 'COLLABORATE', message: null, createdAt: '2026-11-21T12:00:00Z',
    },
  }
}

function setup() {
  TestBed.configureTestingModule({
    imports: [MessagesViewComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  })
  const fixture = TestBed.createComponent(MessagesViewComponent)
  fixture.componentRef.setInput('profileId', 'alice')
  fixture.detectChanges()
  return { fixture, root: fixture.nativeElement as HTMLElement, http: TestBed.inject(HttpTestingController) }
}

describe('MessagesViewComponent', () => {
  it('uses separate request cursors and finds an authorized conversation after a full unlinked page', () => {
    const { fixture, root, http } = setup()
    const secondPage = entry('r20', 'Proyecto visible', 'shared')
    const hidden = Array.from({ length: 20 }, (_, index) => entry('r' + index, 'Contexto privado', null))
    http.expectOne(received).flush([...hidden, secondPage])
    http.expectOne(sent).flush([entry('s1', 'Otro proyecto', 'sent-chat')])
    fixture.detectChanges()

    expect(root.textContent).not.toContain('Contexto privado')
    expect(root.querySelector('a[href="/conversaciones/sent-chat"]')).not.toBeNull()
    root.querySelector<HTMLButtonElement>('.inbox-feedback button')!.click()
    http.expectOne(requests + '/received?offset=20&limit=21').flush([secondPage])
    fixture.detectChanges()
    expect(root.querySelectorAll('.thread-link')).toHaveLength(2)
    const link = root.querySelector<HTMLAnchorElement>('a[href="/conversaciones/shared"]')!
    const select = vi.fn()
    fixture.componentInstance.conversationSelected.subscribe(select)
    link.click()
    expect(select).toHaveBeenCalledWith('shared')

    const modifiedClick = new MouseEvent('click', { ctrlKey: true, cancelable: true })
    link.addEventListener('click', event => {
      expect(event.defaultPrevented).toBe(false)
      event.preventDefault()
    }, { once: true })
    link.dispatchEvent(modifiedClick)
    expect(select).toHaveBeenCalledTimes(1)
    http.verify()
  })

  it('keeps successful pages on partial failure and merges duplicate conversation IDs after retry', () => {
    const { fixture, root, http } = setup()
    const chat = entry('r1', 'Proyecto visible', 'thread-1')
    http.expectOne(received).flush([chat])
    http.expectOne(sent).flush(null, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    expect(root.querySelector('[role=alert]')).not.toBeNull()
    expect(root.querySelectorAll('.thread-link')).toHaveLength(1)
    root.querySelector<HTMLButtonElement>('.inbox-feedback button')!.click()
    http.expectOne(sent).flush([chat])
    fixture.detectChanges()
    expect(root.querySelectorAll('.thread-link')).toHaveLength(1)
    expect(root.querySelector('[role=alert]')).toBeNull()
    http.verify()
  })

  it('continues a healthy inbox past an unlinked page while the other inbox awaits retry', () => {
    const { fixture, root, http } = setup()
    const chat = entry('r20', 'Proyecto siguiente', 'received-chat')
    const unlinked = Array.from({ length: 20 }, (_, index) => entry('r' + index, 'Sin conversación', null))
    http.expectOne(received).flush([...unlinked, chat])
    http.expectOne(sent).flush(null, { status: 503, statusText: 'Unavailable' })
    fixture.detectChanges()
    const button = (label: string) => [...root.querySelectorAll('button')].find(item => item.textContent?.trim() === label)
    expect(button('Reintentar')).toBeDefined()
    expect(button('Buscar más conversaciones')).toBeDefined()
    button('Buscar más conversaciones')!.click()
    http.expectOne(requests + '/received?offset=20&limit=21').flush([chat])
    http.expectNone(sent)
    fixture.detectChanges()
    expect(root.querySelector('a[href="/conversaciones/received-chat"]')).not.toBeNull()
    expect(root.querySelector('[role=alert]')).not.toBeNull()
    expect(button('Buscar más conversaciones')).toBeUndefined()
    button('Reintentar')!.click()
    http.expectOne(sent).flush([entry('s1', 'Proyecto enviado', 'sent-chat')])
    fixture.detectChanges()
    expect(root.querySelectorAll('.thread-link')).toHaveLength(2)
    expect(root.querySelector('[role=alert]')).toBeNull()
    http.verify()
  })

  it('clears prior account data, cancels its pending pages and opens direct threads independently of discovery', () => {
    const { fixture, root, http } = setup()
    http.expectOne(received).flush([entry('r1', 'Perfil anterior', 'thread-1')])
    const previousPage = http.expectOne(sent)
    fixture.componentRef.setInput('profileId', 'bob')
    fixture.componentRef.setInput('conversationId', 'direct')
    fixture.detectChanges()
    expect(previousPage.cancelled).toBe(true)
    http.expectOne('http://localhost:8080/api/v1/conversations/direct/messages?offset=0')
      .flush({ data: [], meta: { offset: 0, limit: 20, hasMore: false } })
    http.expectOne(received).flush([])
    http.expectOne(sent).flush([])
    fixture.detectChanges()
    expect(root.textContent).not.toContain('Perfil anterior')
    expect(root.querySelector('#message-body')).not.toBeNull()
    const select = vi.fn()
    fixture.componentInstance.conversationSelected.subscribe(select)
    root.querySelector<HTMLButtonElement>('button[aria-label="Volver a Mensajes"]')!.click()
    expect(select).toHaveBeenCalledWith(null)
    http.verify()
  })
})
