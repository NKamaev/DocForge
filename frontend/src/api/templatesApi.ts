import api from './axiosInstance'
import type {
  Template,
  TemplateListItem,
  CreateTemplateRequest,
  UpdateTemplateRequest,
  PageResponse,
  TemplateListParams,
  TemplateSnapshot,
} from '../types/template'

export const templatesApi = {
  getTemplates: (params: TemplateListParams = {}) =>
    api
      .get<PageResponse<TemplateListItem>>('/templates', { params })
      .then((r) => r.data),

  getTemplate: (id: string) =>
    api.get<Template>(`/templates/${id}`).then((r) => r.data),

  createTemplate: (data: CreateTemplateRequest) =>
    api.post<Template>('/templates', data).then((r) => r.data),

  updateTemplate: (id: string, data: UpdateTemplateRequest) =>
    api.put<Template>(`/templates/${id}`, data).then((r) => r.data),

  deleteTemplate: (id: string) =>
    api.delete(`/templates/${id}`),

  publishTemplate: (id: string) =>
    api.post<Template>(`/templates/${id}/publish`).then((r) => r.data),

  unpublishTemplate: (id: string) =>
    api.post<Template>(`/templates/${id}/unpublish`).then((r) => r.data),

  exportDocx: (id: string): Promise<Blob> =>
    api
      .post(`/templates/${id}/export/docx`, null, { responseType: 'blob' })
      .then((r) => r.data as Blob),

  exportPdf: (id: string): Promise<Blob> =>
    api
      .post(`/templates/${id}/export/pdf`, null, { responseType: 'blob' })
      .then((r) => r.data as Blob),

  getSnapshot: (templateId: string, versionNumber: number): Promise<TemplateSnapshot> =>
    api
      .get<TemplateSnapshot>(`/templates/${templateId}/versions/${versionNumber}`)
      .then((r) => r.data),
}
