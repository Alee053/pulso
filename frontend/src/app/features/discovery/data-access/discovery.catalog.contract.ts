export const discoveryCatalogContract = {
  defaultLimit: 20,
  defaultOffset: 0,
  minOffset: 0,
  maxLimit: 50,
  maxOffset: 100_000,
  minLimit: 1,
  invalidLimitMessage: (max: number): string =>
    `limit must be an integer between 1 and ${max}`,
  invalidOffsetMessage: (max: number): string =>
    `offset must be an integer between 0 and ${max}`,
} as const;

export const apiPaths = {
  startups: '/api/v1/startups',
  catalogs: '/api/v1/catalogs',
  profile: '/api/v1/me',
  preferences: '/api/v1/me/preferences',
  discoveryFeed: '/api/v1/me/discovery-feed',
  savedProjects: '/api/v1/me/saved-projects',
  interestedProjects: '/api/v1/me/interested-projects',
  projectFeedback: '/api/v1/me/project-feedback',
  projects: '/api/v1/projects',
} as const;
