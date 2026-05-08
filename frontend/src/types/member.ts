export type MemberRole = 'VIEWER' | 'EDITOR' | 'MANAGER' | 'OWNER'
export type MemberSource = 'DIRECT' | 'FOLDER_INHERIT'

export interface FolderMember {
  userId: string
  userLogin: string
  userDisplayName: string | null
  role: MemberRole
  grantedAt: string
}

export interface DocumentMember {
  userId: string
  userLogin: string
  userDisplayName: string | null
  role: MemberRole
  source: MemberSource
  grantedAt: string
}

export const roleImplies = (role: MemberRole, action: 'read' | 'write' | 'manage' | 'share' | 'delete'): boolean => {
  const weights: Record<MemberRole, number> = { VIEWER: 1, EDITOR: 2, MANAGER: 3, OWNER: 4 }
  const required: Record<string, number> = { read: 1, write: 2, manage: 3, share: 3, delete: 3 }
  return weights[role] >= required[action]
}
