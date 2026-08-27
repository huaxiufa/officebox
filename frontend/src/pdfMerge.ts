export type PdfFile = { file: File; id: string }

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export async function mergePdfs(files: PdfFile[], endpoint = 'http://localhost:8080/api/pdf/merge') {
  const form = new FormData()
  files.forEach(item => form.append('files', item.file))
  const response = await fetch(endpoint, { method: 'POST', body: form })
  if (!response.ok) throw new Error(await response.text() || 'PDF 合并失败')
  return response.blob()
}
