// shell.jsx — top bar, sidebar nav

const BrandMark = ({ size = 24 }) => (
  <svg width={size} height={size} viewBox="0 0 32 32" style={{ display: "block" }}>
    <rect x="1" y="1" width="30" height="30" rx="7" fill="var(--brand)"/>
    <path d="M9 8 H15.5 A8 8 0 0 1 15.5 24 H9 Z" fill="none" stroke="white" strokeWidth="2.4" strokeLinejoin="round"/>
    <path d="M19 11 L23 16 L19 21" fill="none" stroke="white" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>
);

const Topbar = ({ user, onSignOut, onToggleSidebar, sidebarCollapsed, active, setActive }) => (
  <header className="topbar">
    <div className="topbar-left">
      <button
        className="icon-btn"
        onClick={onToggleSidebar}
        title={sidebarCollapsed ? "Показать панель" : "Скрыть панель"}>
        <Icon name="fold" size={16}/>
      </button>
      <div className="brand-mark">
        <BrandMark size={22}/>
        <div className="name"><b>Doc</b><span>Forge</span></div>
      </div>
      <div style={{ width: 1, height: 22, background: "var(--line)" }}/>
      <nav className="ws-switch" aria-label="Рабочее пространство">
        {NAV_ITEMS.map(n => (
          <button
            key={n.key}
            className={"ws-pill" + (active === n.key ? " active" : "")}
            onClick={() => setActive(n.key)}>
            <Icon name={n.icon} size={14}/>
            <span>{n.label}</span>
            {n.count != null && <span className="ws-count">{n.count}</span>}
          </button>
        ))}
      </nav>
    </div>
    <div className="topbar-right">
      <div className="topbar-search" role="search">
        <Icon name="search" size={14}/>
        <input placeholder="Найти…"/>
        <kbd>⌘K</kbd>
      </div>
      <button className="icon-btn" title="Помощь"><Icon name="info" size={16}/></button>
      <button className="icon-btn" title="Уведомления"><Icon name="alert" size={16}/></button>
      <div style={{ width: 1, height: 22, background: "var(--line)", margin: "0 6px" }}/>
      <div className="user-pill">
        <div className="avatar">{user.initials}</div>
        <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.15 }} className="user-pill-text">
          <span style={{ fontSize: 12.5, fontWeight: 500 }}>{user.name}</span>
          <span style={{ fontSize: 11, color: "var(--ink-3)" }}>{user.role}</span>
        </div>
        <Icon name="chev-down" size={14}/>
      </div>
    </div>
  </header>
);

const Sidebar = ({ active, setActive, collapsed, onToggle }) => (
  <aside className={"sidebar" + (collapsed ? " collapsed" : "")}>
    <div className="sidebar-section sb-row">
      <span className="sb-section-label">Недавние</span>
      <button className="icon-btn sb-fold" onClick={onToggle} title="Скрыть панель">
        <Icon name="chev-left" size={13}/>
      </button>
    </div>
    {SAMPLE_DOCUMENTS.slice(0, 5).map(d => (
      <div key={d.id} className="nav-item" onClick={() => setActive("workspace")} title={d.name}>
        <Icon name="doc" size={15}/>
        <span>{d.name}</span>
      </div>
    ))}
    <div className="sidebar-section">Закладки</div>
    <div className="nav-item" onClick={() => setActive("designer")}>
      <Icon name="templates" size={15}/>
      <span>Договор аренды</span>
    </div>
    <div className="nav-item">
      <Icon name="templates" size={15}/>
      <span>Приказ об отпуске</span>
    </div>
    <div className="nav-item">
      <Icon name="templates" size={15}/>
      <span>NDA · стандартный</span>
    </div>

    <div className="sidebar-section">Команды</div>
    <div className="nav-item">
      <Icon name="users" size={15}/>
      <span>Юридический отдел</span>
      <span className="count">6</span>
    </div>
    <div className="nav-item">
      <Icon name="users" size={15}/>
      <span>Кадры</span>
      <span className="count">3</span>
    </div>

    <div className="sidebar-foot">
      <span className="dot"/>
      <span>Все изменения сохранены</span>
    </div>
  </aside>
);
