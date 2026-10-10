// screens-simple.jsx — Login, Home, Templates, Documents, Directories

const Login = ({ onSignIn }) => {
  const [tab, setTab] = React.useState("signin");
  return (
    <div className="login-screen">
      <div className="login-art">
        <div className="brand-mark">
          <BrandMark size={26}/>
          <div className="name" style={{ fontSize: 17 }}><b>Doc</b><span>Forge</span></div>
        </div>
        <div style={{ position: "relative", zIndex: 1 }}>
          <h1>Конструктор документов&nbsp;— без боли и&nbsp;без копипаста.</h1>
          <p>Соберите шаблон один раз. Заполняйте по&nbsp;опросному листу. Экспортируйте в&nbsp;DOCX или&nbsp;PDF за&nbsp;секунду.</p>
        </div>
        <div className="stamp"/>
        <div className="demo-doc" aria-hidden="true">
          <h4>ДОГОВОР АРЕНДЫ № <span className="chip chip-param">№ договора</span></h4>
          <div style={{ textAlign: "center", fontSize: "8.5pt", margin: "2px 0 8px", color: "var(--ink-3)" }}>
            г. <span className="chip chip-param">Город</span>, <span className="chip chip-param">Дата</span>
          </div>
          <div className="l"/><div className="l"/><div className="l s"/>
          <div style={{ height: 6 }}/>
          <div className="l"/><div className="l"/>
          <div style={{ height: 4 }}/>
          <div style={{ fontSize: "8.5pt" }}>
            <span className="chip chip-cond-inline">если с мебелью</span>
          </div>
          <div className="l s"/>
        </div>
        <div style={{ position: "relative", fontSize: 12, color: "var(--ink-3)", display: "flex", gap: 14, zIndex: 1 }}>
          <span>© 2026 DocForge</span>
          <span>·</span>
          <span>Версия 4.2.0</span>
        </div>
      </div>
      <div className="login-form">
        <h2>{tab === "signin" ? "Войдите в DocForge" : "Создайте аккаунт"}</h2>
        <p className="sub">{tab === "signin" ? "Введите логин и пароль, чтобы продолжить." : "Заполните форму, чтобы получить доступ к рабочему пространству."}</p>
        <div className="tabs pill" style={{ marginBottom: 22, alignSelf: "flex-start" }}>
          <div className={"tab" + (tab === "signin" ? " active" : "")} onClick={() => setTab("signin")}>Вход</div>
          <div className={"tab" + (tab === "signup" ? " active" : "")} onClick={() => setTab("signup")}>Регистрация</div>
        </div>
        {tab === "signup" && (
          <div className="field">
            <label className="field-lbl">Отображаемое имя</label>
            <input className="input lg" placeholder="Анна Соколова"/>
          </div>
        )}
        <div className="field">
          <label className="field-lbl">Логин</label>
          <div className="input-group" style={{ height: 40 }}>
            <span className="icon"><Icon name="user" size={15}/></span>
            <input placeholder="anna.sokolova" defaultValue={tab === "signin" ? "anna.sokolova" : ""}/>
          </div>
        </div>
        {tab === "signup" && (
          <div className="field">
            <label className="field-lbl">Email <span className="muted" style={{ fontWeight: 400 }}>(необязательно)</span></label>
            <input className="input lg" placeholder="anna@example.ru"/>
          </div>
        )}
        <div className="field">
          <label className="field-lbl">Пароль</label>
          <div className="input-group" style={{ height: 40 }}>
            <span className="icon"><Icon name="lock" size={15}/></span>
            <input type="password" placeholder="Минимум 6 символов" defaultValue={tab === "signin" ? "passw0rd" : ""}/>
          </div>
          {tab === "signin" && <a href="#" style={{ alignSelf: "flex-end", fontSize: 11.5, color: "var(--brand-ink)", textDecoration: "none", marginTop: 4 }}>Забыли пароль?</a>}
        </div>
        <button className="btn primary lg block" style={{ marginTop: 6 }} onClick={onSignIn}>
          {tab === "signin" ? "Войти" : "Создать аккаунт"} <Icon name="chev-right" size={14}/>
        </button>
        <div className="meta">
          {tab === "signin" ? <>Нет аккаунта? <a href="#" onClick={(e)=>{e.preventDefault();setTab("signup");}}>Зарегистрируйтесь</a></>
                            : <>Уже зарегистрированы? <a href="#" onClick={(e)=>{e.preventDefault();setTab("signin");}}>Войти</a></>}
        </div>
      </div>
    </div>
  );
};

