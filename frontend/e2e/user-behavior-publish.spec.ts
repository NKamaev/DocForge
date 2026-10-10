import { test, expect } from '@playwright/test'
import { simulateUserDelay, handleDialog, assertBodyUnlocked, assertDialogGone } from './helpers'

test.describe('User behavior: publish/unpublish (headed mode)', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async ({ page }) => {
    handleDialog(page, 'accept', 1200)

    await page.goto('/login')
    await simulateUserDelay(400)
    await page.getByLabel('Логин').fill('admin')
    await simulateUserDelay(300)
    await page.getByLabel('Пароль').fill('admin123')
    await simulateUserDelay(200)
    await page.getByRole('button', { name: 'Войти' }).click()
    await expect(page).not.toHaveURL(/.*login/)
  })

  test('user publishes a draft template from list page', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await simulateUserDelay(600)

    const draftRow = page.getByRole('row').filter({ has: page.locator('span.tag.amber') }).first()
    await expect(draftRow).toBeVisible({ timeout: 10000 })
    const draftName = (await draftRow.locator('td').first().innerText()).split('\n')[0].trim()

    const publishBtn = draftRow.locator('td:last-child button.btn.icon-only.success')
    await expect(publishBtn).toBeVisible({ timeout: 5000 })

    const box = await publishBtn.boundingBox()
    if (box) {
      const cx = box.x + box.width / 2
      const cy = box.y + box.height / 2

      await page.mouse.move(0, 0)
      await simulateUserDelay(200)

      const steps = 8
      for (let i = 1; i <= steps; i++) {
        await page.mouse.move(
          Math.round((cx * i) / steps),
          Math.round((cy * i) / steps),
        )
        await simulateUserDelay(60)
      }
      await simulateUserDelay(400)
    }

    await publishBtn.hover()
    await simulateUserDelay(500)
    await publishBtn.click()
    await simulateUserDelay(600)

    await expect(page.getByText('Опубликовать шаблон?')).toBeVisible()

    const confirmBtn = page.getByRole('button', { name: 'Опубликовать' })
    await confirmBtn.hover()
    await simulateUserDelay(800)
    await confirmBtn.click()

    const updatedPublishRow = page.getByRole('row').filter({ has: page.getByText(draftName) }).first()
    await expect(updatedPublishRow.getByText('Опубликован')).toBeVisible({ timeout: 10000 })
    await simulateUserDelay(800)

    await assertDialogGone(page, 'Опубликовать шаблон?')
    await assertBodyUnlocked(page)
  })

  test('user unpublishes a published template', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await simulateUserDelay(600)

    const publishedRow = page.getByRole('row').filter({ has: page.locator('span.tag.green') }).first()
    await expect(publishedRow).toBeVisible({ timeout: 10000 })
    const publishedName = (await publishedRow.locator('td').first().innerText()).split('\n')[0].trim()

    const unpublishBtn = publishedRow.locator('td:last-child button.btn.icon-only.warning')
    await expect(unpublishBtn).toBeVisible({ timeout: 5000 })

    const box = await unpublishBtn.boundingBox()
    if (box) {
      const cx = box.x + box.width / 2
      const cy = box.y + box.height / 2
      await page.mouse.move(0, 0)
      await simulateUserDelay(200)
      const steps = 8
      for (let i = 1; i <= steps; i++) {
        await page.mouse.move(
          Math.round((cx * i) / steps),
          Math.round((cy * i) / steps),
        )
        await simulateUserDelay(60)
      }
      await simulateUserDelay(500)
    }

    await unpublishBtn.hover()
    await simulateUserDelay(400)
    await unpublishBtn.click()
    await simulateUserDelay(700)

    await expect(page.getByText('Снять с публикации?')).toBeVisible()

    const confirmBtn = page.getByRole('button', { name: 'Да, снять' })
    await expect(confirmBtn).toBeVisible()
    await confirmBtn.hover()
    await simulateUserDelay(600)
    await confirmBtn.click()

    const updatedUnpublishRow = page.getByRole('row').filter({ has: page.getByText(publishedName) }).first()
    await expect(updatedUnpublishRow.getByText('Черновик')).toBeVisible({ timeout: 10000 })
    await simulateUserDelay(800)

    await assertDialogGone(page, 'Снять с публикации?')
    await assertBodyUnlocked(page)
  })

  test('user opens delete dialog then cancels', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await simulateUserDelay(500)

    const deleteRow = page.getByRole('row').filter({ has: page.locator('span.tag.amber') }).first()
    await expect(deleteRow).toBeVisible({ timeout: 10000 })
    const deleteName = (await deleteRow.locator('td').first().innerText()).split('\n')[0].trim()

    const deleteBtn = deleteRow.locator('td:last-child button.btn.icon-only.danger')
    await expect(deleteBtn).toBeVisible({ timeout: 5000 })

    const box = await deleteBtn.boundingBox()
    if (box) {
      const cx = box.x + box.width / 2
      const cy = box.y + box.height / 2
      await page.mouse.move(0, 0)
      await simulateUserDelay(200)
      const steps = 6
      for (let i = 1; i <= steps; i++) {
        await page.mouse.move(
          Math.round((cx * i) / steps),
          Math.round((cy * i) / steps),
        )
        await simulateUserDelay(80)
      }
      await simulateUserDelay(400)
    }

    await deleteBtn.hover()
    await simulateUserDelay(300)
    await deleteBtn.click()
    await simulateUserDelay(500)

    await expect(page.getByText('Удалить шаблон?')).toBeVisible()

    const cancelBtn = page.getByRole('button', { name: 'Отмена' })
    await expect(cancelBtn).toBeVisible()
    await cancelBtn.hover()
    await simulateUserDelay(500)
    await cancelBtn.click()
    await simulateUserDelay(500)

    await assertDialogGone(page, 'Удалить шаблон?')
    await assertBodyUnlocked(page)
  })
})
