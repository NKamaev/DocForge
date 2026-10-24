/**
 * BUG-068 — список шаблонов не должен предлагать изменяющие действия
 * (публикация, удаление) над шаблонами, которыми пользователь не владеет.
 *
 * Сервер (BUG-068) отвечает на любую изменяющую операцию 403 «Нет прав на
 * изменение шаблона», если вызывающий — не владелец шаблона и не
 * администратор. Чтобы интерфейс не предлагал заведомо отклоняемые
 * действия, список скрывает их для чужих шаблонов; администратор сохраняет
 * доступ ко всем действиям.
 *
 * Дополнительно проверяется, что текст сервера (в т.ч. 403 «Нет прав на
 * изменение шаблона») показывается пользователю, а не заменяется общим
 * сообщением об ошибке.
 */
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import TemplatesPage from './TemplatesPage'
import { templatesApi } from '../api/templatesApi'
import { ToastContainer } from '@/components/ui/toast'
import { TooltipProvider } from '@/components/ui'
import type { TemplateListItem } from '../types/template'

const { authState } = vi.hoisted(() => ({
  authState: {
    user: {
      id: 'user-1',
      login: 'ivan',
      displayName: 'Иван Петров',
      admin: false,
      createdAt: '2026-01-01T00:00:00Z',
    },
    token: 'token',
    isAuthenticated: true,
    isUserResolved: true,
  },
}))

vi.mock('../store/authStore', () => ({
  useAuthStore: (selector?: (s: typeof authState) => unknown) =>
    typeof selector === 'function' ? selector(authState) : authState,
}))

vi.mock('../api/templatesApi', () => ({
  templatesApi: {
    getTemplates: vi.fn(),
    createTemplate: vi.fn(),
    publishTemplate: vi.fn(),
    unpublishTemplate: vi.fn(),
    deleteTemplate: vi.fn(),
  },
}))

function template(overrides: Partial<TemplateListItem>): TemplateListItem {
  return {
    id: 't-1',
    code: null,
    name: 'Договор поставки',
    description: null,
    category: null,
    published: false,
    majorVersion: 1,
    minorVersion: 0,
    createdAt: '2026-01-01T00:00:00Z',
    ownerId: 'user-1',
    ...overrides,
  }
}

const OWN_TEMPLATE = template({ id: 't-own', name: 'Свой шаблон', ownerId: 'user-1' })
const FOREIGN_TEMPLATE = template({
  id: 't-foreign',
  name: 'Чужой шаблон',
  published: true,
  ownerId: 'user-2',
})

function mockList(items: TemplateListItem[]) {
  vi.mocked(templatesApi.getTemplates).mockResolvedValue({
    content: items,
    totalElements: items.length,
    totalPages: 1,
    size: 100,
    number: 0,
  })
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/templates']}>
      <TooltipProvider>
        <TemplatesPage />
        <ToastContainer />
      </TooltipProvider>
    </MemoryRouter>,
  )
}

/** Строка таблицы по имени шаблона. */
async function rowByName(name: string): Promise<HTMLElement> {
  const cell = await screen.findByText(name)
  const row = cell.closest('tr')
  expect(row).not.toBeNull()
  return row as HTMLElement
}

describe('TemplatesPage — права на изменяющие действия (BUG-068)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authState.user = { ...authState.user, id: 'user-1', admin: false }
    mockList([OWN_TEMPLATE, FOREIGN_TEMPLATE])
  })

  it('offers publish and delete for a template the user owns', async () => {
    renderPage()

    const row = await rowByName('Свой шаблон')
    expect(within(row).getByRole('button', { name: 'Опубликовать шаблон' })).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: 'Удалить шаблон' })).toBeInTheDocument()
  })

  it('hides publish and delete for a template the user does not own', async () => {
    renderPage()

    const row = await rowByName('Чужой шаблон')
    expect(within(row).queryByRole('button', { name: /Опубликовать шаблон/ })).toBeNull()
    expect(within(row).queryByRole('button', { name: /Снять с публикации/ })).toBeNull()
    expect(within(row).queryByRole('button', { name: 'Удалить шаблон' })).toBeNull()
    // Открыть шаблон для чтения по-прежнему можно.
    expect(within(row).getByRole('button', { name: 'Открыть редактор' })).toBeInTheDocument()
  })

  it('keeps the actions for an administrator even on a foreign template', async () => {
    authState.user = { ...authState.user, admin: true }
    renderPage()

    const row = await rowByName('Чужой шаблон')
    expect(within(row).getByRole('button', { name: 'Снять с публикации' })).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: 'Удалить шаблон' })).toBeInTheDocument()
  })

  it('hides the actions while the user identity is unknown (fail-closed)', async () => {
    authState.user = null as unknown as typeof authState.user
    renderPage()

    const row = await rowByName('Свой шаблон')
    expect(within(row).queryByRole('button', { name: /Опубликовать шаблон/ })).toBeNull()
    expect(within(row).queryByRole('button', { name: 'Удалить шаблон' })).toBeNull()
  })

  it('never calls the mutating API for a template the user does not own', async () => {
    renderPage()

    const row = await rowByName('Чужой шаблон')
    expect(within(row).queryByRole('button', { name: 'Удалить шаблон' })).toBeNull()
    expect(within(row).queryByRole('button', { name: 'Снять с публикации' })).toBeNull()

    await waitFor(() => expect(templatesApi.getTemplates).toHaveBeenCalled())
    expect(templatesApi.publishTemplate).not.toHaveBeenCalled()
    expect(templatesApi.unpublishTemplate).not.toHaveBeenCalled()
    expect(templatesApi.deleteTemplate).not.toHaveBeenCalled()
  })

  it('shows the server text of a 403 «Нет прав на изменение шаблона»', async () => {
    vi.mocked(templatesApi.publishTemplate).mockRejectedValue({
      response: { status: 403, data: { detail: 'Нет прав на изменение шаблона' } },
    })

    renderPage()

    const row = await rowByName('Свой шаблон')
    fireEvent.click(within(row).getByRole('button', { name: 'Опубликовать шаблон' }))

    const dialog = await screen.findByRole('alertdialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Опубликовать' }))

    expect(await screen.findByText('Нет прав на изменение шаблона')).toBeInTheDocument()
  })
})
