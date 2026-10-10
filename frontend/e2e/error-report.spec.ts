import { test, expect } from '@playwright/test'
import { simulateUserDelay, waitForStable, handleDialog, assertBodyUnlocked, assertDialogGone } from './helpers'

test.describe('Error report button', () => {
  test.beforeEach(async ({ page }) => {
    handleDialog(page, 'accept', 1000)

    await page.goto('/login')
    await page.getByLabel('Логин').fill('admin')
    await page.getByLabel('Пароль').fill('admin123')
    await page.getByRole('button', { name: 'Войти' }).click()
    await expect(page).not.toHaveURL(/.*login/)
  })

  test('should show bug report button in topbar', async ({ page }) => {
    await waitForStable(page)
    const bugBtn = page.getByTitle('Сообщить об ошибке')
    await expect(bugBtn).toBeVisible()
    await expect(bugBtn).toBeAttached()
  })

  test('should open modal on click', async ({ page }) => {
    await waitForStable(page)
    await page.getByTitle('Сообщить об ошибке').click()
    await simulateUserDelay(300)

    await expect(page.getByText('Сообщить об ошибке')).toBeVisible()
    await expect(page.getByPlaceholder('Опишите, что пошло не так…')).toBeVisible()
  })

  test('should show thanks after submit', async ({ page }) => {
    await waitForStable(page, 300)

    await page.getByTitle('Сообщить об ошибке').click()
    await simulateUserDelay(400)

    await page.getByPlaceholder('Опишите, что пошло не так…').fill('Тестовая ошибка: кнопка не работает')
    await simulateUserDelay(200)

    await page.getByRole('button', { name: 'Выбрать' }).click()
    await simulateUserDelay(300)
    await page.locator('h1, h2, h3').first().click()
    await simulateUserDelay(300)
    await page.getByRole('button', { name: 'Готово' }).click()
    await simulateUserDelay(300)

    await page.getByRole('button', { name: 'Отправить' }).click()
    await expect(page.getByText('Спасибо! Ваш отчёт отправлен.')).toBeVisible({ timeout: 5000 })
    await simulateUserDelay(500)
    await assertBodyUnlocked(page)
  })

  test('picker shows floating bar with instructions', async ({ page }) => {
    await waitForStable(page)

    await page.getByTitle('Сообщить об ошибке').click()
    await simulateUserDelay(300)
    await page.getByRole('button', { name: 'Выбрать' }).click()

    await expect(page.getByText('Кликайте по элементам, чтобы добавить их.')).toBeVisible()
  })
})
