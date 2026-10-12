import { describe, expect, it } from 'vitest'
import { projectImageUrl } from './project-image'

describe('projectImageUrl', () => {
  it('accepts public image URLs with query parameters', () => {
    expect(projectImageUrl(' https://images.example.test/cover.jpg?w=1200 '))
      .toBe('https://images.example.test/cover.jpg?w=1200')
  })

  it.each([null, undefined, '', 42, '/cover.jpg', 'javascript:alert(1)',
    'data:image/svg+xml,test', 'https://user:password@example.test/cover.jpg'])
  ('rejects missing or unsafe image URLs: %s', (value) => {
    expect(projectImageUrl(value)).toBeNull()
  })
})
