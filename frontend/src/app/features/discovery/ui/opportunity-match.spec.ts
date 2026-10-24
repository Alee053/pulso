import { TestBed } from '@angular/core/testing'
import { describe, expect, it } from 'vitest'
import type { OpportunityMatch } from '../domain/opportunity-match'
import { matchStrings } from '../strings/match.strings'
import { projectStageStrings } from '../strings/project.strings'
import { OpportunityMatchComponent } from './opportunity-match'

const match: OpportunityMatch = {
  opportunityId: 'opportunity-1',
  title: 'Desarrollador',
  opportunityType: 'TALENT',
  score: 58,
  onboardingCompleted: true,
  missingFactors: ['STAGE'],
  factors: [
    {
      key: 'SKILLS', status: 'PARTIAL', weight: 35, points: 23,
      matched: ['Java'], unmatched: ['Figma'], reason: null,
    },
    {
      key: 'INTENTION', status: 'MATCHED', weight: 20, points: 20,
      matched: ['WORK'], unmatched: [], reason: null,
    },
    {
      key: 'INTERESTS', status: 'NOT_APPLICABLE', weight: 0, points: 0,
      matched: [], unmatched: [], reason: null,
    },
    {
      key: 'STAGE', status: 'MISSING_PROFILE_DATA', weight: 10, points: 0,
      matched: [], unmatched: ['MVP'], reason: null,
    },
    {
      key: 'LOCATION', status: 'MATCHED', weight: 10, points: 10,
      matched: [], unmatched: [], reason: 'REMOTE_ALLOWED',
    },
  ],
}

function create(value: OpportunityMatch = match) {
  const fixture = TestBed.createComponent(OpportunityMatchComponent)
  fixture.componentRef.setInput('match', value)
  fixture.detectChanges()
  return fixture
}

describe('OpportunityMatchComponent', () => {
  it('shows the score as an accessible meter without a bare percentage', () => {
    const fixture = create()
    const element = fixture.nativeElement as HTMLElement
    const meter = element.querySelector('[role="meter"]') as HTMLElement

    expect(element.textContent).toContain(matchStrings.scoreLabel(58))
    expect(meter.getAttribute('aria-valuenow')).toBe('58')
    expect(meter.getAttribute('aria-label')).toBe(matchStrings.scoreAria('Desarrollador', 58))
    expect(element.querySelector('.opportunity-match__factors')).toBeNull()
    fixture.destroy()
  })

  it('reveals every applicable factor with what matched, what did not and what is missing', () => {
    const fixture = create()
    const element = fixture.nativeElement as HTMLElement
    const toggle = element.querySelector('.opportunity-match__toggle') as HTMLButtonElement
    expect(toggle.getAttribute('aria-expanded')).toBe('false')

    toggle.click()
    fixture.detectChanges()

    expect(toggle.getAttribute('aria-expanded')).toBe('true')
    const factors = Array.from(element.querySelectorAll('.opportunity-match__factor'))
    expect(factors).toHaveLength(4)
    expect(element.textContent).not.toContain(matchStrings.factors.INTERESTS)
    const text = factors.map((factor) => factor.textContent ?? '').join('\n')
    expect(text).toContain(matchStrings.factors.SKILLS)
    expect(text).toContain(matchStrings.statuses.PARTIAL)
    expect(text).toContain(matchStrings.points(23, 35))
    expect(text).toContain('Java')
    expect(text).toContain('Figma')
    expect(text).toContain('Trabajar')
    expect(text).toContain(matchStrings.statuses.MISSING_PROFILE_DATA)
    expect(text).toContain(matchStrings.expectedLabel)
    expect(text).toContain(projectStageStrings.MVP)
    expect(text).toContain(matchStrings.reasons['REMOTE_ALLOWED'])

    toggle.click()
    fixture.detectChanges()
    expect(element.querySelector('.opportunity-match__factors')).toBeNull()
    fixture.destroy()
  })

  it('tells a person without onboarding which factors cannot be compared yet', () => {
    const fixture = create({
      ...match,
      score: 0,
      onboardingCompleted: false,
      missingFactors: ['SKILLS', 'INTENTION', 'STAGE'],
    })
    const text = (fixture.nativeElement as HTMLElement).textContent ?? ''

    expect(text).toContain(matchStrings.incompleteProfile)
    expect(text).toContain(`${matchStrings.missingIntro} Habilidades, Intención, Etapa.`)
    fixture.destroy()
  })

  it('does not warn when the profile already covers every comparable factor', () => {
    const fixture = create({ ...match, missingFactors: [], onboardingCompleted: true })

    expect((fixture.nativeElement as HTMLElement).textContent)
      .not.toContain(matchStrings.incompleteProfile)
    fixture.destroy()
  })
})
