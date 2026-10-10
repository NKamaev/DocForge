import { test, expect } from '@playwright/test'
import path from 'path'

const SCREENSHOT_DIR = path.resolve(process.cwd(), 'docs/screenshots')

test.describe('AlertDialog screenshot', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Логин').fill('admin')
    await page.getByLabel('Пароль').fill('admin123')
    await page.getByRole('button', { name: 'Войти' }).click()
    await expect(page).not.toHaveURL(/.*login/)
  })

  test('publish dialog screenshot', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(500)

    // Use "Test Fresh Template" — it's a draft (success button)
    const row = page.getByRole('row').filter({ has: page.getByText('Test Fresh Template', { exact: true }) })
    await expect(row).toBeVisible({ timeout: 10000 })

    const publishBtn = row.locator('td:last-child button.btn.icon-only.success')
    await expect(publishBtn).toBeVisible()
    await publishBtn.click()

    await expect(page.getByText('Опубликовать шаблон?')).toBeVisible()
    await page.waitForTimeout(300)

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'dialog-publish.png'),
      fullPage: false,
    })

    // Confirm and close dialog
    await page.getByRole('button', { name: 'Опубликовать' }).click()
    await expect(row.getByText('Опубликован')).toBeVisible({ timeout: 5000 })
  })

  test('unpublish dialog screenshot', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(500)

    // "Test Fresh Template" was published in previous test
    const row = page.getByRole('row').filter({ has: page.getByText('Test Fresh Template', { exact: true }) })
    await expect(row).toBeVisible({ timeout: 10000 })
    await expect(row.getByText('Опубликован')).toBeVisible()

    const unpublishBtn = row.locator('td:last-child button.btn.icon-only.warning')
    await expect(unpublishBtn).toBeVisible()
    await unpublishBtn.click()

    await expect(page.getByText('Снять с публикации?')).toBeVisible()
    await page.waitForTimeout(300)

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'dialog-unpublish.png'),
      fullPage: false,
    })

    // Confirm and close dialog
    await page.getByRole('button', { name: 'Да, снять' }).click()
    await expect(row.getByText('Черновик')).toBeVisible({ timeout: 5000 })
  })

  test('delete dialog screenshot', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(500)

    // Restore "Test Fresh Template" to draft — now use a different template
    // "Designer Publish Test" is a safe draft to use
    const row = page.getByRole('row').filter({ has: page.getByText('BUG-029 Designer Test', { exact: true }) })
    await expect(row).toBeVisible({ timeout: 10000 })

    const deleteBtn = row.locator('td:last-child button.btn.icon-only.danger')
    await expect(deleteBtn).toBeVisible()
    await deleteBtn.click()

    await expect(page.getByText('Удалить шаблон?')).toBeVisible()
    await page.waitForTimeout(300)

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'dialog-delete.png'),
      fullPage: false,
    })

    // Cancel — don't actually delete
    await page.getByRole('button', { name: 'Отмена' }).click()
    await page.waitForTimeout(500)
    // Verify dialog is closed
    await expect(page.getByText('Удалить шаблон?')).not.toBeVisible()
  })
})
