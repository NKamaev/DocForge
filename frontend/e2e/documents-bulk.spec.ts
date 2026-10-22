/**
 * E2E coverage for BUG-049 — bulk actions on the documents list page.
 *
 * The regression: `DocumentsPage` rendered the bulk «Скачать» / «Удалить»
 * buttons with no `onClick`, so the whole bulk bar was decorative.
 *
 * What this spec pins down (user-visible behaviour only):
 *  1. Selecting all documents reveals the bulk bar with the selection count
 *     and both actions enabled.
 *  2. «Удалить» opens a confirmation AlertDialog (nothing is deleted yet) and
 *     cancelling it performs no deletion at all — no DELETE request is sent,
 *     the document count is unchanged and the selection is preserved.
 *  3. «Скачать» produces a real `.docx` browser download.
 *
 * Documents are seeded through the public API when the environment has fewer
 * than two of them at the root, so the spec is self-sufficient but never
 * destructive (the delete path is only ever cancelled).
 */
import { stat } from 'node:fs/promises'
import { test, expect, type Page } from '@playwright/test'
import { assertBodyUnlocked, assertDialogGone, login, waitForStable } from './helpers'

// ── API helpers ───────────────────────────────────────────────────────────────

const DOCUMENTS_API = '/api/documents'
const DOCUMENTS_ENDPOINT = /\/api\/documents\/[^/?]+$/

/** Documents created to seed the environment; deleted again in `afterAll`. */
const createdDocIds: string[] = []
/** Token captured after login so `afterAll` can authenticate its cleanup. */
let authToken: string | undefined

