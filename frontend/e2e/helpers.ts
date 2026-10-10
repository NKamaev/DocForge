import { Page, expect } from '@playwright/test'

export function simulateUserDelay(ms = 500): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

export async function waitForStable(page: Page, ms = 500): Promise<void> {
  await page.waitForLoadState('networkidle')
  await simulateUserDelay(ms)
}

export type DialogAction = 'accept' | 'dismiss'

export function handleDialog(page: Page, action: DialogAction = 'accept', delayMs = 1000): void {
  page.on('dialog', async dialog => {
    await simulateUserDelay(delayMs)
    if (action === 'accept') {
      await dialog.accept()
    } else {
      await dialog.dismiss()
    }
  })
}

export type BodyState = {
  pointerEvents: string
  scrollLocked: string | null
  overflow: string
  position: string
}

export async function getBodyState(page: Page): Promise<BodyState> {
  return page.evaluate(() => ({
    pointerEvents: document.body.style.pointerEvents,
    scrollLocked: document.body.dataset.scrollLocked ?? null,
    overflow: document.body.style.overflow,
    position: document.body.style.position,
  }))
}

export async function assertBodyUnlocked(page: Page): Promise<void> {
  const state = await getBodyState(page)
  expect(state.pointerEvents, 'body pointerEvents should not be "none"').not.toBe('none')
  expect(state.scrollLocked, 'body should not have scrollLocked dataset').toBeNull()
}

export async function assertDialogGone(page: Page, text: string): Promise<void> {
  await expect(page.getByText(text)).not.toBeVisible({ timeout: 3000 })
  const count = await page.getByText(text).count()
  expect(count, `dialog text "${text}" should be absent from DOM`).toBe(0)
}

export async function login(page: Page): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Логин').fill('admin')
  await page.getByLabel('Пароль').fill('admin123')
  await page.getByRole('button', { name: 'Войти' }).click()
  await expect(page).not.toHaveURL(/.*login/)
}

export async function findDraftTemplate(page: Page): Promise<{ name: string; id?: string } | null> {
  const rows = page.getByRole('row')
  const count = await rows.count()
  for (let i = 0; i < count; i++) {
    const row = rows.nth(i)
    const badge = row.locator('td').nth(2)
    const status = await badge.innerText()
    if (status.trim() === 'Черновик') {
      const name = (await row.locator('td').first().innerText()).trim()
      return { name }
    }
  }
  return null
}

export async function findPublishedTemplate(page: Page): Promise<{ name: string; id?: string } | null> {
  const rows = page.getByRole('row')
  const count = await rows.count()
  for (let i = 0; i < count; i++) {
    const row = rows.nth(i)
    const badge = row.locator('td').nth(2)
    const status = await badge.innerText()
    if (status.trim() === 'Опубликован') {
      const name = (await row.locator('td').first().innerText()).trim()
      return { name }
    }
  }
  return null
}
