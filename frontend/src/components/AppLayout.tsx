import { useState, useEffect } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { documentsApi } from '../api/documentsApi'
import type { DocumentListItem } from '../types/document'
import { Icon } from './Icon'
import { BrandMark } from './BrandMark'

const NAV_ITEMS = [
  { key: '/', icon: 'home', label: 'Главная' },
  { key: '/documents', icon: 'docs', label: 'Мои документы' },
  { key: '/templates', icon: 'templates', label: 'Шаблоны' },
  { key: '/directories', icon: 'book', label: 'Справочники' },
]

function getInitials(name: string): string {
  return name
    .split(' ')
    .slice(0, 2)
    .map((w) => w[0] ?? '')
    .join('')
    .toUpperCase()
}

function getActiveKey(pathname: string): string {
  for (const item of NAV_ITEMS.slice(1)) {
    if (pathname.startsWith(item.key)) return item.key
  }
  return '/'
}

export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuthStore()
  const [collapsed, setCollapsed] = useState(true)
  const [recentDocs, setRecentDocs] = useState<DocumentListItem[]>([])

  useEffect(() => {
    documentsApi.list(0, 5).then((page) => setRecentDocs(page.content)).catch(() => {})
  }, [])

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const activeKey = getActiveKey(location.pathname)
  const initials = user?.displayName ? getInitials(user.displayName) : '?'

  return (
    <div className={'app' + (collapsed ? ' sidebar-collapsed' : '')}>
      <header className="topbar">
        <div className="topbar-left">
          <button
            className="icon-btn"
            onClick={() => setCollapsed((c) => !c)}
            title={collapsed ? 'Показать панель' : 'Скрыть панель'}
          >
            <Icon name="fold" size={16} />
          </button>
          <div className="brand-mark">
            <BrandMark size={22} />
            <div className="name">
              <b>Doc</b>
              <span>Forge</span>
            </div>
          </div>
          <div style={{ width: 1, height: 22, background: 'var(--line)', flexShrink: 0 }} />
          <nav className="ws-switch" aria-label="Рабочее пространство">
            {NAV_ITEMS.map((n) => (
              <button
                key={n.key}
                className={'ws-pill' + (activeKey === n.key ? ' active' : '')}
                onClick={() => navigate(n.key)}
              >
                <Icon name={n.icon} size={14} />
                <span>{n.label}</span>
              </button>
            ))}
          </nav>
        </div>

        <div className="topbar-right">
          <button className="icon-btn" title="Помощь">
            <Icon name="info" size={16} />
          </button>
          <button className="icon-btn" title="Уведомления">
            <Icon name="alert" size={16} />
          </button>
          <div style={{ width: 1, height: 22, background: 'var(--line)', margin: '0 6px', flexShrink: 0 }} />
          <div className="user-pill" onClick={handleLogout} title="Выйти" style={{ cursor: 'pointer' }}>
            <div className="avatar">{initials}</div>
            <div className="user-pill-text">
              <span style={{ fontSize: 12.5, fontWeight: 500 }}>{user?.displayName}</span>
              <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>{user?.login}</span>
            </div>
            <Icon name="chev-down" size={14} />
          </div>
        </div>
      </header>

      <aside className={'sidebar' + (collapsed ? ' collapsed' : '')}>
        <div className="sidebar-section sb-row">
          <span className="sb-section-label">Недавние</span>
          <button
            className="icon-btn sb-fold"
            onClick={() => setCollapsed(true)}
            title="Скрыть панель"
          >
            <Icon name="chev-left" size={13} />
          </button>
        </div>

        {recentDocs.map((d) => (
          <div
            key={d.id}
            className={'nav-item' + (location.pathname.includes(d.id) ? ' active' : '')}
            onClick={() => navigate(`/documents/${d.id}/fill`)}
            title={d.name}
          >
            <Icon name="doc" size={15} />
            <span>{d.name}</span>
          </div>
        ))}

        <div className="sidebar-section">Закладки</div>
        <div className="nav-item">
          <Icon name="templates" size={15} />
          <span>Договор аренды</span>
        </div>
        <div className="nav-item">
          <Icon name="templates" size={15} />
          <span>Приказ об отпуске</span>
        </div>
        <div className="nav-item">
          <Icon name="templates" size={15} />
          <span>NDA · стандартный</span>
        </div>

        <div className="sidebar-section">Команды</div>
        <div className="nav-item">
          <Icon name="users" size={15} />
          <span>Юридический отдел</span>
          <span className="count">6</span>
        </div>
        <div className="nav-item">
          <Icon name="users" size={15} />
          <span>Кадры</span>
          <span className="count">3</span>
        </div>

        <div className="sidebar-foot">
          <span className="dot" />
          <span>Все изменения сохранены</span>
        </div>
      </aside>

      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
