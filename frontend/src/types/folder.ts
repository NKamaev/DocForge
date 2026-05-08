export interface Folder {
  id: string
  name: string
  color: string | null
  documentCount: number
  parentId: string | null
}

export interface FolderRequest {
  name: string
  color: string | null
  parentId?: string | null
}
