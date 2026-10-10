import { useEffect, useState } from 'react'
import {
  Avatar,
  AvatarFallback,
  Button,
  Input,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Separator,
} from '@/components/ui'
import { Trash2 } from 'lucide-react'
import { membersApi } from '../api/membersApi'
import { usersApi } from '../api/usersApi'
import type { DocumentMember, FolderMember, MemberRole } from '../types/member'
import { roleImplies } from '../types/member'

interface ShareDialogProps {
  open: boolean
  onClose: () => void
  resourceType: 'folder' | 'document'
  resourceId: string
  resourceName: string
  myRole: MemberRole
}

const ROLE_LABELS: Record<MemberRole, string> = {
  VIEWER: 'Просмотр',
  EDITOR: 'Редактор',
  MANAGER: 'Менеджер',
  OWNER: 'Владелец',
}

const ASSIGNABLE_ROLES: MemberRole[] = ['VIEWER', 'EDITOR', 'MANAGER']

type AnyMember = FolderMember | DocumentMember

function showNotification(type: 'success' | 'error', message: string) {
  console.log(`[${type.toUpperCase()}]`, message)
  if (type === 'error') {
    alert(message)
  }
}

export default function ShareDialog({
  open, onClose, resourceType, resourceId, resourceName, myRole,
}: ShareDialogProps) {
  const [members, setMembers] = useState<AnyMember[]>([])
  const [loginInput, setLoginInput] = useState('')
  const [addRole, setAddRole] = useState<MemberRole>('VIEWER')
  const [adding, setAdding] = useState(false)

  const canManage = roleImplies(myRole, 'share')

  const loadMembers = async () => {
    try {
      const data = resourceType === 'folder'
        ? await membersApi.getFolderMembers(resourceId)
        : await membersApi.getDocumentMembers(resourceId)
      setMembers(data)
    } catch {
      showNotification('error', 'Не удалось загрузить участников')
    }
  }

  useEffect(() => {
    if (!open) return
    setLoginInput('')
    setAddRole('VIEWER')
    loadMembers()
  }, [open, resourceId])

  const handleRoleChange = async (userId: string, role: MemberRole) => {
    try {
      if (resourceType === 'folder') {
        await membersApi.updateFolderRole(resourceId, userId, role)
      } else {
        await membersApi.updateDocumentRole(resourceId, userId, role)
      }
      await loadMembers()
    } catch {
      showNotification('error', 'Не удалось изменить роль')
    }
  }

  const handleRevoke = async (userId: string) => {
    try {
      if (resourceType === 'folder') {
        await membersApi.revokeFolderMember(resourceId, userId)
      } else {
        await membersApi.revokeDocumentMember(resourceId, userId)
      }
      await loadMembers()
    } catch {
      showNotification('error', 'Не удалось удалить участника')
    }
  }

  const handleGrant = async () => {
    const login = loginInput.trim()
    if (!login) return
    setAdding(true)
    try {
      const user = await usersApi.searchByLogin(login)
      if (resourceType === 'folder') {
        await membersApi.grantFolderRole(resourceId, user.id, addRole)
      } else {
        await membersApi.grantDocumentRole(resourceId, user.id, addRole)
      }
      setLoginInput('')
      await loadMembers()
      showNotification('success', `Доступ выдан: ${login}`)
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      showNotification('error', msg ?? 'Не удалось выдать доступ')
    } finally {
      setAdding(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[480px]" aria-describedby={undefined}>
        <DialogHeader>
          <DialogTitle>Доступ к «{resourceName}»</DialogTitle>
        </DialogHeader>

        <div className="mt-2 flex flex-col gap-2.5">
          {members.map(m => {
            const isOwner = m.role === 'OWNER'
            const isInherited = 'source' in m && (m as DocumentMember).source === 'FOLDER_INHERIT'
            const isReadOnly = isOwner || isInherited || !canManage

            return (
              <div key={m.userId} className="flex items-center gap-2.5">
                <Avatar className="h-8 w-8 bg-primary flex-shrink-0">
                  <AvatarFallback className="bg-primary text-primary-foreground">
                    {(m.userDisplayName ?? m.userLogin).charAt(0).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <div className="flex-1 min-w-0">
                  <div className="font-semibold text-sm block">
                    {m.userDisplayName ?? m.userLogin}
                  </div>
                  <div className="text-muted-foreground text-xs">
                    @{m.userLogin}
                    {isInherited && <span className="ml-1.5">· Из папки</span>}
                  </div>
                </div>
                <Select
                  value={m.role}
                  disabled={isReadOnly}
                  onValueChange={(role: MemberRole) => handleRoleChange(m.userId, role)}
                >
                  <SelectTrigger className="w-[120px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ASSIGNABLE_ROLES.map(r => (
                      <SelectItem key={r} value={r}>
                        {ROLE_LABELS[r]}
                      </SelectItem>
                    ))}
                    {isOwner && (
                      <SelectItem value="OWNER">
                        {ROLE_LABELS.OWNER}
                      </SelectItem>
                    )}
                  </SelectContent>
                </Select>
                {canManage && !isReadOnly && (
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    className="h-8 w-8 text-destructive"
                    onClick={() => handleRevoke(m.userId)}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                )}
              </div>
            )
          })}
          {members.length === 0 && (
            <div className="text-center py-3 text-muted-foreground text-sm">
              Нет участников
            </div>
          )}
        </div>

        {canManage && (
          <>
            <Separator className="my-4" />
            <div className="flex items-center gap-2">
              <Input
                value={loginInput}
                onChange={e => setLoginInput(e.target.value)}
                placeholder="Введите логин"
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    handleGrant()
                  }
                }}
                className="flex-1"
              />
              <Select
                value={addRole}
                onValueChange={v => setAddRole(v as MemberRole)}
              >
                <SelectTrigger className="w-[130px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ASSIGNABLE_ROLES.map(r => (
                    <SelectItem key={r} value={r}>
                      {ROLE_LABELS[r]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button onClick={handleGrant} disabled={adding}>
                {adding ? 'Выдача...' : 'Выдать доступ'}
              </Button>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
