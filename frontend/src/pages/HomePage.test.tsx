/**
 * BUG-044 — «Счетчик шаблонов за неделю на главной не работает».
 *
 * HomePage used to render a hardcoded trend label `+2 за неделю` on the
 * «Шаблонов» stat card. The fix asks the backend for the count of templates
 * created since the start of the current week:
 *
 *   templatesApi.getTemplates({ page: 0, size: 1, createdAfter: startOfWeek().toISOString() })
 *
 * and renders `+{totalElements} за неделю`. These tests pin that contract:
 *  1. the request carries `createdAfter === startOfWeek().toISOString()`;
 *  2. the rendered trend reflects the server-provided `totalElements`;
 *  3. a rejected request hides the trend entirely (no hardcoded fallback, no crash).
 */
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router-dom'
import HomePage from './HomePage'
import { templatesApi } from '../api/templatesApi'
import { documentsApi } from '../api/documentsApi'
import { startOfWeek } from '../utils/startOfWeek'

vi.mock('../store/authStore', () => {
  const user = {
    id: 'user-1',
    login: 'ivan',
    displayName: 'Иван Петров',
    admin: false,
    createdAt: '2026-01-01T00:00:00Z',
  }
  const state = { user, token: 'token', isAuthenticated: true }
  return {
    useAuthStore: (selector?: (s: typeof state) => unknown) =>
      typeof selector === 'function' ? selector(state) : state,
  }
})

vi.mock('../api/templatesApi', () => ({
  templatesApi: {
    getTemplates: vi.fn(),
  },
}))

vi.mock('../api/documentsApi', () => ({
  documentsApi: {
    list: vi.fn(),
  },
}))

const EMPTY_DOCS_PAGE = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  size: 4,
  number: 0,
}

function templatesPage(totalElements: number) {
  return {
    content: [],
    totalElements,
    totalPages: 0,
    size: 1,
    number: 0,
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <HomePage />
    </MemoryRouter>,
  )
}

/** Renders the page next to a probe of the router location. */
function renderPageWithLocationProbe() {
  function LocationProbe() {
    const location = useLocation()
    return <div data-testid="location">{location.pathname}</div>
  }

  return render(
    <MemoryRouter initialEntries={['/']}>
      <HomePage />
      <LocationProbe />
    </MemoryRouter>,
  )
}

/** All `getTemplates` calls that carried a `createdAfter` parameter. */
function createdAfterCalls() {
  return vi
    .mocked(templatesApi.getTemplates)
    .mock.calls.filter(([params]) => params?.createdAfter !== undefined)
}

describe('HomePage — weekly templates counter (BUG-044)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(documentsApi.list).mockResolvedValue(EMPTY_DOCS_PAGE)
    vi.mocked(templatesApi.getTemplates).mockResolvedValue(templatesPage(0))
  })

  it('requests the weekly count with createdAfter = startOfWeek().toISOString()', async () => {
    const expected = startOfWeek().toISOString()

    renderPage()

    await waitFor(() => expect(createdAfterCalls()).toHaveLength(1))

    expect(templatesApi.getTemplates).toHaveBeenCalledWith({
      page: 0,
      size: 1,
      createdAfter: expected,
    })

    // The boundary must really be a Monday 00:00 local time.
    const firstCall = createdAfterCalls()[0]?.[0]
    const boundary = new Date(String(firstCall?.createdAfter))
    expect(boundary.getDay()).toBe(1)
    expect(boundary.getHours()).toBe(0)
    expect(boundary.getMinutes()).toBe(0)
    expect(boundary.getSeconds()).toBe(0)
    expect(boundary.getMilliseconds()).toBe(0)
  })

  it('renders the server-provided weekly count instead of a hardcoded value', async () => {
    vi.mocked(templatesApi.getTemplates).mockImplementation((params = {}) =>
      Promise.resolve(templatesPage(params.createdAfter !== undefined ? 7 : 53)),
    )

    renderPage()

    await screen.findByText('+7 за неделю')
    expect(screen.getByText('53')).toBeInTheDocument()
    expect(screen.queryByText('+2 за неделю')).not.toBeInTheDocument()
  })

  it('hides the trend tag when the weekly request fails (no crash)', async () => {
    vi.mocked(templatesApi.getTemplates).mockImplementation((params = {}) =>
      params.createdAfter !== undefined
        ? Promise.reject(new Error('boom'))
        : Promise.resolve(templatesPage(53)),
    )

    renderPage()

    // The page still renders the total count, proving the rejection was handled.
    await screen.findByText('53')
    await waitFor(() => expect(createdAfterCalls()).toHaveLength(1))

    expect(screen.queryByText(/за неделю/)).not.toBeInTheDocument()
    expect(screen.getByText('Шаблонов')).toBeInTheDocument()
  })
})

/**
 * BUG-057 — «Импорт DOCX» на главной выглядит рабочим, но ничего не делает.
 *
 * DOCX import is out of MVP scope, so the entry point was removed entirely
 * rather than left as a disabled placeholder. The quick-action copy no longer
 * promises DOCX import; it points at the template catalogue instead.
 */
describe('HomePage — DOCX import entry point removed (BUG-057)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(documentsApi.list).mockResolvedValue(EMPTY_DOCS_PAGE)
    vi.mocked(templatesApi.getTemplates).mockResolvedValue(templatesPage(0))
  })

  it('renders no «Импорт DOCX» button at all', () => {
    renderPage()

    expect(screen.queryByRole('button', { name: /Импорт DOCX/i })).toBeNull()
  })

  it('no longer promises DOCX import and offers the template catalogue instead', async () => {
    renderPage()

    await screen.findByText('Быстрые действия')

    expect(document.body).not.toHaveTextContent(/импортируйте DOCX/i)
    expect(document.body).toHaveTextContent('выберите готовый шаблон')
  })

  it('navigates to /templates when the primary «Новый шаблон» button is clicked', async () => {
    renderPageWithLocationProbe()

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)

    fireEvent.click(screen.getByRole('button', { name: /Новый шаблон/i }))

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent('/templates'),
    )
  })
})
