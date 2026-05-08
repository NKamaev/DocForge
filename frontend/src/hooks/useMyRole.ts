import { useState, useEffect } from 'react'
import { useAuthStore } from '../store/authStore'
import { membersApi } from '../api/membersApi'
import type { MemberRole } from '../types/member'

export function useMyFolderRole(folderId: string | null): MemberRole | null {
  const user = useAuthStore(s => s.user)
  const [role, setRole] = useState<MemberRole | null>(null)

  useEffect(() => {
    if (!folderId || !user) return
    let cancelled = false
    membersApi.getFolderMembers(folderId)
      .then(members => {
        if (cancelled) return
        const me = members.find(m => m.userId === user.id)
        setRole(me?.role ?? null)
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [folderId, user])

  return role
}

export function useMyDocumentRole(documentId: string | null): MemberRole | null {
  const user = useAuthStore(s => s.user)
  const [role, setRole] = useState<MemberRole | null>(null)

  useEffect(() => {
    if (!documentId || !user) return
    let cancelled = false
    membersApi.getDocumentMembers(documentId)
      .then(members => {
        if (cancelled) return
        const me = members.find(m => m.userId === user.id)
        setRole(me?.role ?? null)
      })
      .catch(() => {})
    return () => { cancelled = true }
  }, [documentId, user])

  return role
}