interface DocumentRow {
  id: string
  name: string
  folderId: string | null
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  // Token lives in localStorage and is attached by the axios instance.
  const token = await page.evaluate(() => localStorage.getItem('token'))
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function fetchDocuments(page: Page): Promise<DocumentRow[]> {
  const res = await page.request.get(`${DOCUMENTS_API}?page=0&size=200`, {
    headers: await authHeaders(page),
  })
  expect(res.ok(), `GET ${DOCUMENTS_API} → ${res.status()}`).toBeTruthy()
  const body = (await res.json()) as { content?: DocumentRow[] }
  return body.content ?? []
}

/**
 * Make sure at least `minimum` documents live in the root folder (bulk
 * selection is scoped to the visible folder). Documents are created through
 * the real API so they behave exactly like user-created ones.
 */
async function ensureRootDocuments(page: Page, minimum = 2): Promise<void> {
  const rootDocs = (await fetchDocuments(page)).filter(d => !d.folderId)
  if (rootDocs.length >= minimum) return

  const headers = await authHeaders(page)
  const tplRes = await page.request.get('/api/templates?page=0&size=100', { headers })
  const templates = tplRes.ok()
    ? ((await tplRes.json()) as { content?: Array<{ id: string; name: string; published?: boolean }> }).content ?? []
    : []
  const template = templates.find(t => t.published) ?? templates[0]

  test.skip(!template, 'No template available to seed documents — seed the environment first')

  const stamp = Date.now()
  for (let i = rootDocs.length; i < minimum; i++) {
    const res = await page.request.post(DOCUMENTS_API, {
      headers: { ...headers, 'Content-Type': 'application/json' },
      data: { templateId: template!.id, name: `E2E bulk ${stamp}-${i + 1}` },
    })
    expect(res.ok(), `POST ${DOCUMENTS_API} → ${res.status()}`).toBeTruthy()
    const created = (await res.json()) as { id?: string }
    if (created.id) createdDocIds.push(created.id)
  }
}

// ── Locators ──────────────────────────────────────────────────────────────────

/** Document rows only — folder rows carry "Папка" instead of a status tag, and
 *  loading/empty placeholder rows have no tag at all. */
function documentRows(page: Page) {
  return page.locator('.tbl table tbody tr').filter({ has: page.locator('span.tag') })
}

function bulkBar(page: Page) {
  return page.locator('.bulk-bar')
}

function selectAllCheckbox(page: Page) {
  return page.getByRole('checkbox', { name: 'Выбрать все документы' })
}

/** Select every visible document and wait until the bulk bar reports it. */
async function selectAllDocuments(page: Page): Promise<number> {
  const rootDocs = (await fetchDocuments(page)).filter(d => !d.folderId)
  await expect(documentRows(page).first()).toBeVisible({ timeout: 10_000 })
  await selectAllCheckbox(page).check()

  await expect(bulkBar(page)).toBeVisible()
  await expect(bulkBar(page)).toContainText(`Выбрано: ${rootDocs.length}`)
  return rootDocs.length
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('Documents bulk actions (BUG-049)', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async ({ page }) => {
    await login(page)
    authToken = await page.evaluate(() => localStorage.getItem('token') ?? undefined)
    await page.goto('/documents')
    await waitForStable(page)

    // BUG-049 regression: the bulk bar only exists once something is selected.
    await ensureRootDocuments(page, 2)
    await page.reload()
    await waitForStable(page)

    await expect(selectAllCheckbox(page)).toBeVisible({ timeout: 10_000 })
  })

  test.afterAll(async ({ request }) => {
    // Keep the environment clean: remove only the documents this spec created.
    if (createdDocIds.length === 0) return
    const headers = authToken ? { Authorization: `Bearer ${authToken}` } : {}
    await Promise.allSettled(
      createdDocIds.map(id => request.delete(`${DOCUMENTS_API}/${id}`, { headers })),
    )
    createdDocIds.length = 0
  })

  test('selecting all documents shows the bulk bar with both actions enabled', async ({ page }) => {
    const rootDocs = (await fetchDocuments(page)).filter(d => !d.folderId)
    expect(rootDocs.length).toBeGreaterThan(0)

    await selectAllCheckbox(page).check()

    const bar = bulkBar(page)
    await expect(bar).toBeVisible()
    await expect(bar).toContainText(`Выбрано: ${rootDocs.length}`)

    // Every visible document row is ticked (folders are not part of this check).
    for (let i = 0; i < rootDocs.length; i++) {
      await expect(documentRows(page).nth(i).locator('td:first-child input[type="checkbox"]')).toBeChecked()
    }

    await expect(bar.getByRole('button', { name: /Скачать/ })).toBeEnabled()
    await expect(bar.getByRole('button', { name: /Удалить/ })).toBeEnabled()

    // Deselecting hides the bar again.
    await bar.getByRole('button', { name: 'Снять выбор' }).click()
    await expect(bar).toBeHidden()
  })

  test('«Удалить» opens the confirmation alertdialog and cancelling deletes nothing', async ({ page }) => {
    const documentsBefore = (await fetchDocuments(page)).length
    const selectedCount = await selectAllDocuments(page)
    const rowsBefore = await documentRows(page).count()

    // Any DELETE aimed at a document would show up here.
    const deleteRequests: string[] = []
    page.on('request', request => {
      if (request.method() !== 'DELETE') return
      if (DOCUMENTS_ENDPOINT.test(new URL(request.url()).pathname)) {
        deleteRequests.push(request.url())
      }
    })

    await bulkBar(page).getByRole('button', { name: /Удалить/ }).click()

    const dialog = page.getByRole('alertdialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText(
      selectedCount === 1
        ? 'Удалить выбранный документ?'
        : `Удалить выбранные документы (${selectedCount})?`,
    )
    await expect(dialog).toContainText('Это действие нельзя отменить.')

    // Per the ticket the action must be confirmed first — nothing happens yet.
    expect(deleteRequests, 'opening the dialog must not delete anything').toEqual([])

    await dialog.getByRole('button', { name: 'Отмена' }).click()

    // Radix must not leave the page in a locked state (pointer-events bug).
    await expect(dialog).toBeHidden()
    await assertDialogGone(page, 'Это действие нельзя отменить.')
    await assertBodyUnlocked(page)

    // Cancelling is a true no-op: no DELETE fired, no document disappeared and
    // the selection (hence the bulk bar) is preserved.
    await page.waitForTimeout(800) // give a stray async delete a chance to surface
    expect(deleteRequests, 'cancelling must not issue DELETE requests').toEqual([])
    expect((await fetchDocuments(page)).length).toBe(documentsBefore)
    await expect(documentRows(page)).toHaveCount(rowsBefore)
    await expect(bulkBar(page)).toBeVisible()
    await expect(bulkBar(page)).toContainText(`Выбрано: ${selectedCount}`)
  })

  test('«Скачать» triggers a .docx download for the selected documents', async ({ page }) => {
    await selectAllDocuments(page)

    const downloadPromise = page.waitForEvent('download', { timeout: 60_000 })
    await bulkBar(page).getByRole('button', { name: /Скачать/ }).click()

    const download = await downloadPromise
    const filename = download.suggestedFilename()

    expect(filename, 'bulk download must produce a DOCX file').toMatch(/\.docx$/i)
    expect(filename, 'download filename must be sanitized').not.toMatch(/[/\\:*?"<>|]/)

    // The download stream itself must complete without a browser-side failure.
    expect(await download.failure()).toBeNull()
    const filePath = await download.path()
    if (filePath) {
      const { size } = await stat(filePath)
      expect(size, 'downloaded DOCX must not be empty').toBeGreaterThan(0)
    }

    // The bar returns to its idle label once the loop finished.
    await expect(bulkBar(page).getByRole('button', { name: /^Скачать$/ })).toBeEnabled({ timeout: 60_000 })
  })
})
