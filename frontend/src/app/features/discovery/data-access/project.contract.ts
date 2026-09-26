export const projectContract = {
  slugMinLength: 2,
  slugMaxLength: 120,
  nameMaxLength: 160,
  taglineMaxLength: 240,
  descriptionMaxLength: 5000,
  cityMaxLength: 120,
} as const;

export const profileContract = {
  displayNameMaxLength: 120,
  shortBioMaxLength: 500,
  cityMaxLength: 120,
  departmentMaxLength: 120,
} as const;
