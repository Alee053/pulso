export type OpportunitySkillImportance = 'REQUIRED' | 'PREFERRED'

export interface ManagedProjectOpportunity {
  id: string
  title: string
  status: string
}

export interface OpportunitySkill {
  skillId: string
  slug: string
  name: string
  importance: OpportunitySkillImportance
  active: boolean
}

export interface OpportunityPage {
  data: ManagedProjectOpportunity[]
  meta: { offset: number; limit: number; hasMore: boolean }
}
