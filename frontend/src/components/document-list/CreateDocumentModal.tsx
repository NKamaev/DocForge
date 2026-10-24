import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileText, History } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Label,
  Skeleton,
} from '@/components/ui'
import { templatesApi } from '../../api/templatesApi'
import { documentsApi } from '../../api/documentsApi'
import { foldersApi } from '../../api/foldersApi'
import { extractServerMessage, isForbiddenError } from '../../api/errorMessages'
import type { TemplateListItem, TemplateVersionDTO } from '../../types/template'
import type { Folder } from '../../types/folder'

/**
 * Может ли версия шаблона быть инстанцирована пользователем (не только владельцем)?
 *
 * Зеркалит серверную логику `TemplateServiceImpl#isPublished`:
 *  - `PUBLISH` — опубликованная версия — доступна всем;
 *  - `null` — легаси-строка (до появления `bump_type`), тоже публикация — доступна всем;
 *  - `MAJOR`/`MINOR` — черновик `saveVersion` — не-владельцу недоступен (403);
 *  - `ROLLBACK` — сервер разрешает только при откате с опубликованной версии,
 *    что фронтенд определить не может, поэтому такие версии не предлагаем.
 */
export function isInstantiableVersion(version: TemplateVersionDTO): boolean {
  const { bumpType } = version
  // `bumpType == null` — легаси-строка (до появления bump_type): тоже публикация.
  return bumpType === 'PUBLISH' || bumpType == null
}

/**
 * Является ли версия черновиком (создана `saveVersion`).
 *
 * `ROLLBACK` черновиком НЕ является: это откат, доступность которого сервер
 * определяет по источнику отката, поэтому в счётчик черновиков он не входит.
 */
export function isDraftVersion(version: TemplateVersionDTO): boolean {
  const { bumpType } = version
  return bumpType === 'MAJOR' || bumpType === 'MINOR'
}

/** Сводка по версиям шаблона: что можно выбрать и какие подсказки показывать. */
export interface TemplateVersionAvailability {
  /** Версии, доступные для инстанцирования (опубликованные и легаси-строки). */
  instantiable: TemplateVersionDTO[]
  /** Есть ли среди всех версий черновики (`MAJOR`/`MINOR`). */
  hasDrafts: boolean
  /** Есть ли у шаблона строки версий вообще. */
  hasVersionRows: boolean
}

/**
 * Разбирает список версий шаблона на инстанцируемые и черновики.
 *
 * Зеркалит серверную логику (`DocumentServiceImpl#create`):
 *  - не-владелец может инстанцировать только опубликованную версию;
 *  - если строки версий есть, но опубликованных нет — backend отвечает
 *    403 «Нет опубликованной версии шаблона»;
 *  - если строк версий нет вовсе — backend работает с live-шаблоном.
 */
export function analyzeTemplateVersions(all: TemplateVersionDTO[]): TemplateVersionAvailability {
  return {
    instantiable: all.filter(isInstantiableVersion),
    hasDrafts: all.some(isDraftVersion),
    hasVersionRows: all.length > 0,
  }
}

/**
 * Подсказка под селектором версии.
 *
 * Возвращает `null`, если версии есть и селектор показывается.
 */
export function getVersionAvailabilityHint(
  availability: TemplateVersionAvailability,
): string | null {
  if (availability.instantiable.length > 0) return null
  return availability.hasVersionRows
    ? 'У шаблона нет опубликованных версий — создать документ нельзя'
    : 'Нет опубликованных версий. Будет использована текущая версия шаблона.'
}

/**
 * Заметка о черновиках версий.
 *
 * Черновик (`MAJOR`/`MINOR`) может инстанцировать только автор шаблона —
 * остальным backend отвечает 403 «Нет доступа к черновой версии шаблона».
 */
export function getDraftVersionsNotice(
  availability: TemplateVersionAvailability,
): string | null {
  return availability.hasDrafts ? 'Черновики версий доступны только автору шаблона.' : null
}

function showNotification(type: 'success' | 'error' | 'warning', message: string) {
  console.log(`[${type.toUpperCase()}]`, message)
  if (type === 'error') {
    alert(message)
  }
}

interface Props {
  open: boolean
  onClose: () => void
  /** Pre-select this folder when modal opens (e.g. current folder on DocumentsPage) */
  defaultFolderId?: string | null
}

