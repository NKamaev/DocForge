import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
} from '@/components/ui'
import { directoriesApi } from '../api/directoriesApi'
import type { DirectoryListItem } from '../types/directory'
import { Icon } from '../components/Icon'

function showNotification(type: 'success' | 'error', message: string) {
  console.log(`[${type.toUpperCase()}]`, message)
  if (type === 'error') {
    alert(message)
  }
}

export default function DirectoriesPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<DirectoryListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [errors, setErrors] = useState<{ name?: string }>({})

  const load = () => {
    setLoading(true)
    directoriesApi
      .list()
      .then(setItems)
      .catch(() => showNotification('error', 'Не удалось загрузить справочники'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const handleReset = () => {
    setName('')
    setErrors({})
  }

  const handleCreate = async () => {
    if (!name.trim()) {
      setErrors({ name: 'Введите название' })
      return
    }
    setSaving(true)
    try {
      await directoriesApi.create({ name: name.trim(), fields: [] })
      setModalOpen(false)
      handleReset()
      load()
    } catch {
      showNotification('error', 'Ошибка создания')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="crumb">
            <Icon name="home" size={12} />
            <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>Главная</span>
            <Icon name="chev-right" size={11} />
            <span>Справочники</span>
          </div>
          <h1>Справочники</h1>
          <p>Структурированные данные для повторного использования в шаблонах: города, организации, сотрудники.</p>
        </div>
        <div className="page-head-actions">
          <button className="btn primary" onClick={() => setModalOpen(true)}>
            <Icon name="plus" size={14} /> Создать справочник
          </button>
        </div>
      </div>

      {loading ? (
        <div style={{ padding: 48, textAlign: 'center', color: 'var(--ink-3)' }}>Загрузка…</div>
      ) : items.length === 0 ? (
        <div className="card pad" style={{ textAlign: 'center', padding: 48 }}>
          <div style={{ color: 'var(--ink-3)', marginBottom: 16 }}>Нет справочников</div>
          <button className="btn primary" onClick={() => setModalOpen(true)}>
            <Icon name="plus" size={14} /> Создать первый
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
          {items.map((d) => (
            <div
              key={d.id}
              className="card hover pad"
              onClick={() => navigate(`/directories/${d.id}`)}
              style={{ display: 'flex', flexDirection: 'column', gap: 10 }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: 'var(--bg-soft)', color: 'var(--ink-2)', display: 'grid', placeItems: 'center', border: '1px solid var(--line)' }}>
                  <Icon name="book" size={16} />
                </div>
                <Icon name="chev-right" size={14} />
              </div>
              <div style={{ fontSize: 15, fontWeight: 550 }}>{d.name}</div>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11.5, color: 'var(--ink-3)' }}>
                <span>
                  <b className="tabular" style={{ color: 'var(--ink-1)', fontWeight: 600 }}>{d.entryCount}</b> записей
                </span>
                <span>{d.fields.length} полей</span>
                <span>{new Date().toLocaleDateString('ru-RU')}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      <Dialog open={modalOpen} onOpenChange={(open) => {
        if (!open) {
          setModalOpen(false)
          handleReset()
        }
      }}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>Новый справочник</DialogTitle>
          </DialogHeader>
          <div className="mt-4 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="directory-name">
                Название <span className="text-destructive">*</span>
              </Label>
              <Input
                id="directory-name"
                value={name}
                onChange={(e) => {
                  setName(e.target.value)
                  if (errors.name) setErrors({})
                }}
                placeholder="Список банков"
                autoFocus
                className={errors.name ? 'border-destructive' : ''}
              />
              {errors.name && (
                <p className="text-xs text-destructive">{errors.name}</p>
              )}
            </div>
          </div>
          <DialogFooter className="mt-6">
            <Button variant="outline" onClick={() => { setModalOpen(false); handleReset() }}>
              Отмена
            </Button>
            <Button onClick={handleCreate} disabled={saving}>
              {saving ? 'Создание...' : 'Создать'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
