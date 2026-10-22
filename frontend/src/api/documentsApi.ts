import api from './axiosInstance'
import type {
  Document,
  DocumentListItem,
  CreateDocumentRequest,
  SaveAnswersRequest,
  SaveAnswersResponse,
  GenerateResponse,
  Questionnaire,
  PageResponse,
  RevisionMeta,
  RevisionDiff,
} from '../types/document'

export const documentsApi = {
  list: (page = 0, size = 20) =>
    api.get<PageResponse<DocumentListItem>>('/documents', { params: { page, size } })
       .then(r => r.data),

  getById: (id: string) =>
    api.get<Document>(`/documents/${id}`).then(r => r.data),

  create: (data: CreateDocumentRequest) =>
    api.post<Document>('/documents', data).then(r => r.data),

  delete: (id: string) =>
    api.delete(`/documents/${id}`).then(() => undefined),

  getQuestionnaire: (id: string) =>
    api.get<Questionnaire>(`/documents/${id}/questionnaire`).then(r => r.data),

  saveAnswers: (id: string, data: SaveAnswersRequest) =>
    api.put<SaveAnswersResponse>(`/documents/${id}/questionnaire/answers`, data)
       .then(r => r.data),

  generate: (id: string) =>
    api.post<GenerateResponse>(`/documents/${id}/generate`).then(r => r.data),

  updateBody: (id: string, currentBody: unknown) =>
    api.put(`/documents/${id}/body`, { currentBody }).then(r => r.data),

  exportDocx: (id: string) =>
    api.get(`/documents/${id}/export/docx`, { responseType: 'blob' }).then(r => r.data as Blob),

  exportPdf: (id: string) =>
    api.get(`/documents/${id}/export/pdf`, { responseType: 'blob' }).then(r => r.data as Blob),

  listRevisions: (id: string) =>
    api.get<RevisionMeta[]>(`/documents/${id}/revisions`).then(r => r.data),

  getRevision: (id: string, revNum: number) =>
    api.get(`/documents/${id}/revisions/${revNum}`).then(r => r.data),

  saveRevision: (id: string, comment?: string) =>
    api.post(`/documents/${id}/revisions`, null, { params: { comment } }).then(r => r.data),

  restoreRevision: (id: string, revNum: number) =>
    api.post(`/documents/${id}/revisions/${revNum}/restore`).then(r => r.data),

  getRevisionDiff: (id: string, revNum: number, compareTo?: number) =>
    api.get<RevisionDiff>(`/documents/${id}/revisions/${revNum}/diff`, {
      params: { compareTo },
    }).then(r => r.data),

  moveToFolder: (id: string, folderId: string | null) =>
    api.patch(`/documents/${id}/folder`, { folderId }),
}
