import { test, expect } from '@playwright/test'
import { waitForStable, handleDialog, assertBodyUnlocked } from './helpers'

test.describe('Template publish/unpublish', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async ({ page }) => {
    handleDialog(page, 'accept', 1000)

    await page.goto('/login')
    await page.getByLabel('Логин').fill('admin')
    await page.getByLabel('Пароль').fill('admin123')
    await page.getByRole('button', { name: 'Войти' }).click()
    await expect(page).not.toHaveURL(/.*login/)
  })

  test('publish a draft template from list page', async ({ page }) => {
    await page.goto('/templates')
    await waitForStable(page)

    const draftRow = page.getByRole('row').filter({ has: page.locator('span.tag.amber') }).first()
    await expect(draftRow).toBeVisible({ timeout: 10000 })

    const templateName = (await draftRow.locator('td').first().innerText()).split('\n')[0].trim()

    const publishBtn = draftRow.locator('td:last-child button.btn.icon-only.success')
    await expect(publishBtn).toBeAttached({ timeout: 5000 })
    await expect(publishBtn).toBeVisible()

    await publishBtn.click()
    await waitForStable(page, 300)

    await expect(page.getByText('Опубликовать шаблон?')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Опубликовать' })).toBeVisible()

    await page.getByRole('button', { name: 'Опубликовать' }).click()

    const updatedRow = page.getByRole('row').filter({ has: page.getByText(templateName) }).first()
    await expect(updatedRow.getByText('Опубликован')).toBeVisible({ timeout: 5000 })
    await waitForStable(page, 500)

    await assertBodyUnlocked(page)
  })

  test('unpublish a published template', async ({ page }) => {
    await page.goto('/templates')
    await waitForStable(page)

    const publishedRow = page.getByRole('row').filter({ has: page.locator('span.tag.green') }).first()
    await expect(publishedRow).toBeVisible({ timeout: 10000 })

    const templateName = (await publishedRow.locator('td').first().innerText()).split('\n')[0].trim()

    const unpublishBtn = publishedRow.locator('td:last-child button.btn.icon-only.warning')
    await expect(unpublishBtn).toBeAttached({ timeout: 5000 })
    await expect(unpublishBtn).toBeVisible()
    await unpublishBtn.click()
    await waitForStable(page, 300)

    await expect(page.getByText('Снять с публикации?')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Да, снять' })).toBeVisible()

    await page.getByRole('button', { name: 'Да, снять' }).click()

    const updatedRow = page.getByRole('row').filter({ has: page.getByText(templateName) }).first()
    await expect(updatedRow.getByText('Черновик')).toBeVisible({ timeout: 5000 })
    await waitForStable(page, 500)

    await assertBodyUnlocked(page)
  })
})
