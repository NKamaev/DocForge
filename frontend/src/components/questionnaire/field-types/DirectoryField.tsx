import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Skeleton } from '@/components/ui/skeleton'
import { directoriesApi } from '../../../api/directoriesApi'
import type { Directory, DirectoryEntry } from '../../../types/directory'
import DirectoryEntryModal from './DirectoryEntryModal'

interface Props {
  directoryId: string
  directoryFields: string[]
  paramCode: string
  value: unknown
  /** Called for the main answer (entryId) AND for each sub-field value */
  onMultiChange: (changes: Record<string, unknown>) => void
}

export default function DirectoryField({
  directoryId, directoryFields, paramCode, value, onMultiChange,
}: Props) {
  const [directory, setDirectory] = useState<Directory | null>(null)
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editEntry, setEditEntry] = useState<DirectoryEntry | undefined>(undefined)

  const loadDirectory = () => {
    setLoading(true)
    directoriesApi.getById(directoryId)
      .then(setDirectory)
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadDirectory() }, [directoryId]) // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) return <Skeleton className="h-10 w-full" />
  if (!directory) return <span className="text-destructive">Справочник не найден</span>

  const firstFieldKey = directory.fields[0]?.key
  const options = directory.entries.map(entry => ({
    value: entry.id,
    label: firstFieldKey ? String(entry.values[firstFieldKey] ?? entry.id) : entry.id,
    entry,
  }))

  const selectedEntry = directory.entries.find(e => e.id === (value as string))

  const applyEntry = (entry: DirectoryEntry) => {
    const changes: Record<string, unknown> = { [paramCode]: entry.id }
    for (const fieldKey of directoryFields) {
      changes[`${paramCode}__${fieldKey}`] = entry.values[fieldKey] ?? ''
    }
    onMultiChange(changes)
  }

  const handleChange = (entryId: string) => {
    const entry = directory.entries.find(e => e.id === entryId)
    if (!entry) return
    applyEntry(entry)
  }

  const handleClear = () => {
    const changes: Record<string, unknown> = { [paramCode]: null }
    for (const fieldKey of directoryFields) {
      changes[`${paramCode}__${fieldKey}`] = ''
    }
    onMultiChange(changes)
  }

  const openCreate = () => {
    setEditEntry(undefined)
    setModalOpen(true)
  }

  const openEdit = () => {
    if (!selectedEntry) return
    setEditEntry(selectedEntry)
    setModalOpen(true)
  }

  const handleSaved = (saved: DirectoryEntry) => {
    loadDirectory()
    applyEntry(saved)
  }

  return (
    <TooltipProvider>
      <div className="flex gap-1 w-full">
        <div className="flex-1">
          <Select
            value={(value as string) || ''}
            onValueChange={(v) => v ? handleChange(v) : handleClear()}
          >
            <SelectTrigger>
              <SelectValue placeholder="Выберите запись из справочника" />
            </SelectTrigger>
            <SelectContent>
              {options.map(opt => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="secondary" size="icon" onClick={openCreate}>
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
            </Button>
          </TooltipTrigger>
          <TooltipContent>Создать новую запись</TooltipContent>
        </Tooltip>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button 
              variant="secondary" 
              size="icon" 
              onClick={openEdit}
              disabled={!selectedEntry}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            {selectedEntry ? 'Редактировать выбранную запись' : 'Выберите запись для редактирования'}
          </TooltipContent>
        </Tooltip>
      </div>

      {directory && (
        <DirectoryEntryModal
          open={modalOpen}
          directory={directory}
          entry={editEntry}
          onClose={() => setModalOpen(false)}
          onSaved={handleSaved}
        />
      )}
    </TooltipProvider>
  )
}
