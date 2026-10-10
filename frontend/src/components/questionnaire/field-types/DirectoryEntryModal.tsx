import { useEffect, useState } from 'react'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { DatePicker } from '@/components/ui/date-picker'
import { directoriesApi } from '../../../api/directoriesApi'
import type { Directory, DirectoryEntry, DirectoryEntryRequest } from '../../../types/directory'

function showNotification(type: 'success' | 'error', message: string) {
  console.log(`[${type.toUpperCase()}]`, message)
}

interface Props {
  open: boolean
  directory: Directory
  entry?: DirectoryEntry
  onClose: () => void
  onSaved: (entry: DirectoryEntry) => void
}

interface FormValues {
  [key: string]: unknown
}

export default function DirectoryEntryModal({ open, directory, entry, onClose, onSaved }: Props) {
  const [formValues, setFormValues] = useState<FormValues>({})
  const isEdit = Boolean(entry)

  useEffect(() => {
    if (open) {
      if (entry) {
        const vals: FormValues = {}
        for (const field of directory.fields) {
          const v = entry.values[field.key]
          vals[field.key] = v ?? ''
        }
        setFormValues(vals)
      } else {
        const vals: FormValues = {}
        for (const field of directory.fields) {
          vals[field.key] = ''
        }
        setFormValues(vals)
      }
    }
  }, [open, entry, directory])

  const handleFieldChange = (key: string, value: unknown) => {
    setFormValues(prev => ({ ...prev, [key]: value }))
  }

  const handleSave = async () => {
    try {
      const values: Record<string, unknown> = {}
      for (const field of directory.fields) {
        const v = formValues[field.key]
        values[field.key] = v ?? ''
      }
      const payload: DirectoryEntryRequest = { values }
      let updated: { entries: DirectoryEntry[] }
      if (isEdit && entry) {
        updated = await directoriesApi.updateEntry(directory.id, entry.id, payload)
        const saved = updated.entries.find(e => e.id === entry.id)!
        showNotification('success', 'Запись обновлена')
        onSaved(saved)
      } else {
        updated = await directoriesApi.addEntry(directory.id, payload)
        const saved = updated.entries[updated.entries.length - 1]
        showNotification('success', 'Запись создана')
        onSaved(saved)
      }
      onClose()
    } catch {
      // validation error - stay open
    }
  }

  const renderField = (field: { key: string; label?: string; type?: string; options?: string[] }) => {
    const value = formValues[field.key]

    if (field.type === 'BOOLEAN') {
      return (
        <div className="flex items-center gap-2">
          <Switch
            checked={Boolean(value)}
            onCheckedChange={(checked) => handleFieldChange(field.key, checked)}
          />
          <Label className="text-sm">{field.label || field.key}</Label>
        </div>
      )
    }

    if (field.type === 'NUMBER') {
      return (
        <Input
          type="number"
          value={String(value ?? '')}
          onChange={(e) => handleFieldChange(field.key, e.target.value ? Number(e.target.value) : '')}
        />
      )
    }

    if (field.type === 'DATE') {
      return (
        <DatePicker
          value={typeof value === 'string' ? value : undefined}
          onChange={(date) => handleFieldChange(field.key, date)}
        />
      )
    }

    if (field.type === 'SELECT' && field.options?.length) {
      return (
        <Select
          value={String(value ?? '')}
          onValueChange={(val) => handleFieldChange(field.key, val)}
        >
          <SelectTrigger>
            <SelectValue placeholder="Выберите значение" />
          </SelectTrigger>
          <SelectContent>
            {field.options.map(opt => (
              <SelectItem key={opt} value={opt}>{opt}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      )
    }

    return (
      <Input
        value={String(value ?? '')}
        onChange={(e) => handleFieldChange(field.key, e.target.value)}
      />
    )
  }

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle>
            {isEdit ? 'Редактировать запись' : 'Создать запись'}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4 mt-2">
          {directory.fields.map(field => (
            <div key={field.key} className="space-y-2">
              <Label htmlFor={`field-${field.key}`}>
                {field.label || field.key}
              </Label>
              {renderField(field)}
            </div>
          ))}
        </div>

        <DialogFooter className="mt-6">
          <Button variant="outline" onClick={onClose}>
            Отмена
          </Button>
          <Button onClick={handleSave}>
            {isEdit ? 'Сохранить' : 'Создать'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
