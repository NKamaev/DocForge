import { useEffect, useState } from 'react'
import { Avatar, Button, Input, Modal, Select, Space, Typography, notification } from 'antd'
import { DeleteOutlined, UserOutlined } from '@ant-design/icons'
import { membersApi } from '../api/membersApi'
import { usersApi } from '../api/usersApi'
import type { DocumentMember, FolderMember, MemberRole } from '../types/member'
import { roleImplies } from '../types/member'

const { Text } = Typography

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
      notification.error({ message: 'Не удалось загрузить участников' })
    }
  }

  useEffect(() => {
    if (!open) return
    setLoginInput('')
    setAddRole('VIEWER')
    loadMembers()
  }, [open, resourceId]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleRoleChange = async (userId: string, role: MemberRole) => {
    try {
      if (resourceType === 'folder') {
        await membersApi.updateFolderRole(resourceId, userId, role)
      } else {
        await membersApi.updateDocumentRole(resourceId, userId, role)
      }
      await loadMembers()
    } catch {
      notification.error({ message: 'Не удалось изменить роль' })
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
      notification.error({ message: 'Не удалось удалить участника' })
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
      notification.success({ message: `Доступ выдан: ${login}` })
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      notification.error({ message: msg ?? 'Не удалось выдать доступ' })
    } finally {
      setAdding(false)
    }
  }

  return (
    <Modal
      title={`Доступ к «${resourceName}»`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={480}
      destroyOnHidden
    >
      <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {members.map(m => {
          const isOwner = m.role === 'OWNER'
          const isInherited = 'source' in m && (m as DocumentMember).source === 'FOLDER_INHERIT'
          const isReadOnly = isOwner || isInherited || !canManage

          return (
            <div key={m.userId} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Avatar size={32} icon={<UserOutlined />} style={{ flexShrink: 0, background: '#1677ff' }}>
                {(m.userDisplayName ?? m.userLogin).charAt(0).toUpperCase()}
              </Avatar>
              <div style={{ flex: 1, minWidth: 0 }}>
                <Text strong style={{ display: 'block', fontSize: 13 }}>
                  {m.userDisplayName ?? m.userLogin}
                </Text>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  @{m.userLogin}
                  {isInherited && <span style={{ marginLeft: 6 }}>· Из папки</span>}
                </Text>
              </div>
              <Select
                size="small"
                value={m.role}
                disabled={isReadOnly}
                onChange={(role: MemberRole) => handleRoleChange(m.userId, role)}
                options={[
                  ...ASSIGNABLE_ROLES.map(r => ({ value: r, label: ROLE_LABELS[r] })),
                  ...(isOwner ? [{ value: 'OWNER' as MemberRole, label: ROLE_LABELS.OWNER }] : []),
                ]}
                style={{ width: 120 }}
              />
              {canManage && !isReadOnly && (
                <Button
                  type="text"
                  danger
                  size="small"
                  icon={<DeleteOutlined />}
                  onClick={() => handleRevoke(m.userId)}
                />
              )}
            </div>
          )
        })}
        {members.length === 0 && (
          <Text type="secondary" style={{ textAlign: 'center', padding: '12px 0', display: 'block' }}>
            Нет участников
          </Text>
        )}
      </div>

      {canManage && (
        <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid var(--line)' }}>
          <Space.Compact style={{ width: '100%' }}>
            <Input
              value={loginInput}
              onChange={e => setLoginInput(e.target.value)}
              placeholder="Введите логин"
              onPressEnter={handleGrant}
              style={{ flex: 1 }}
            />
            <Select
              value={addRole}
              onChange={v => setAddRole(v)}
              options={ASSIGNABLE_ROLES.map(r => ({ value: r, label: ROLE_LABELS[r] }))}
              style={{ width: 130 }}
            />
            <Button type="primary" onClick={handleGrant} loading={adding}>
              Выдать доступ
            </Button>
          </Space.Compact>
        </div>
      )}
    </Modal>
  )
}
