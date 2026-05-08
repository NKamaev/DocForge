import api from './axiosInstance'
import type { DocumentMember, FolderMember, MemberRole } from '../types/member'

export const membersApi = {
  getFolderMembers: (folderId: string): Promise<FolderMember[]> =>
    api.get<FolderMember[]>(`/folders/${folderId}/members`).then(r => r.data),

  grantFolderRole: (folderId: string, userId: string, role: MemberRole): Promise<FolderMember> =>
    api.post<FolderMember>(`/folders/${folderId}/members`, { userId, role }).then(r => r.data),

  updateFolderRole: (folderId: string, userId: string, role: MemberRole): Promise<FolderMember> =>
    api.put<FolderMember>(`/folders/${folderId}/members/${userId}`, { role }).then(r => r.data),

  revokeFolderMember: (folderId: string, userId: string): Promise<void> =>
    api.delete(`/folders/${folderId}/members/${userId}`).then(() => undefined),

  getDocumentMembers: (documentId: string): Promise<DocumentMember[]> =>
    api.get<DocumentMember[]>(`/documents/${documentId}/members`).then(r => r.data),

  grantDocumentRole: (documentId: string, userId: string, role: MemberRole): Promise<DocumentMember> =>
    api.post<DocumentMember>(`/documents/${documentId}/members`, { userId, role }).then(r => r.data),

  updateDocumentRole: (documentId: string, userId: string, role: MemberRole): Promise<DocumentMember> =>
    api.put<DocumentMember>(`/documents/${documentId}/members/${userId}`, { role }).then(r => r.data),

  revokeDocumentMember: (documentId: string, userId: string): Promise<void> =>
    api.delete(`/documents/${documentId}/members/${userId}`).then(() => undefined),
}
