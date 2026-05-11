import { test, expect } from '@playwright/test'
import path from 'path'

const SCREENSHOT_DIR = path.resolve(process.cwd(), 'docs/screenshots')

test.describe('BUG-029: Toast notification visibility', () => {
  test.describe.configure({ mode: 'serial' })

  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Логин').fill('admin')
    await page.getByLabel('Пароль').fill('admin123')
    await page.getByRole('button', { name: 'Войти' }).click()
    await expect(page).not.toHaveURL(/.*login/)
  })

  test('toast debug — publish template and check toast visibility', async ({ page }) => {
    // Navigate to templates page
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(500)

    // Clear any initial toast state
    await page.evaluate(() => { document.title = 'initial' })

    // Find the "E2E Publish Test" draft template
    const row = page.getByRole('row').filter({ has: page.getByText('E2E Publish Test', { exact: true }) })
    await expect(row).toBeVisible({ timeout: 10000 })

    // Screenshot before publish
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'toast-debug-before-publish.png'),
      fullPage: true,
    })

    // Click publish button (green check icon, class "success")
    const publishBtn = row.locator('td:last-child button.btn.icon-only.success')
    await expect(publishBtn).toBeVisible()
    await publishBtn.click()

    // Confirm in dialog
    await expect(page.getByText('Опубликовать шаблон?')).toBeVisible()
    await page.getByRole('button', { name: 'Опубликовать' }).click()

    // Wait for the API call and state update
    await expect(row.getByText('Опубликован')).toBeVisible({ timeout: 10000 })
    // Extra wait for toast to appear
    await page.waitForTimeout(1000)

    // ===== DIAGNOSTICS =====
    const diagnostics = await page.evaluate(() => {
      // Check if the toast container is in the DOM
      const toastContainer = document.querySelector('[data-testid="toast-container"]')
      
      // Count all alert-dialog instances (should be 0 after dialog closes)
      const dialogCount = document.querySelectorAll('[data-slot="alert-dialog"]').length
      
      // Check body for scroll locking
      const pointerEvents = document.body.style.pointerEvents
      const scrollLocked = document.body.dataset.scrollLocked

      // Check for any toast-like elements
      const allFixedElements = Array.from(document.querySelectorAll('*'))
        .filter(el => {
          const style = window.getComputedStyle(el)
          return style.position === 'fixed' && style.zIndex === '9999'
        })
        .map(el => ({
          tag: el.tagName,
          classes: el.className,
          text: el.textContent?.slice(0, 100),
          rect: el.getBoundingClientRect(),
        }))

      return {
        pageTitle: document.title,
        toastContainerExists: !!toastContainer,
        toastContainerHTML: toastContainer?.outerHTML ?? null,
        dialogCount,
        pointerEvents,
        scrollLocked,
        fixedElementsWithZ9999: allFixedElements,
      }
    })

    console.log('=== TOAST DIAGNOSTICS ===')
    console.log(JSON.stringify(diagnostics, null, 2))
    console.log('=========================')

    // Take full-page screenshot
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'toast-debug-after-publish-full.png'),
      fullPage: true,
    })

    // Take viewport screenshot
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'toast-debug-after-publish-viewport.png'),
      fullPage: false,
    })

    // Check if page title was changed by showToast
    const hasToastTitle = diagnostics.pageTitle.startsWith('TOAST:')
    console.log('Title changed by showToast:', hasToastTitle)

    // Check if toast container is in DOM
    console.log('Toast container in DOM:', diagnostics.toastContainerExists)
    
    expect(hasToastTitle).toBeTruthy()
  })

  test('toast debug — delete template and check toast visibility', async ({ page }) => {
    // This test runs after publish, so "E2E Publish Test 2" should still be a draft
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(500)

    await page.evaluate(() => { document.title = 'initial' })

    // Find "E2E Publish Test 2" (draft) — don't delete the published one as other tests rely on it
    const row = page.getByRole('row').filter({ has: page.getByText('E2E Publish Test 2', { exact: true }) })
    await expect(row).toBeVisible({ timeout: 10000 })

    // Click delete button (trash icon, class "danger")
    const deleteBtn = row.locator('td:last-child button.btn.icon-only.danger')
    await expect(deleteBtn).toBeVisible()
    await deleteBtn.click()

    // Confirm in dialog
    await expect(page.getByText('Удалить шаблон?')).toBeVisible()
    await page.getByRole('button', { name: 'Удалить' }).click()

    // Wait for deletion
    await page.waitForTimeout(1000)

    // Diagnostics
    const diagnostics = await page.evaluate(() => {
      const toastContainer = document.querySelector('[data-testid="toast-container"]')
      return {
        pageTitle: document.title,
        toastContainerExists: !!toastContainer,
        toastContainerHTML: toastContainer?.outerHTML ?? null,
      }
    })

    console.log('=== DELETE TOAST DIAGNOSTICS ===')
    console.log(JSON.stringify(diagnostics, null, 2))
    console.log('================================')

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'toast-debug-after-delete.png'),
      fullPage: true,
    })
  })

  test('toast debug — verify showToast is called and CustomEvent works', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')

    // Manually dispatch a toast event to test the CustomEvent path
    await page.evaluate(() => {
      window.dispatchEvent(
        new CustomEvent('app-toast', {
          detail: { id: 999, message: 'Тестовое уведомление из Playwright', type: 'success' },
        })
      )
    })

    await page.waitForTimeout(500)

    const result = await page.evaluate(() => {
      const container = document.querySelector('[data-testid="toast-container"]')
      const alerts = document.querySelectorAll('[role="alert"]')
      return {
        containerExists: !!container,
        containerHTML: container?.outerHTML ?? null,
        alertCount: alerts.length,
        alertTexts: Array.from(alerts).map(el => el.textContent),
      }
    })

    console.log('=== CUSTOM EVENT DIAGNOSTICS ===')
    console.log(JSON.stringify(result, null, 2))
    console.log('=================================')

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, 'toast-debug-custom-event.png'),
      fullPage: false,
    })
  })

  test('toast debug — check for CSS conflicts and z-index issues', async ({ page }) => {
    await page.goto('/templates')
    await page.waitForLoadState('networkidle')

    // Dispatch a test toast
    await page.evaluate(() => {
      window.dispatchEvent(
        new CustomEvent('app-toast', {
          detail: { id: 888, message: 'CSS debug toast', type: 'success' },
        })
      )
    })

    await page.waitForTimeout(500)

    // Check CSS computed styles of the toast
    const cssDiagnostics = await page.evaluate(() => {
      const toast = document.querySelector('[role="alert"]')
      if (!toast) return { error: 'No toast element found' }

      const style = window.getComputedStyle(toast)
      const parent = toast.parentElement
      const parentStyle = parent ? window.getComputedStyle(parent) : null

      return {
        toast: {
          position: style.position,
          zIndex: style.zIndex,
          opacity: style.opacity,
          visibility: style.visibility,
          display: style.display,
          transform: style.transform,
          color: style.color,
          background: style.background,
          clipPath: style.clipPath,
          overflow: style.overflow,
          pointerEvents: style.pointerEvents,
          width: style.width,
          height: style.height,
          bottom: style.bottom,
          right: style.right,
        },
        parent: parentStyle ? {
          position: parentStyle.position,
          zIndex: parentStyle.zIndex,
          opacity: parentStyle.opacity,
          display: parentStyle.display,
        } : null,
        bodyStyles: {
          overflow: document.body.style.overflow,
          position: document.body.style.position,
          pointerEvents: document.body.style.pointerEvents,
          height: document.body.style.height,
        },
        bodyComputed: {
          overflow: window.getComputedStyle(document.body).overflow,
          position: window.getComputedStyle(document.body).position,
        }
      }
    })

    console.log('=== CSS DIAGNOSTICS ===')
    console.log(JSON.stringify(cssDiagnostics, null, 2))
    console.log('=======================')
  })
})
