import api from './axiosInstance'

export interface ImageUploadResponse {
  id: string
  url: string
}

export const uploadsApi = {
  uploadImage: (file: File): Promise<ImageUploadResponse> => {
    const form = new FormData()
    form.append('file', file)
    return api
      .post<ImageUploadResponse>('/uploads/images', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data)
  },
}
