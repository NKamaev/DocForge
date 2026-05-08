import type { Parameter, Questionnaire, Script, DocumentFormatting, Phrasing } from './templateModel'

export type { Parameter, Questionnaire, Script, DocumentFormatting, Phrasing }

export interface TemplateListItem {
  id: string
  code: string | null
  name: string
  description: string | null
  category: string | null
  published: boolean
  createdAt: string
}

export interface Template {
  id: string
  code: string | null
  name: string
  description: string | null
  category: string | null
  version: number
  published: boolean
  ownerId: string
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
}

/** Immutable snapshot created on each template publish. Mirrors backend TemplateSnapshot. */
export interface TemplateSnapshot {
  id: string
  templateId: string
  versionNumber: number
  parameters: Parameter[] | null
  questionnaire: Questionnaire | null
  body: Record<string, unknown> | null
  formatting: DocumentFormatting | null
  scripts: Script[] | null
  phrasings: Phrasing[] | null
  createdAt: string
}
