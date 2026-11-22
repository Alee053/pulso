export type OpportunitySkillImportance = 'REQUIRED' | 'PREFERRED'
export type OpportunityType =
  | 'TALENT' | 'COLLABORATION' | 'CUSTOMER' | 'BETA_TESTER' | 'INVESTMENT'
  | 'MENTOR' | 'SUPPLIER' | 'PARTNERSHIP' | 'OTHER'

export type OpportunityStatus = 'DRAFT' | 'OPEN' | 'PAUSED' | 'FILLED' | 'CLOSED' | 'ARCHIVED'

export interface OpportunityContentValue {
  opportunityType: OpportunityType
  title: string
  description: string
  city: string
  department: string
  remoteAllowed: boolean
}

export interface ManagedProjectOpportunity {
  id: string
  projectId: string
  opportunityType: OpportunityType
  title: string
  description: string
  status: OpportunityStatus
  city: string | null
  department: string | null
  remoteAllowed: boolean
  createdAt: string
  updatedAt: string
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
