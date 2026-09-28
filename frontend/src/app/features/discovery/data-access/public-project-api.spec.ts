import { TestBed } from '@angular/core/testing'
import { HttpClientTestingModule } from '@angular/common/http/testing'
import { describe, it, expect, beforeEach } from 'vitest'
import { PublicProjectApi } from './public-project-api'

describe('PublicProjectApi', () => {
    let api: PublicProjectApi

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [PublicProjectApi],
        })
        api = TestBed.inject(PublicProjectApi)
    })

    it('debe crearse', () => {
        expect(api).toBeTruthy()
    })

    it('debe devolver el mock del proyecto eco-pack', async () => {
        const project = await new Promise<any>((resolve, reject) => {
            api.getPublicDetail('eco-pack').subscribe({ next: resolve, error: reject })
        })

        expect(project.name).toBe('EcoPack Bolivia')
        expect(project.slug).toBe('eco-pack')
        expect(project.sector.name).toBe('Sostenibilidad')
        expect(project.tags.length).toBe(1)
        expect(project.externalLinks.length).toBe(1)
        expect(project.members.length).toBe(1)
        expect(project.members[0].isFeatured).toBe(true)
    })

    it('debe fallar con proyecto inexistente', async () => {
        let errorCaught: any = null

        await new Promise<void>((resolve) => {
            api.getPublicDetail('no-existe').subscribe({
                next: () => resolve(),
                error: (err) => {
                    errorCaught = err
                    resolve()
                },
            })
        })

        expect(errorCaught).toBeTruthy()
        expect(errorCaught.status).toBe(404)
    })
})