export interface DocumentListItem {
  id: string
  name: string
  status: 'DRAFT' | 'GENERATED' | 'EXPORTED'
  templateId: string
  folderId: string | null
  createdAt: string
}

export interface Document {
  id: string
  name: string
  status: 'DRAFT' | 'GENERATED' | 'EXPORTED'
  templateId: string
  templateSnapshotId: string | null
  templateVersionNumber: number | null
  ownerId: string
  folderId: string | null
  createdAt: string
  updatedAt: string
  answers: Record<string, unknown> | null
  generatedBody: unknown | null
  currentBody: unknown | null
  questionnaire: Questionnaire | null
}

export interface Questionnaire {
  title: string | null
  pages: QuestionnairePage[]
}

export interface QuestionnairePage {
  id: string | null
  title: string
  questions: QuestionnaireQuestion[]
}

export interface QuestionnaireQuestion {
  id: string
  parameterCode: string
  label: string
  hint: string | null
  order: number
  type: 'text' | 'number' | 'date' | 'fio' | 'boolean' | 'select' | 'directory'
  options: string[] | null
  visible: boolean
  required: boolean
  currentValue: unknown
  directoryId?: string | null
  directoryFields?: string[] | null
}

export interface CreateDocumentRequest {
  templateId: string
  name: string
  folderId?: string | null
  templateVersionNumber?: number | null
}

export interface SaveAnswersRequest {
  answers: Record<string, unknown>
}

export interface SaveAnswersResponse {
  answers: Record<string, unknown>
  questionnaire: Questionnaire
}

export interface GenerateResponse {
  documentId: string
  body: unknown
}

export interface RevisionMeta {
  id: string
  editionNumber: number
  savedAt: string
  savedBy?: string | null
  authorName?: string | null
  comment?: string | null
}

export type DiffChunkType = 'INSERT' | 'DELETE' | 'EQUAL'

export interface DiffChunk {
  type: DiffChunkType
  text: string
}

export interface RevisionDiff {
  revisionId: string
  revisionNumber: number
  compareToRevisionNumber: number | null
  compareToCurrent: boolean
  diff: DiffChunk[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}
