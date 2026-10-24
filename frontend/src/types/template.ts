import type { Parameter, Questionnaire, Script, DocumentFormatting, Phrasing } from './templateModel'

export type { Parameter, Questionnaire, Script, DocumentFormatting, Phrasing }

export interface TemplateListItem {
  id: string
  code: string | null
  name: string
  description: string | null
  category: string | null
  published: boolean
  majorVersion: number
  minorVersion: number
  createdAt: string
  /**
   * Владелец шаблона.
   *
   * Сервер (BUG-068) отдаёт его в списке шаблонов, чтобы клиент мог скрыть
   * изменяющие действия (публикация, удаление) для чужих шаблонов: владельцем
   * является только он или администратор, остальным сервер отвечает 403.
   */
  ownerId: string | null
}

export interface Template {
  id: string
  code: string | null
  name: string
  description: string | null
  category: string | null
  version: number
  majorVersion: number
  minorVersion: number
  published: boolean
  /**
   * Владелец шаблона. Колонка в БД nullable — шаблон без владельца
   * (`ownerId == null`) не принадлежит никому (см. `isTemplateOwner`).
   */
  ownerId: string | null
  defaultFolderId: string | null
  createdAt: string
  updatedAt: string
  parameters: Parameter[] | null
  questionnaire: Questionnaire | null
  body: Record<string, unknown> | null
  formatting: DocumentFormatting | null
  scripts: Script[] | null
  phrasings: Phrasing[] | null
}

export interface CreateTemplateRequest {
  name: string
  description?: string
  category?: string
}

export interface UpdateTemplateRequest {
  name: string
  description?: string
  category?: string
  parameters?: Parameter[]
  questionnaire?: Questionnaire
  body?: Record<string, unknown>
  scripts?: Script[]
  formatting?: DocumentFormatting
  phrasings?: Phrasing[]
  defaultFolderId?: string | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface TemplateListParams {
  page?: number
  size?: number
  category?: string
  published?: boolean
  /** ISO date-time; вернуть только шаблоны, созданные не раньше указанного момента */
  createdAfter?: string
}

/** Immutable snapshot created on each template publish. Mirrors backend TemplateSnapshot. */
export interface TemplateSnapshot {
  id: string
  templateId: string
  versionNumber: number
  majorVersion: number
  minorVersion: number
  parameters: Parameter[] | null
  questionnaire: Questionnaire | null
  body: Record<string, unknown> | null
  formatting: DocumentFormatting | null
  scripts: Script[] | null
  phrasings: Phrasing[] | null
  createdAt: string
}

/**
 * Тип изменения версии шаблона. Зеркалит backend TemplateVersion.bumpType.
 *
 * - `PUBLISH` — версия создана публикацией шаблона;
 * - `MAJOR` / `MINOR` — черновик, созданный `saveVersion` (не-владельцу недоступен);
 * - `ROLLBACK` — откат (сервер разрешает не-владельцу только если откат сделан
 *   с опубликованной версии — определить это на клиенте невозможно).
 */
export type TemplateVersionBumpType = 'PUBLISH' | 'MAJOR' | 'MINOR' | 'ROLLBACK'

/** Version metadata from PostgreSQL. Mirrors backend TemplateVersionDTO. */
export interface TemplateVersionDTO {
  id: string
  templateId: string
  versionNumber: number
  majorVersion: number
  minorVersion: number
  createdAt: string
  createdBy: string | null
  comment: string | null
  bumpType: TemplateVersionBumpType | null
  autoDescription: string | null
  rollbackFrom: number | null
}

export interface SaveVersionRequest {
  bumpType: 'MAJOR' | 'MINOR'
  comment?: string
}

export interface SaveVersionResponse {
  version: TemplateVersionDTO
  template: Template
}

export interface RollbackResponse {
  version: TemplateVersionDTO
  template: Template
}

export interface DiffResult {
  summary: string
  changes: string[]
  hasChanges: boolean
}
