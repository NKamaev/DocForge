export type DirectoryFieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'FIO' | 'BOOLEAN' | 'SELECT'

export interface DirectoryField {
  key: string
  label: string
  type: DirectoryFieldType
  options?: string[]
  order: number
}

export interface DirectoryEntry {
  id: string
  values: Record<string, unknown>
}

export interface DirectoryListItem {
  id: string
  name: string
  fields: DirectoryField[]
  entryCount: number
  createdAt: string
  updatedAt: string
}

export interface Directory {
  id: string
  name: string
  fields: DirectoryField[]
  entries: DirectoryEntry[]
  createdAt: string
  updatedAt: string
}

export interface CreateDirectoryRequest {
  name: string
  fields: DirectoryField[]
}

export interface UpdateDirectoryRequest {
  name: string
  fields: DirectoryField[]
}

export interface DirectoryEntryRequest {
  values: Record<string, unknown>
}