const StatCard = ({ icon, color, num, label, trend }) => (
  <div className="card pad" style={{ display: "flex", flexDirection: "column", gap: 8 }}>
    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
      <div style={{ width: 32, height: 32, borderRadius: 8, background: color || "var(--brand-soft)", color: "var(--brand-ink)", display: "grid", placeItems: "center" }}>
        <Icon name={icon} size={16}/>
      </div>
      {trend && <span className="tag green"><span className="dot"/>{trend}</span>}
    </div>
    <div style={{ fontSize: 26, fontWeight: 600, letterSpacing: "-0.02em", lineHeight: 1 }}>{num}</div>
    <div style={{ fontSize: 13, color: "var(--ink-3)" }}>{label}</div>
  </div>
);

const Home = ({ go, user }) => (
  <div className="page">
    <div className="page-head">
      <div>
        <div className="crumb"><Icon name="home" size={12}/> <span>Главная</span></div>
        <h1>Здравствуйте, {user.firstName}.</h1>
        <p>Вторник, 28 апреля. У вас <b>3 черновика</b> и <b>1 документ</b> ожидает завершения опросного листа.</p>
      </div>
      <div className="page-head-actions">
        <button className="btn"><Icon name="upload" size={14}/> Импорт DOCX</button>
        <button className="btn primary" onClick={() => go("designer")}><Icon name="plus" size={14}/> Новый шаблон</button>
      </div>
    </div>

    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 22 }}>
      <StatCard icon="templates" num="9" label="Шаблонов" trend="+2 за неделю"/>
      <StatCard icon="doc" num="34" label="Документов в работе"/>
      <StatCard icon="users" num="6" label="Активных соавторов" color="var(--accent-green-soft)"/>
      <StatCard icon="download" num="128" label="Экспортов в этом месяце"/>
    </div>

    <div style={{ display: "grid", gridTemplateColumns: "1.5fr 1fr", gap: 14 }}>
      <div className="card">
        <div style={{ padding: "14px 18px", borderBottom: "1px solid var(--line)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 550 }}>Продолжить работу</h3>
          <a onClick={() => go("documents")} style={{ fontSize: 12, color: "var(--brand-ink)", cursor: "pointer" }}>Все документы →</a>
        </div>
        <div style={{ padding: "6px 8px" }}>
          {SAMPLE_DOCUMENTS.slice(0, 4).map(d => (
            <div key={d.id} onClick={() => go("workspace")} style={{ display: "grid", gridTemplateColumns: "26px 1fr auto auto", gap: 12, alignItems: "center", padding: "10px 10px", borderRadius: 8, cursor: "pointer" }} className="hoverable-row">
              <div style={{ width: 26, height: 26, borderRadius: 6, background: "var(--brand-soft)", color: "var(--brand-ink)", display: "grid", placeItems: "center" }}>
                <Icon name="doc" size={14}/>
              </div>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 13.5, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{d.name}</div>
                <div style={{ fontSize: 11.5, color: "var(--ink-3)" }}>{d.template} · {d.updated}</div>
              </div>
              <div style={{ width: 110, display: "flex", flexDirection: "column", gap: 4 }}>
                <div style={{ fontSize: 11, color: "var(--ink-3)", display: "flex", justifyContent: "space-between" }}>
                  <span>Заполнено</span><span className="tabular">{d.progress}%</span>
                </div>
                <div className="q-progress" style={{ marginTop: 0 }}><div style={{ width: d.progress + "%" }}/></div>
              </div>
              <Icon name="chev-right" size={14}/>
            </div>
          ))}
        </div>
      </div>

      <div className="card pad" style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 550 }}>Быстрые действия</h3>
        <div onClick={() => go("designer")} className="card hover" style={{ padding: 14, display: "flex", gap: 12, alignItems: "flex-start" }}>
          <div style={{ width: 34, height: 34, borderRadius: 8, background: "var(--brand-soft)", color: "var(--brand-ink)", display: "grid", placeItems: "center" }}><Icon name="templates" size={16}/></div>
          <div>
            <div style={{ fontSize: 13.5, fontWeight: 550 }}>Создать шаблон</div>
            <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 2 }}>Начните с пустой страницы или импортируйте DOCX.</div>
          </div>
        </div>
        <div onClick={() => go("workspace")} className="card hover" style={{ padding: 14, display: "flex", gap: 12, alignItems: "flex-start" }}>
          <div style={{ width: 34, height: 34, borderRadius: 8, background: "var(--accent-green-soft)", color: "oklch(0.42 0.12 150)", display: "grid", placeItems: "center" }}><Icon name="doc" size={16}/></div>
          <div>
            <div style={{ fontSize: 13.5, fontWeight: 550 }}>Сгенерировать документ</div>
            <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 2 }}>Выберите шаблон и заполните опросный лист.</div>
          </div>
        </div>
        <div onClick={() => go("directories")} className="card hover" style={{ padding: 14, display: "flex", gap: 12, alignItems: "flex-start" }}>
          <div style={{ width: 34, height: 34, borderRadius: 8, background: "oklch(0.96 0.04 280)", color: "oklch(0.4 0.13 280)", display: "grid", placeItems: "center" }}><Icon name="book" size={16}/></div>
          <div>
            <div style={{ fontSize: 13.5, fontWeight: 550 }}>Справочники</div>
            <div style={{ fontSize: 12, color: "var(--ink-3)", marginTop: 2 }}>Города, организации, сотрудники.</div>
          </div>
        </div>
      </div>
    </div>
  </div>
);

