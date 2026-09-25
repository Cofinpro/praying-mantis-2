/**
 * Hands a Blob to the browser as a download. The file came from `fetch` (so it went through the API
 * client's middleware), which a plain `<a href="/api/…">` couldn't do: an object URL points at the
 * bytes in memory, and a temporary `<a download>` makes the browser save them under our name.
 */
export function saveFile(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  // Firefox ignores a click on a link that isn't in the document
  document.body.append(link)
  link.click()
  link.remove()
  // The object URL keeps the Blob in memory until it's revoked. Revoke it on the next task, not
  // right away: some browsers (older Safari) start reading the URL only after click() returns.
  setTimeout(() => URL.revokeObjectURL(url), 0)
}
