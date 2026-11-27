import type { DownloadedFile } from '../api/client'

/** Saves a downloaded file through a temporary link, under the server's file name. */
export function saveFile(file: DownloadedFile): void {
  const url = URL.createObjectURL(file.blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
