import { test, expect } from '@playwright/test'

test.describe('App shell', () => {
  test('should redirect to login when not authenticated', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/.*login/)
  })

  test('login page should have title and form', async ({ page }) => {
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 2 })).toBeVisible()
    await expect(page.getByLabel('Логин')).toBeVisible()
    await expect(page.getByLabel('Пароль')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Войти' })).toBeVisible()
  })

  test('Tailwind CSS should be compiled — utility classes produce correct computed styles', async ({ page }) => {
    await page.goto('/login')
    const twWorks = await page.evaluate(() => {
      const el = document.createElement('div')
      el.className = 'fixed'
      document.body.appendChild(el)
      const pos = window.getComputedStyle(el).position
      document.body.removeChild(el)
      return pos === 'fixed'
    })
    expect(twWorks).toBe(true)
  })
})
