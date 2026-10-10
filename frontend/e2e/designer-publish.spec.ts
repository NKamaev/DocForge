import { test, expect } from '@playwright/test'
import { waitForStable, handleDialog, assertBodyUnlocked, assertDialogGone } from './helpers'

test.describe('Template designer publish/unpublish', () => {
  test.describe.configure({ mode: 'serial' })

  let templateId: string

  test.beforeEach(async ({ page }) => {
    handleDialog(page, 'accept', 1000)

    await page.goto('/login')
    await page.getByLabel('Логин').fill('admin')
    await page.getByLabel('Пароль').fill('admin123')
    await page.getByRole('button', { name: 'Войти' }).click()
    await expect(page).not.toHaveURL(/.*login/)
  })

  test('find first draft template and publish it', async ({ page }) => {
    await page.goto('/templates')
    await waitForStable(page)

    const draftRow = page.getByRole('row').filter({ has: page.locator('span.tag.amber') }).first()
    await expect(draftRow).toBeVisible({ timeout: 10000 })

    const editBtn = draftRow.locator('td:last-child .row-actions button.btn.icon-only').first()
    await expect(editBtn).toBeAttached({ timeout: 5000 })
    await editBtn.click()
    await waitForStable(page, 1000)

    templateId = await page.evaluate(() => {
      const match = window.location.pathname.match(/\/templates\/([^/]+)\/edit/)
      return match ? match[1] : ''
    })

    const publishBtn = page.locator('button[title="Опубликовать шаблон"]')
    await expect(publishBtn).toBeVisible({ timeout: 10000 })
    await expect(publishBtn).toBeAttached()

    await publishBtn.click()
    await waitForStable(page, 300)

    await expect(page.getByText('Опубликовать шаблон?')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Опубликовать' })).toBeVisible()

    await page.getByRole('button', { name: 'Опубликовать' }).click()
    await expect(page.getByText('Шаблон опубликован.')).toBeVisible({ timeout: 10000 })
    await waitForStable(page, 500)

    await assertDialogGone(page, 'Опубликовать шаблон?')
    await assertBodyUnlocked(page)
  })

  test('unpublish the template from designer page', async ({ page }) => {
    if (templateId) {
      await page.goto(`/templates/${templateId}/edit`)
    } else {
      await page.goto('/templates')
      await waitForStable(page)
      const draftRow = page.getByRole('row').filter({ has: page.locator('span.tag.amber') }).first()
      await expect(draftRow).toBeVisible({ timeout: 10000 })
      const editBtn = draftRow.locator('td:last-child .row-actions button.btn.icon-only').first()
      await expect(editBtn).toBeAttached({ timeout: 5000 })
      await editBtn.click()
    }
    await waitForStable(page, 1000)

    const unpublishBtn = page.locator('button[title="Снять с публикации"]')
    await expect(unpublishBtn).toBeVisible({ timeout: 10000 })
    await expect(unpublishBtn).toBeAttached()

    await unpublishBtn.click()
    await waitForStable(page, 300)

    await expect(page.getByText('Снять с публикации?')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Да, снять' })).toBeVisible()

    await page.getByRole('button', { name: 'Да, снять' }).click()
    await expect(page.getByTitle('Опубликовать шаблон')).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('Шаблон опубликован.')).not.toBeVisible()
    await waitForStable(page, 500)

    await assertDialogGone(page, 'Снять с публикации?')
    await assertBodyUnlocked(page)
  })
})
