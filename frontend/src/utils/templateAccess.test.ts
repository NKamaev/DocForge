import { describe, it, expect } from 'vitest'
import {
  isTemplateOwner,
  canManageTemplate,
  canEditTemplate,
  getReadOnlyHint,
  getTemplateMutationRights,
} from './templateAccess'

describe('isTemplateOwner', () => {
  it('is true for the owner of the template', () => {
    expect(isTemplateOwner({ ownerId: 'u-1' }, 'u-1')).toBe(true)
  })

  it('is false for another user', () => {
    expect(isTemplateOwner({ ownerId: 'u-1' }, 'u-2')).toBe(false)
  })

  it('is false for a template without an owner (legacy templates)', () => {
    expect(isTemplateOwner({ ownerId: null }, 'u-1')).toBe(false)
    expect(isTemplateOwner({ ownerId: undefined }, 'u-1')).toBe(false)
    expect(isTemplateOwner({}, 'u-1')).toBe(false)
  })

  it('is false without a template or a user', () => {
    expect(isTemplateOwner(null, 'u-1')).toBe(false)
    expect(isTemplateOwner(undefined, 'u-1')).toBe(false)
    expect(isTemplateOwner({ ownerId: 'u-1' }, null)).toBe(false)
    expect(isTemplateOwner({ ownerId: 'u-1' }, undefined)).toBe(false)
  })
})

describe('canManageTemplate', () => {
  it('allows the owner', () => {
    expect(canManageTemplate({ ownerId: 'u-1' }, { id: 'u-1', admin: false })).toBe(true)
  })

  it('allows an administrator even for a foreign template', () => {
    expect(canManageTemplate({ ownerId: 'u-1' }, { id: 'u-2', admin: true })).toBe(true)
    expect(canManageTemplate({ ownerId: null }, { id: 'u-2', admin: true })).toBe(true)
  })

  it('denies a non-owner', () => {
    expect(canManageTemplate({ ownerId: 'u-1' }, { id: 'u-2', admin: false })).toBe(false)
  })

  it('denies when there is no user or no template', () => {
    expect(canManageTemplate({ ownerId: 'u-1' }, null)).toBe(false)
    expect(canManageTemplate(null, { id: 'u-1', admin: false })).toBe(false)
  })
})

describe('canEditTemplate', () => {
  it('allows the owner to edit an unpublished template', () => {
    expect(canEditTemplate({ ownerId: 'u-1' }, { id: 'u-1', admin: false }, false)).toBe(true)
  })

  it('allows an administrator to edit an unpublished template', () => {
    expect(canEditTemplate({ ownerId: 'u-1' }, { id: 'u-2', admin: true }, false)).toBe(true)
  })

  it('denies editing a published template (server rejects PUT with 409)', () => {
    expect(canEditTemplate({ ownerId: 'u-1' }, { id: 'u-1', admin: false }, true)).toBe(false)
    expect(canEditTemplate({ ownerId: 'u-1' }, { id: 'u-2', admin: true }, true)).toBe(false)
  })

  it('denies editing a foreign template', () => {
    expect(canEditTemplate({ ownerId: 'u-1' }, { id: 'u-2', admin: false }, false)).toBe(false)
  })

  it('denies editing while the user identity is unknown', () => {
    expect(canEditTemplate({ ownerId: 'u-1' }, null, false)).toBe(false)
    expect(canEditTemplate(null, { id: 'u-1', admin: false }, false)).toBe(false)
  })
})

describe('getReadOnlyHint', () => {
  it('explains that a non-owner cannot edit', () => {
    expect(getReadOnlyHint({ ownerId: 'u-1' }, { id: 'u-2', admin: false })).toBe(
      'Вы не владелец шаблона — редактирование недоступно',
    )
  })

  it('is absent for the owner and for an administrator', () => {
    expect(getReadOnlyHint({ ownerId: 'u-1' }, { id: 'u-1', admin: false })).toBeNull()
    expect(getReadOnlyHint({ ownerId: 'u-1' }, { id: 'u-2', admin: true })).toBeNull()
  })

  it('is absent while the user identity or the template is unknown', () => {
    expect(getReadOnlyHint({ ownerId: 'u-1' }, null)).toBeNull()
    expect(getReadOnlyHint(null, { id: 'u-2', admin: false })).toBeNull()
  })
})

/**
 * BUG-068 — сервер требует владельца (или администратора) для любой
 * изменяющей операции: publish, unpublish, delete, POST /versions, rollback.
 * Фронтенд не должен предлагать эти действия чужим шаблонам.
 */
describe('getTemplateMutationRights', () => {
  it('grants every mutating action to the owner', () => {
    expect(getTemplateMutationRights({ ownerId: 'u-1' }, { id: 'u-1', admin: false })).toEqual({
      canPublish: true,
      canDelete: true,
      canSaveVersion: true,
      canRollback: true,
    })
  })

  it('grants every mutating action to an administrator', () => {
    expect(getTemplateMutationRights({ ownerId: 'u-1' }, { id: 'u-2', admin: true })).toEqual({
      canPublish: true,
      canDelete: true,
      canSaveVersion: true,
      canRollback: true,
    })
  })

  it('denies every mutating action to a non-owner', () => {
    expect(getTemplateMutationRights({ ownerId: 'u-1' }, { id: 'u-2', admin: false })).toEqual({
      canPublish: false,
      canDelete: false,
      canSaveVersion: false,
      canRollback: false,
    })
  })

  it('denies every mutating action for a template without an owner', () => {
    expect(getTemplateMutationRights({ ownerId: null }, { id: 'u-1', admin: false })).toEqual({
      canPublish: false,
      canDelete: false,
      canSaveVersion: false,
      canRollback: false,
    })
  })

  it('is fail-closed while the user identity is unknown', () => {
    expect(getTemplateMutationRights({ ownerId: 'u-1' }, null)).toEqual({
      canPublish: false,
      canDelete: false,
      canSaveVersion: false,
      canRollback: false,
    })
    expect(getTemplateMutationRights(null, { id: 'u-1', admin: false })).toEqual({
      canPublish: false,
      canDelete: false,
      canSaveVersion: false,
      canRollback: false,
    })
  })
})