const Templates = ({ go, openCreate }) => {
  const [filter, setFilter] = React.useState("all");
  const filtered = SAMPLE_TEMPLATES.filter(t => filter === "all" || (filter === "published" ? t.status === "published" : t.status === "draft"));
  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="crumb"><Icon name="home" size={12}/> <span onClick={() => go("home")} style={{ cursor: "pointer" }}>Главная</span> <Icon name="chev-right" size={11}/> <span>Шаблоны</span></div>
          <h1>Шаблоны</h1>
          <p>Конструируйте, версионируйте и публикуйте многоразовые шаблоны для команды.</p>
        </div>
        <div className="page-head-actions">
          <button className="btn"><Icon name="filter" size={14}/> Фильтр</button>
          <button className="btn"><Icon name="upload" size={14}/> Импорт DOCX</button>
          <button className="btn primary" onClick={openCreate}><Icon name="plus" size={14}/> Создать шаблон</button>
        </div>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
        <div className="tabs pill">
          <div className={"tab" + (filter === "all" ? " active" : "")} onClick={() => setFilter("all")}>Все · {SAMPLE_TEMPLATES.length}</div>
          <div className={"tab" + (filter === "published" ? " active" : "")} onClick={() => setFilter("published")}>Опубликованные · {SAMPLE_TEMPLATES.filter(t=>t.status==="published").length}</div>
          <div className={"tab" + (filter === "draft" ? " active" : "")} onClick={() => setFilter("draft")}>Черновики · {SAMPLE_TEMPLATES.filter(t=>t.status==="draft").length}</div>
        </div>
        <div className="input-group" style={{ width: 240 }}>
          <span className="icon"><Icon name="search" size={14}/></span>
          <input placeholder="Поиск по названию"/>
        </div>
      </div>

      <div className="tbl">
        <table>
          <thead><tr>
            <th style={{ width: "38%" }}>Название</th>
            <th>Категория</th>
            <th>Статус</th>
            <th>Использован</th>
            <th>Автор</th>
            <th>Обновлён</th>
            <th style={{ width: 130 }}></th>
          </tr></thead>
          <tbody>
            {filtered.map(t => (
              <tr key={t.id}>
                <td>
                  <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                    <div style={{ width: 28, height: 28, borderRadius: 6, background: "var(--bg-soft)", color: "var(--ink-2)", display: "grid", placeItems: "center", border: "1px solid var(--line)" }}>
                      <Icon name="doc" size={14}/>
                    </div>
                    <div>
                      <div style={{ fontWeight: 500, cursor: "pointer" }} onClick={() => go("designer")}>{t.name}</div>
                      <div style={{ fontSize: 11.5, color: "var(--ink-3)" }}>v{t.id === "tpl-rent" ? "2.3" : "1.0"} · 12 параметров</div>
                    </div>
                  </div>
                </td>
                <td><span className="tag">{t.category}</span></td>
                <td>
                  {t.status === "published"
                    ? <span className="tag green"><span className="dot"/>Опубликован</span>
                    : <span className="tag amber"><span className="dot"/>Черновик</span>}
                </td>
                <td className="tabular">{t.count}</td>
                <td className="muted">{t.author}</td>
                <td className="muted tabular">{t.updated}</td>
                <td>
                  <div className="row-actions">
                    <button className="btn xs" onClick={() => go("designer")}>Открыть</button>
                    <button className="btn xs icon-only"><Icon name="more-h" size={14}/></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="tbl-foot">
          <span>Всего: {filtered.length}</span>
          <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <button className="btn xs icon-only" disabled><Icon name="chev-left" size={13}/></button>
            <span className="tabular">1 / 1</span>
            <button className="btn xs icon-only" disabled><Icon name="chev-right" size={13}/></button>
            <span style={{ color: "var(--ink-3)", marginLeft: 8 }}>20 / стр.</span>
          </span>
        </div>
      </div>
    </div>
  );
};

const Documents = ({ go }) => {
  const [currentFolder, setCurrentFolder] = React.useState(null); // null = root
  const [expanded, setExpanded] = React.useState(new Set(["f-contracts", "f-hr"]));
  const [selected, setSelected] = React.useState(new Set());
  const [view, setView] = React.useState("list"); // list | grid

  const folderById = React.useMemo(() => {
    const m = {}; SAMPLE_FOLDERS.forEach(f => m[f.id] = f); return m;
  }, []);

  // Inherit color from nearest ancestor
  const getColor = (id) => {
    let f = folderById[id];
    while (f && f.color == null) f = f.parent ? folderById[f.parent] : null;
    return f ? f.color : 220;
  };

  const childFolders = (parent) => SAMPLE_FOLDERS.filter(f => f.parent === parent);
  const docsIn = (parent) => SAMPLE_DOCUMENTS.filter(d => d.folder === (parent || "root"));
  const docCountDeep = (parent) => {
    let n = SAMPLE_DOCUMENTS.filter(d => d.folder === (parent || "root")).length;
    childFolders(parent).forEach(f => { n += docCountDeep(f.id); });
    return n;
  };

  const breadcrumb = React.useMemo(() => {
    const out = [];
    let id = currentFolder;
    while (id) { const f = folderById[id]; if (!f) break; out.unshift(f); id = f.parent; }
    return out;
  }, [currentFolder, folderById]);

  const toggleExp = (id) => {
    setExpanded(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const TreeNode = ({ folder, depth }) => {
    const kids = childFolders(folder.id);
    const isOpen = expanded.has(folder.id);
    const isCurrent = currentFolder === folder.id;
    const hue = getColor(folder.id);
    return (
      <div>
        <div
          className={"tree-row" + (isCurrent ? " current" : "")}
          style={{ paddingLeft: 8 + depth * 14 }}
          onClick={() => setCurrentFolder(folder.id)}>
          <button
            className="tree-tri"
            onClick={(e) => { e.stopPropagation(); if (kids.length) toggleExp(folder.id); }}
            style={{ visibility: kids.length ? "visible" : "hidden" }}>
            <Icon name={isOpen ? "tri-d" : "tri-r"} size={10}/>
          </button>
          <span className="tree-folder-icon" style={{ color: `oklch(0.55 0.13 ${hue})` }}>
            <Icon name="folder" size={14}/>
          </span>
          <span className="tree-label">{folder.name}</span>
          <span className="tree-count">{docCountDeep(folder.id)}</span>
        </div>
        {isOpen && kids.map(k => <TreeNode key={k.id} folder={k} depth={depth + 1}/>)}
      </div>
    );
  };

  const subFolders = childFolders(currentFolder);
  const docsHere = docsIn(currentFolder);
  const empty = !subFolders.length && !docsHere.length;

  const toggleSel = (id) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  return (
    <div className="docs-page">
      <aside className="docs-tree">
        <div className="docs-tree-head">
          <span className="muted" style={{ fontSize: 10.5, letterSpacing: ".06em", textTransform: "uppercase", fontWeight: 600 }}>Папки</span>
          <button className="btn xs ghost" title="Создать папку"><Icon name="folder-plus" size={13}/></button>
        </div>
        <div
          className={"tree-row" + (currentFolder === null ? " current" : "")}
          style={{ paddingLeft: 8 }}
          onClick={() => setCurrentFolder(null)}>
          <span className="tree-tri" style={{ visibility: "hidden" }}/>
          <span className="tree-folder-icon" style={{ color: "var(--ink-3)" }}>
            <Icon name="home" size={13}/>
          </span>
          <span className="tree-label">Все документы</span>
          <span className="tree-count">{SAMPLE_DOCUMENTS.length}</span>
        </div>
        <div className="tree-divider"/>
        {SAMPLE_FOLDERS.filter(f => f.parent === null).map(f => (
          <TreeNode key={f.id} folder={f} depth={0}/>
        ))}
      </aside>

      <div className="docs-main">
        <div className="page-head">
          <div style={{ minWidth: 0 }}>
            <div className="crumb">
              <Icon name="home" size={12}/>
              <span onClick={() => go("home")} style={{ cursor: "pointer" }}>Главная</span>
              <Icon name="chev-right" size={11}/>
              <span onClick={() => setCurrentFolder(null)} style={{ cursor: "pointer" }}>
                Мои документы
              </span>
              {breadcrumb.map(f => (
                <React.Fragment key={f.id}>
                  <Icon name="chev-right" size={11}/>
                  <span
                    onClick={() => setCurrentFolder(f.id)}
                    style={{ cursor: "pointer" }}>{f.name}</span>
                </React.Fragment>
              ))}
            </div>
            <h1>{currentFolder ? folderById[currentFolder].name : "Мои документы"}</h1>
            <p>{currentFolder
              ? `${docCountDeep(currentFolder)} документов · ${subFolders.length} вложенных папок`
              : "Документы и папки. Перетаскивайте, чтобы упорядочить."}</p>
          </div>
          <div className="page-head-actions">
            <div className="seg">
              <button className={"seg-b" + (view === "list" ? " active" : "")} onClick={() => setView("list")} title="Список"><Icon name="list-ul" size={14}/></button>
              <button className={"seg-b" + (view === "grid" ? " active" : "")} onClick={() => setView("grid")} title="Плитка"><Icon name="templates" size={14}/></button>
            </div>
            <button className="btn"><Icon name="folder-plus" size={14}/> Папка</button>
            <button className="btn primary" onClick={() => go("workspace")}><Icon name="plus" size={14}/> Создать документ</button>
          </div>
        </div>

        {selected.size > 0 && (
          <div className="bulk-bar">
            <span className="bulk-count">Выбрано: {selected.size}</span>
            <button className="btn xs"><Icon name="move" size={12}/> Переместить</button>
            <button className="btn xs"><Icon name="download" size={12}/> Скачать</button>
            <button className="btn xs danger"><Icon name="trash" size={12}/> Удалить</button>
            <button className="btn xs ghost" onClick={() => setSelected(new Set())} style={{ marginLeft: "auto" }}>Снять выбор</button>
          </div>
        )}

        {view === "grid" ? (
          <div className="docs-grid">
            {currentFolder && (
              <div className="folder-card up" onClick={() => setCurrentFolder(folderById[currentFolder].parent)}>
                <Icon name="folder-up" size={20}/>
                <div className="fc-name">…на уровень выше</div>
              </div>
            )}
            {subFolders.map(f => {
              const hue = getColor(f.id);
              return (
                <div key={f.id} className="folder-card" onClick={() => setCurrentFolder(f.id)}>
                  <div className="fc-icon" style={{ background: `oklch(0.94 0.04 ${hue})`, color: `oklch(0.4 0.15 ${hue})` }}>
                    <Icon name="folder" size={22}/>
                  </div>
                  <div className="fc-name">{f.name}</div>
                  <div className="fc-meta">{docCountDeep(f.id)} док. · {childFolders(f.id).length} папок</div>
                </div>
              );
            })}
            {docsHere.map(d => (
              <div key={d.id} className="doc-card" onClick={() => go("workspace")}>
                <div className="dc-icon" style={{ background: d.status === "complete" ? "var(--accent-green-soft)" : "var(--brand-soft)", color: d.status === "complete" ? "oklch(0.42 0.12 150)" : "var(--brand-ink)" }}>
                  <Icon name={d.status === "complete" ? "check" : "doc"} size={16}/>
                </div>
                <div className="dc-name">{d.name}</div>
                <div className="dc-tpl">{d.template}</div>
                <div className="dc-foot">
                  <span className="muted tabular" style={{ fontSize: 11 }}>{d.updated}</span>
                  <span className="muted tabular" style={{ fontSize: 11 }}>{d.progress}%</span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="tbl">
            <table>
              <thead><tr>
                <th style={{ width: 36 }}><input type="checkbox" onChange={(e) => {
                  if (e.target.checked) {
                    const s = new Set();
                    subFolders.forEach(f => s.add("f:" + f.id));
                    docsHere.forEach(d => s.add(d.id));
                    setSelected(s);
                  } else setSelected(new Set());
                }}/></th>
                <th style={{ width: "36%" }}>Название</th>
                <th>Тип / Шаблон</th>
                <th>Прогресс</th>
                <th>Автор</th>
                <th>Обновлён</th>
                <th style={{ width: 130 }}></th>
              </tr></thead>
              <tbody>
                {currentFolder && (
                  <tr className="row-up" onClick={() => setCurrentFolder(folderById[currentFolder].parent)}>
                    <td></td>
                    <td>
                      <div style={{ display: "flex", gap: 10, alignItems: "center", color: "var(--ink-3)" }}>
                        <Icon name="folder-up" size={16}/>
                        <span>… на уровень выше</span>
                      </div>
                    </td>
                    <td colSpan={5} className="muted">{folderById[folderById[currentFolder].parent]?.name || "Мои документы"}</td>
                  </tr>
                )}
                {subFolders.map(f => {
                  const hue = getColor(f.id);
                  const sel = selected.has("f:" + f.id);
                  return (
                    <tr key={f.id} className={"row-folder" + (sel ? " selected" : "")}>
                      <td onClick={(e) => e.stopPropagation()}>
                        <input type="checkbox" checked={sel} onChange={() => toggleSel("f:" + f.id)}/>
                      </td>
                      <td onClick={() => setCurrentFolder(f.id)}>
                        <div style={{ display: "flex", gap: 10, alignItems: "center", cursor: "pointer" }}>
                          <span style={{ width: 28, height: 28, borderRadius: 6, background: `oklch(0.94 0.04 ${hue})`, color: `oklch(0.4 0.15 ${hue})`, display: "grid", placeItems: "center" }}>
                            <Icon name="folder" size={15}/>
                          </span>
                          <span style={{ fontWeight: 500 }}>{f.name}</span>
                        </div>
                      </td>
                      <td className="muted">Папка · {childFolders(f.id).length} вложенных</td>
                      <td className="muted">—</td>
                      <td className="muted">—</td>
                      <td className="muted tabular">—</td>
                      <td>
                        <div className="row-actions">
                          <span className="muted tabular" style={{ fontSize: 12 }}>{docCountDeep(f.id)} док.</span>
                          <button className="btn xs icon-only" onClick={(e) => e.stopPropagation()}><Icon name="more-h" size={14}/></button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {docsHere.map(d => {
                  const sel = selected.has(d.id);
                  return (
                    <tr key={d.id} className={sel ? "selected" : ""}>
                      <td><input type="checkbox" checked={sel} onChange={() => toggleSel(d.id)}/></td>
                      <td>
                        <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                          <div style={{ width: 28, height: 28, borderRadius: 6, background: d.status === "complete" ? "var(--accent-green-soft)" : "var(--brand-soft)", color: d.status === "complete" ? "oklch(0.42 0.12 150)" : "var(--brand-ink)", display: "grid", placeItems: "center" }}>
                            <Icon name={d.status === "complete" ? "check" : "doc"} size={14}/>
                          </div>
                          <span style={{ fontWeight: 500, cursor: "pointer" }} onClick={() => go("workspace")}>{d.name}</span>
                        </div>
                      </td>
                      <td className="muted">{d.template}</td>
                      <td>
                        <div style={{ display: "flex", flexDirection: "column", gap: 4, width: 140 }}>
                          <div style={{ fontSize: 11, display: "flex", justifyContent: "space-between" }}>
                            <span className="muted">{d.status === "complete" ? "Готово" : "Черновик"}</span>
                            <span className="tabular muted">{d.progress}%</span>
                          </div>
                          <div className="q-progress" style={{ marginTop: 0 }}><div style={{ width: d.progress + "%", background: d.status === "complete" ? "var(--accent-green)" : "var(--brand)" }}/></div>
                        </div>
                      </td>
                      <td className="muted">{d.author}</td>
                      <td className="muted tabular">{d.updated}</td>
                      <td>
                        <div className="row-actions">
                          <button className="btn xs" onClick={() => go("workspace")}>Открыть</button>
                          <button className="btn xs icon-only"><Icon name="download" size={13}/></button>
                          <button className="btn xs icon-only"><Icon name="more-h" size={14}/></button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {empty && (
                  <tr><td colSpan={7}>
                    <div className="empty-folder">
                      <Icon name="folder" size={28}/>
                      <div>Пусто. Создайте документ или вложенную папку.</div>
                    </div>
                  </td></tr>
                )}
              </tbody>
            </table>
            <div className="tbl-foot">
              <span>Папок: {subFolders.length} · Документов: {docsHere.length}</span>
              <span className="muted">20 / стр.</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

const Directories = ({ go, openDir }) => (
  <div className="page">
    <div className="page-head">
      <div>
        <div className="crumb"><Icon name="home" size={12}/> <span onClick={()=>go("home")} style={{cursor:"pointer"}}>Главная</span> <Icon name="chev-right" size={11}/> <span>Справочники</span></div>
        <h1>Справочники</h1>
        <p>Структурированные данные для повторного использования в шаблонах: города, организации, сотрудники.</p>
      </div>
      <div className="page-head-actions">
        <button className="btn primary"><Icon name="plus" size={14}/> Создать справочник</button>
      </div>
    </div>
    <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14 }}>
      {SAMPLE_DIRECTORIES.map(d => (
        <div key={d.id} className="card hover pad" onClick={openDir} style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
            <div style={{ width: 32, height: 32, borderRadius: 8, background: "var(--bg-soft)", color: "var(--ink-2)", display: "grid", placeItems: "center", border: "1px solid var(--line)" }}>
              <Icon name="book" size={16}/>
            </div>
            <Icon name="chev-right" size={14}/>
          </div>
          <div style={{ fontSize: 15, fontWeight: 550 }}>{d.name}</div>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11.5, color: "var(--ink-3)" }}>
            <span><b className="tabular" style={{ color: "var(--ink-1)", fontWeight: 600 }}>{d.count}</b> записей</span>
            <span>{d.fields} полей</span>
            <span>{d.updated}</span>
          </div>
        </div>
      ))}
    </div>
  </div>
);

const DirectoryDetail = ({ go }) => {
  const cities = [
    { name: "Москва", code: "495", region: "Москва", okato: "45000000" },
    { name: "Санкт-Петербург", code: "812", region: "Санкт-Петербург", okato: "40000000" },
    { name: "Казань", code: "843", region: "Республика Татарстан", okato: "92401000" },
    { name: "Новосибирск", code: "383", region: "Новосибирская область", okato: "50401000" },
    { name: "Екатеринбург", code: "343", region: "Свердловская область", okato: "65401000" },
    { name: "Нижний Новгород", code: "831", region: "Нижегородская область", okato: "22401000" },
  ];
  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="crumb">
            <Icon name="chev-left" size={12}/>
            <span onClick={()=>go("directories")} style={{cursor:"pointer"}}>Справочники</span>
          </div>
          <h1>Города</h1>
          <p>145 записей · 4 поля · обновлено 12.03.2026</p>
        </div>
        <div className="page-head-actions">
          <button className="btn"><Icon name="upload" size={14}/> Импорт CSV</button>
          <button className="btn"><Icon name="settings" size={14}/> Поля</button>
          <button className="btn primary"><Icon name="plus" size={14}/> Добавить запись</button>
        </div>
      </div>
      <div className="tbl">
        <table>
          <thead><tr>
            <th>Название</th>
            <th>Телефонный код</th>
            <th>Регион</th>
            <th>ОКАТО</th>
            <th style={{ width: 110 }}></th>
          </tr></thead>
          <tbody>
            {cities.map(c => (
              <tr key={c.name}>
                <td><b>{c.name}</b></td>
                <td className="tabular">{c.code}</td>
                <td className="muted">{c.region}</td>
                <td className="tabular muted">{c.okato}</td>
                <td><div className="row-actions">
                  <button className="btn xs"><Icon name="edit" size={12}/></button>
                  <button className="btn xs danger"><Icon name="trash" size={12}/></button>
                </div></td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="tbl-foot">
          <span>Показано 6 из 145</span>
          <span className="muted">20 / стр.</span>
        </div>
      </div>
    </div>
  );
};
