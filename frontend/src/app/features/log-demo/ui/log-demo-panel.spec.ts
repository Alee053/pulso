import { provideHttpClient } from '@angular/common/http'
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import { LogDemoPanelComponent } from './log-demo-panel'

describe('LogDemoPanelComponent', () => {
  it('shows the logged error result after the backend returns the expected 503', () => {
    TestBed.configureTestingModule({
      imports: [LogDemoPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    })

    const fixture = TestBed.createComponent(LogDemoPanelComponent)
    const http = TestBed.inject(HttpTestingController)
    fixture.detectChanges()

    fixture.nativeElement.querySelector('[data-action="toggle"]').click()
    fixture.detectChanges()
    expect(fixture.nativeElement.textContent).toContain('Provocar un error controlado')

    fixture.nativeElement.querySelector('[data-action="trigger"]').click()
    const request = http.expectOne(
      'http://localhost:8080/api/dev/log-demo/data-store-failure',
    )
    expect(request.request.method).toBe('POST')
    request.flush(
      {
        title: 'Data store unavailable',
        detail: 'The data store is temporarily unavailable.',
        status: 503,
      },
      { status: 503, statusText: 'Service Unavailable' },
    )
    fixture.detectChanges()

    expect(fixture.nativeElement.textContent).toContain('Evento de error generado')
    expect(fixture.nativeElement.textContent).toContain('ERROR')
    expect(fixture.nativeElement.textContent).toContain('dataStoreUnavailable')
    expect(fixture.nativeElement.textContent).toContain('HTTP 503')
    http.verify()
  })
})
