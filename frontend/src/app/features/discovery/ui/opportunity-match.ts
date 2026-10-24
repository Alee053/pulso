import { CommonModule } from '@angular/common'
import { Component, Input, signal } from '@angular/core'
import type { MatchFactor, OpportunityMatch } from '../domain/opportunity-match'
import { intentionStrings } from '../strings/onboarding.strings'
import { matchStrings } from '../strings/match.strings'
import { projectStageStrings } from '../strings/project.strings'

@Component({
  selector: 'pulso-opportunity-match',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './opportunity-match.html',
})
export class OpportunityMatchComponent {
  @Input({ required: true }) match!: OpportunityMatch
  @Input() idPrefix = 'match'

  readonly strings = matchStrings
  readonly expanded = signal(false)

  get visibleFactors(): MatchFactor[] {
    return this.match.factors.filter((factor) => factor.status !== 'NOT_APPLICABLE')
  }

  get missingLabels(): string[] {
    return this.match.missingFactors.map((key) => this.strings.factors[key])
  }

  toggle(): void {
    this.expanded.update((value) => !value)
  }

  itemLabel(factor: MatchFactor, value: string): string {
    if (factor.key === 'INTENTION') {
      return (intentionStrings as Record<string, string>)[value] ?? value
    }
    if (factor.key === 'STAGE') {
      return (projectStageStrings as Record<string, string>)[value] ?? value
    }
    return value
  }

  reasonLabel(factor: MatchFactor): string | null {
    return factor.reason ? (this.strings.reasons[factor.reason] ?? null) : null
  }
}
