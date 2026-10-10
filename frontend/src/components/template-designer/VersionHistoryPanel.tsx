import { useEffect, useState } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  Skeleton,
  showToast,
} from '@/components/ui'
import { templatesApi } from '../../api/templatesApi'
import type { TemplateVersionDTO } from '../../types/template'
import { Icon } from '../Icon'

interface Props {
  templateId: string
  onRollback: (versionNumber: number) => Promise<void>
  /** Trigger refetch when parent template version changes */
  refreshKey?: number
}

function showNotification(type: 'success' | 'error', message: string) {
  showToast(message, type === 'error' ? 'error' : 'success')
}

export default function VersionHistoryPanel({ templateId, onRollback, refreshKey }: Props) {
  const [versions, setVersions] = useState<TemplateVersionDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [rollbackTarget, setRollbackTarget] = useState<number | null>(null)
  const [rollingBack, setRollingBack] = useState(false)

  const load = () => {
    setLoading(true)
    templatesApi.getVersions(templateId)
      .then(setVersions)
      .catch(() => showNotification('error', 'Не удалось загрузить историю версий'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    if (templateId) load()
  }, [templateId, refreshKey])

  const handleRollback = async () => {
    if (rollbackTarget === null) return
    setRollingBack(true)
    try {
      await onRollback(rollbackTarget)
      setRollbackTarget(null)
      showNotification('success', 'Шаблон восстановлен до версии')
      load()
    } catch {
      showNotification('error', 'Не удалось выполнить откат')
    } finally {
      setRollingBack(false)
    }
  }

  const formatBumpType = (type: string | null) => {
    switch (type) {
      case 'MAJOR': return <span className="tag">Major</span>
      case 'MINOR': return <span className="tag">Minor</span>
      case 'ROLLBACK': return <span className="tag amber">Откат</span>
      case 'PUBLISH': return <span className="tag green">Публикация</span>
      default: return null
    }
  }

  if (loading) {
    return (
      <div style={{ padding: 16 }}>
        <Skeleton className="h-8 w-full mb-2" />
        <Skeleton className="h-8 w-full mb-2" />
        <Skeleton className="h-8 w-full" />
      </div>
    )
  }

  if (versions.length === 0) {
    return (
      <div className="empty" style={{ padding: 24 }}>
        <div className="ill"><Icon name="history" size={22} /></div>
        <div style={{ fontSize: 13.5, fontWeight: 500, color: 'var(--ink-1)', marginBottom: 4 }}>Нет версий</div>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', textAlign: 'center', maxWidth: 220 }}>
          При сохранении шаблона здесь появится история изменений.
        </div>
      </div>
    )
  }

  return (
    <div style={{ padding: '8px 0' }}>
      {versions.map((v, idx) => {
        const isLatest = idx === 0
        return (
          <div
            key={v.id}
            style={{
              padding: '10px 12px',
              borderBottom: '1px solid var(--line)',
              opacity: isLatest ? 1 : 0.75,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <span style={{ fontWeight: 600, fontSize: 13 }}>
                v{v.majorVersion}.{v.minorVersion}
              </span>
              {formatBumpType(v.bumpType)}
              {isLatest && <span className="tag" style={{ fontSize: 10 }}>Текущая</span>}
              {v.rollbackFrom != null && (
                <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  (из v{v.rollbackFrom})
                </span>
              )}
            </div>

            {v.autoDescription && (
              <div style={{ fontSize: 11.5, color: 'var(--ink-2)', marginBottom: 2 }}>
                {v.autoDescription}
              </div>
            )}

            {v.comment && (
              <div style={{ fontSize: 11.5, color: 'var(--ink-3)', fontStyle: 'italic', marginBottom: 2 }}>
                «{v.comment}»
              </div>
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: 'var(--ink-3)' }}>
              <span>№{v.versionNumber}</span>
              <span>·</span>
              <span>{new Date(v.createdAt).toLocaleString('ru-RU')}</span>
              {!isLatest && (
                <>
                  <span>·</span>
                  <button
                    className="btn xs"
                    onClick={() => setRollbackTarget(v.versionNumber)}
                    style={{ fontSize: 11, padding: '2px 6px' }}
                  >
                    <Icon name="undo" size={10} /> Откатить
                  </button>
                </>
              )}
            </div>
          </div>
        )
      })}

      <AlertDialog open={rollbackTarget !== null} onOpenChange={(o) => { if (!o) setRollbackTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Откатить шаблон?</AlertDialogTitle>
            <AlertDialogDescription>
              Содержимое шаблона будет восстановлено до выбранной версии.
              Будет создана новая версия с пометкой «Откат».
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={rollingBack}>Отмена</AlertDialogCancel>
            <AlertDialogAction onClick={handleRollback} disabled={rollingBack}>
              {rollingBack ? 'Откат...' : 'Откатить'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
