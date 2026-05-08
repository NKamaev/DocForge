import api from './axiosInstance'
import type { Folder, FolderRequest } from '../types/folder'

export const foldersApi = {
  list: () =>
    api.get<Folder[]>('/folders').then(r => r.data),

  create: (data: FolderRequest) =>
    api.post<Folder>('/folders', data).then(r => r.data),

  update: (id: string, data: FolderRequest) =>
    api.put<Folder>(`/folders/${id}`, data).then(r => r.data),

  delete: (id: string) =>
    api.delete(`/folders/${id}`),
}
