import api from './axiosInstance'
import type {
  Directory, DirectoryListItem,
  CreateDirectoryRequest, UpdateDirectoryRequest, DirectoryEntryRequest,
} from '../types/directory'

export const directoriesApi = {
  list: () =>
    api.get<DirectoryListItem[]>('/directories').then(r => r.data),

  getById: (id: string) =>
    api.get<Directory>(`/directories/${id}`).then(r => r.data),

  create: (data: CreateDirectoryRequest) =>
    api.post<Directory>('/directories', data).then(r => r.data),

  update: (id: string, data: UpdateDirectoryRequest) =>
    api.put<Directory>(`/directories/${id}`, data).then(r => r.data),

  delete: (id: string) =>
    api.delete(`/directories/${id}`),

  addEntry: (id: string, data: DirectoryEntryRequest) =>
    api.post<Directory>(`/directories/${id}/entries`, data).then(r => r.data),

  updateEntry: (id: string, entryId: string, data: DirectoryEntryRequest) =>
    api.put<Directory>(`/directories/${id}/entries/${entryId}`, data).then(r => r.data),

  deleteEntry: (id: string, entryId: string) =>
    api.delete<Directory>(`/directories/${id}/entries/${entryId}`).then(r => r.data),
}
