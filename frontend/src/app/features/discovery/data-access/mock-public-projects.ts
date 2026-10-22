import type { PublicProjectDetail } from '../domain/startup'

export const MOCK_PUBLIC_PROJECTS: Record<string, PublicProjectDetail> = {
    'eco-pack': {
        id: '33333333-3333-3333-3333-333333333333',
        slug: 'eco-pack',
        name: 'EcoPack Bolivia',
        tagline: 'Envases biodegradables hechos con residuos agrícolas.',
        description: 'Envases compostables para productores locales.',
        valueProposition: 'Reduce residuos y mejora el valor de los desechos agrícolas.',
        sector: {
            id: '11111111-1111-1111-1111-111111111111',
            slug: 'sostenibilidad',
            name: 'Sostenibilidad',
        },
        stage: 'MVP',
        city: 'Cochabamba',
        department: 'Cochabamba',
        tags: [
            {
                id: '22222222-2222-2222-2222-222222222222',
                slug: 'economia-circular',
                name: 'Economía circular',
            },
        ],
        externalLinks: [
            {
                type: 'WEBSITE',
                url: 'https://ecopack.example',
                label: 'Sitio web',
            },
        ],
        members: [
            {
                profileId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                displayName: 'Persona Uno',
                avatarStoragePath: 'avatars/persona-uno.webp',
                displayTitle: 'Co-fundadora',
                isFeatured: true,
            },
        ],
        opportunities: [],
    },
    '1': {
        id: '33333333-3333-3333-3333-333333333333',
        slug: 'eco-pack',
        name: 'EcoPack Bolivia',
        tagline: 'Envases biodegradables hechos con residuos agrícolas.',
        description: 'Envases compostables para productores locales.',
        valueProposition: 'Reduce residuos y mejora el valor de los desechos agrícolas.',
        sector: {
            id: '11111111-1111-1111-1111-111111111111',
            slug: 'sostenibilidad',
            name: 'Sostenibilidad',
        },
        stage: 'MVP',
        city: 'Cochabamba',
        department: 'Cochabamba',
        tags: [
            {
                id: '22222222-2222-2222-2222-222222222222',
                slug: 'economia-circular',
                name: 'Economía circular',
            },
        ],
        externalLinks: [
            {
                type: 'WEBSITE',
                url: 'https://ecopack.example',
                label: 'Sitio web',
            },
        ],
        members: [
            {
                profileId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                displayName: 'Persona Uno',
                avatarStoragePath: 'avatars/persona-uno.webp',
                displayTitle: 'Co-fundadora',
                isFeatured: true,
            },
        ],
        opportunities: [],
    },
}

export function getMockProject(reference: string): PublicProjectDetail | undefined {
    return MOCK_PUBLIC_PROJECTS[reference]
}