export default function CreateDocumentModal({ open, onClose, defaultFolderId }: Props) {
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [nameError, setNameError] = useState('')
  const [templates, setTemplates] = useState<TemplateListItem[]>([])
  const [folders, setFolders] = useState<Folder[]>([])
  const [loadingTemplates, setLoadingTemplates] = useState(false)
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null)
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  // Version selection
  const [availability, setAvailability] = useState<TemplateVersionAvailability>({
    instantiable: [],
    hasDrafts: false,
    hasVersionRows: false,
  })
  const [loadingVersions, setLoadingVersions] = useState(false)
  const [selectedVersionNumber, setSelectedVersionNumber] = useState<number | null>(null)
  const [versionsError, setVersionsError] = useState<string | null>(null)

  const versions = availability.instantiable
  const versionHint = getVersionAvailabilityHint(availability)
  const draftNotice = getDraftVersionsNotice(availability)

  useEffect(() => {
    if (!open) return
    setSelectedTemplateId(null)
    setSelectedFolderId(defaultFolderId ?? null)
    setName('')
    setNameError('')
    setAvailability({ instantiable: [], hasDrafts: false, hasVersionRows: false })
    setSelectedVersionNumber(null)
    setVersionsError(null)

    setLoadingTemplates(true)
    Promise.all([
      templatesApi.getTemplates({ published: true, size: 100 }),
      foldersApi.list(),
    ])
      .then(([tmplRes, folderRes]) => {
        setTemplates(tmplRes.content)
        setFolders(folderRes)
      })
      .catch(() => showNotification('error', 'Не удалось загрузить данные'))
      .finally(() => setLoadingTemplates(false))
  }, [open, defaultFolderId])

  // When template changes, load versions
  useEffect(() => {
    if (!selectedTemplateId) {
      setAvailability({ instantiable: [], hasDrafts: false, hasVersionRows: false })
      setSelectedVersionNumber(null)
      setVersionsError(null)
      return
    }
    setLoadingVersions(true)
    setVersionsError(null)
    templatesApi.getVersions(selectedTemplateId)
      .then((all) => {
        // Черновики (MAJOR/MINOR) и откаты (ROLLBACK) нельзя инстанцировать
        // не-владельцу шаблона — сервер вернёт 403. Оставляем только
        // инстанцируемые версии и по умолчанию выбираем новейшую из них.
        // Для не-владельца сервер и так отдаёт только опубликованные версии.
        const analyzed = analyzeTemplateVersions(all)
        const newest = analyzed.instantiable.reduce<TemplateVersionDTO | null>(
          (max, v) => (max === null || v.versionNumber > max.versionNumber ? v : max),
          null,
        )
        setAvailability(analyzed)
        setSelectedVersionNumber(newest ? newest.versionNumber : null)
      })
      .catch((err: unknown) => {
        setAvailability({ instantiable: [], hasDrafts: false, hasVersionRows: false })
        setSelectedVersionNumber(null)
        setVersionsError(
          isForbiddenError(err)
            ? 'Нет доступа к версиям шаблона'
            : (extractServerMessage(err) ?? 'Не удалось загрузить версии шаблона'),
        )
      })
      .finally(() => setLoadingVersions(false))
  }, [selectedTemplateId])

  // When template changes, pre-fill folder from template's defaultFolderId
  const handleTemplateSelect = (templateId: string) => {
    setSelectedTemplateId(templateId)
    const tmpl = templates.find(t => t.id === templateId)
    if (tmpl && 'defaultFolderId' in tmpl) {
      const dfi = (tmpl as { defaultFolderId?: string | null }).defaultFolderId
      if (dfi) setSelectedFolderId(dfi)
    }
  }

  const handleOk = async () => {
    if (!selectedTemplateId) {
      showNotification('warning', 'Выберите шаблон')
      return
    }
    if (!name.trim()) {
      setNameError('Введите название документа')
      return
    }
    setCreating(true)
    try {
      const doc = await documentsApi.create({
        templateId: selectedTemplateId,
        name: name.trim(),
        folderId: selectedFolderId ?? null,
        templateVersionNumber: selectedVersionNumber,
      })
      onClose()
      navigate(`/documents/${doc.id}/fill`)
    } catch (err) {
      showNotification(
        'error',
        extractServerMessage(err) ?? 'Не удалось создать документ',
      )
    } finally {
      setCreating(false)
    }
  }

  const folderOptions = [
    { value: '__none__', label: 'Без папки' },
    ...folders.map(f => ({ value: f.id, label: f.name })),
  ]

  const handleCancel = () => {
    onClose()
  }

  const selectedTemplate = templates.find(t => t.id === selectedTemplateId)

  return (
    <Dialog open={open} onOpenChange={(open) => !open && handleCancel()}>
      <DialogContent className="sm:max-w-[640px]" style={{ maxWidth: 640 }}>
        <DialogHeader>
          <DialogTitle>Создать документ</DialogTitle>
        </DialogHeader>

        <div className="mt-4 space-y-4">
          {/* Name */}
          <div className="space-y-2">
            <Label htmlFor="doc-name">
              Название документа <span className="text-destructive">*</span>
            </Label>
            <Input
              id="doc-name"
              value={name}
              onChange={(e) => {
                setName(e.target.value)
                if (nameError) setNameError('')
              }}
              placeholder="Договор с ООО «Ромашка»"
              className={nameError ? 'border-destructive' : ''}
            />
            {nameError && (
              <p className="text-xs text-destructive">{nameError}</p>
            )}
          </div>

          {/* Folder */}
          <div className="space-y-2">
            <Label>Папка</Label>
            <Select
              value={selectedFolderId ?? '__none__'}
              onValueChange={v => setSelectedFolderId(v === '__none__' ? null : v)}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Без папки" />
              </SelectTrigger>
              <SelectContent>
                {folderOptions.map(opt => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Template selector */}
          <div>
            <p className="text-sm font-medium mb-2">Выберите шаблон</p>

            {loadingTemplates ? (
              <div className="flex justify-center py-6">
                <Skeleton className="h-8 w-full" />
              </div>
            ) : templates.length === 0 ? (
              <p className="text-sm text-muted-foreground mt-3">
                Нет опубликованных шаблонов
              </p>
            ) : (
              <div
                className="grid grid-cols-2 gap-2.5 max-h-[280px] overflow-y-auto mt-3"
                role="radiogroup"
              >
                {templates.map(t => {
                  const isSelected = selectedTemplateId === t.id
                  return (
                    <button
                      key={t.id}
                      type="button"
                      role="radio"
                      aria-checked={isSelected}
                      onClick={() => handleTemplateSelect(t.id)}
                      className={[
                        'flex items-start gap-2.5 rounded-lg border p-3 text-left transition-colors',
                        isSelected
                          ? 'border-primary bg-primary/5 ring-1 ring-primary'
                          : 'border-border bg-card hover:border-muted-foreground/30',
                      ].join(' ')}
                    >
                      <FileText
                        className="size-5 mt-0.5 shrink-0"
                        style={{ color: isSelected ? 'var(--primary)' : 'var(--muted-foreground)' }}
                      />
                      <div className="min-w-0 flex-1">
                        <p
                          className="text-sm font-medium leading-tight break-words"
                          style={{ color: isSelected ? 'var(--primary)' : undefined }}
                        >
                          {t.name}
                        </p>
                        {t.description && (
                          <p className="text-xs text-muted-foreground mt-1 line-clamp-2">
                            {t.description}
                          </p>
                        )}
                        {t.category && (
                          <p className="text-[11px] text-muted-foreground/60 mt-0.5">
                            {t.category}
                          </p>
                        )}
                      </div>
                    </button>
                  )
                })}
              </div>
            )}
          </div>

          {/* Version selector — shown when a template is selected */}
          {selectedTemplate && (
            <div className="space-y-2">
              <Label>
                <History className="size-3.5 inline-block mr-1" />
                Версия шаблона
              </Label>
              {versionsError && (
                <p className="text-xs text-destructive">{versionsError}</p>
              )}
              {loadingVersions ? (
                <Skeleton className="h-9 w-full" />
              ) : versions.length === 0 ? (
                <div className="space-y-1">
                  {!versionsError && versionHint && (
                    <p className="text-xs text-muted-foreground">{versionHint}</p>
                  )}
                  {draftNotice && (
                    <p className="text-xs text-muted-foreground">{draftNotice}</p>
                  )}
                </div>
              ) : (
                <>
                  <Select
                    value={String(selectedVersionNumber ?? '')}
                    onValueChange={v => setSelectedVersionNumber(v ? Number(v) : null)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Последняя версия" />
                    </SelectTrigger>
                    <SelectContent>
                      {versions.map(v => (
                        <SelectItem key={v.versionNumber} value={String(v.versionNumber)}>
                          v{v.majorVersion}.{v.minorVersion}
                          {v.comment ? ` — ${v.comment}` : ''}
                          {v.bumpType === 'PUBLISH' || v.bumpType == null
                            ? ' (опубликована)'
                            : ''}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {draftNotice && (
                    <p className="text-xs text-muted-foreground">{draftNotice}</p>
                  )}
                </>
              )}
            </div>
          )}
        </div>

        <DialogFooter className="mt-6">
          <Button variant="outline" onClick={handleCancel}>
            Отмена
          </Button>
          <Button onClick={handleOk} disabled={creating}>
            {creating ? 'Создание...' : 'Создать'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
