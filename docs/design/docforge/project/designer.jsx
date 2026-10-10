// designer.jsx — Template Designer

const TBBtn = ({ icon, label, active, onClick, title }) => (
  <button
    className={"tb-btn" + (label ? " lbl" : "") + (active ? " active" : "")}
    onClick={onClick} title={title || label}>
    {icon && <Icon name={icon} size={14}/>}
    {label && <span>{label}</span>}
  </button>
);

const Toolbar = ({ marks, toggleMark, onPickFont, fontFamily, fontSize, setFontSize, onInsert, openSearch }) => (
  <div className="toolbar">
    <select className="tb-select" value={fontFamily} onChange={e=>onPickFont(e.target.value)} style={{ width: 145 }}>
      <option>Times New Roman</option>
      <option>Source Serif 4</option>
      <option>Inter Tight</option>
      <option>JetBrains Mono</option>
    </select>
    <select className="tb-select" value={fontSize} onChange={e=>setFontSize(e.target.value)} style={{ width: 60 }}>
      {["10","11","12","13","14","16","18","20","24"].map(s => <option key={s}>{s}</option>)}
    </select>
    <span className="sep"/>
    <TBBtn icon="bold" active={marks.bold} onClick={()=>toggleMark("bold")} title="Полужирный · ⌘B"/>
    <TBBtn icon="italic" active={marks.italic} onClick={()=>toggleMark("italic")} title="Курсив · ⌘I"/>
    <TBBtn icon="under" active={marks.under} onClick={()=>toggleMark("under")} title="Подчёркнутый · ⌘U"/>
    <TBBtn icon="strike" active={marks.strike} onClick={()=>toggleMark("strike")} title="Зачёркнутый"/>
    <span className="sep"/>
    <TBBtn icon="color" title="Цвет текста"/>
    <TBBtn icon="highlight" title="Подсветка"/>
    <span className="sep"/>
    <TBBtn icon="align-l" active={marks.align === "left"} onClick={()=>toggleMark("align","left")}/>
    <TBBtn icon="align-c" active={marks.align === "center"} onClick={()=>toggleMark("align","center")}/>
    <TBBtn icon="align-r" active={marks.align === "right"} onClick={()=>toggleMark("align","right")}/>
    <span className="sep"/>
    <TBBtn icon="list-ul" title="Маркированный список"/>
    <TBBtn icon="list-ol" title="Нумерованный список"/>
    <TBBtn icon="indent-l" title="Уменьшить отступ"/>
    <TBBtn icon="indent-r" title="Увеличить отступ"/>
    <span className="sep"/>
    <TBBtn icon="table" title="Вставить таблицу"/>
    <TBBtn icon="image" title="Вставить изображение"/>
    <TBBtn icon="link" title="Ссылка"/>
    <TBBtn icon="page-break" title="Разрыв страницы" onClick={()=>onInsert("pagebreak")}/>
    <span className="sep"/>
    <TBBtn icon="param" label="Параметр" onClick={()=>onInsert("param")}/>
    <TBBtn icon="branch" label="Условие" onClick={()=>onInsert("cond")}/>
    <TBBtn icon="hash" label="Пункт" onClick={()=>onInsert("clause")}/>
    <span className="sep"/>
    <TBBtn icon="search" onClick={openSearch} title="Найти и заменить · ⌘F"/>
  </div>
);

const RentDoc = ({ activeChip, setActiveChip, published }) => {
  const C = ({ id, label, type = "param" }) => (
    <span
      className={"chip chip-" + type + (activeChip === id ? " active" : "")}
      onClick={() => !published && setActiveChip(id)}>
      {label}
    </span>
  );
  return (
    <div className="doc-page">
      <div className="doc-title">ДОГОВОР АРЕНДЫ НЕЖИЛОГО ПОМЕЩЕНИЯ № <C id="contract_no" label="№ договора"/></div>
      <div className="doc-sub">г. <C id="city" label="Город"/>, <C id="contract_date" label="Дата договора"/></div>

      <p>
        <C id="landlord_name" label="ООО «Северные окна»"/> (ИНН&nbsp;<C id="landlord_inn" label="7806543210"/>),
        именуемое в&nbsp;дальнейшем «Арендодатель», в&nbsp;лице
        <C id="landlord_repr" label="Генерального директора Лебедева А.В."/>,
        действующего на&nbsp;основании Устава, с&nbsp;одной стороны, и&nbsp;
        <C id="tenant_name" label="ООО «Карелия-Трейд»"/>,
        именуемое в&nbsp;дальнейшем «Арендатор», в&nbsp;лице
        <C id="tenant_repr" label="Орлова Дмитрия Сергеевича"/>, действующего
        на&nbsp;основании доверенности №&nbsp;7 от&nbsp;14.03.2026, с&nbsp;другой стороны,
        заключили настоящий Договор о&nbsp;нижеследующем.
      </p>

      <div className="clause">
        <span className="clause-num">§&nbsp;1.&nbsp;Предмет договора.</span>{" "}
        Арендодатель передаёт, а Арендатор принимает во временное владение и пользование нежилое помещение,
        расположенное по&nbsp;адресу:{" "}
        <C id="premise_addr" label="г. Санкт-Петербург, ул. Гороховая, д. 47, помещ. 12-Н"/>,
        общей площадью <C id="premise_area" label="84,5"/> кв.м (далее — «Помещение»).
      </div>

      <div className="cond-block" data-cond="если с мебелью = да">
        <p style={{ margin: 0 }}>
          §&nbsp;1.1. Помещение передаётся Арендатору с&nbsp;комплектом мебели и&nbsp;офисного оборудования
          согласно Приложению&nbsp;№&nbsp;2 (опись имущества). Арендатор обязуется обеспечить сохранность переданного
          имущества и&nbsp;возвратить его в&nbsp;состоянии, соответствующем естественному износу.
        </p>
      </div>

      <div className="clause">
        <span className="clause-num">§&nbsp;2.&nbsp;Срок аренды.</span>{" "}
        Договор заключён на&nbsp;срок <C id="term_months" label="11"/> (<C id="term_words" label="одиннадцать"/>) месяцев,
        начиная с&nbsp;<C id="start_date" label="01.05.2026"/>.{" "}
        <span className="chip chip-cond-inline">если срок &gt;= 12 → требуется регистрация</span>
      </div>

      <div className="clause">
        <span className="clause-num">§&nbsp;3.&nbsp;Арендная плата.</span>{" "}
        Размер ежемесячной арендной платы составляет
        {" "}<C id="rent_amount" label="180 000"/> (<C id="rent_words" label="сто восемьдесят тысяч"/>) ₽,
        включая НДС. Оплата производится не&nbsp;позднее{" "}
        <C id="pay_day" label="5-го"/> числа текущего месяца на&nbsp;расчётный счёт Арендодателя.
      </div>

      <div className="clause">
        <span className="clause-num">§&nbsp;4.&nbsp;Обеспечительный платёж.</span>{" "}
        Арендатор вносит обеспечительный платёж в&nbsp;размере{" "}
        <C id="deposit_months" label="2"/>-месячной арендной платы в&nbsp;течение 5&nbsp;(пяти)
        банковских дней с&nbsp;даты подписания настоящего Договора. См.&nbsp;также{" "}
        <C id="ref_clause_3" label="§ 3 «Арендная плата»" type="clauseref"/>.
      </div>

      <div className="clause">
        <span className="clause-num">§&nbsp;5.&nbsp;Права и обязанности сторон.</span>{" "}
        <C id="phr_rights" label="Стандартный набор прав сторон" type="phrasing"/>.
        Арендатор вправе использовать Помещение исключительно в&nbsp;целях, предусмотренных{" "}
        <C id="purpose" label="ведения офисной деятельности"/>.
      </div>

      <div className="page-break">Разрыв страницы</div>
    </div>
  );
};

const ParamsTab = ({ params, activeChip, setActiveChip }) => (
  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
      <span style={{ fontSize: 12, color: "var(--ink-3)" }}>Параметров: <b className="tabular" style={{ color: "var(--ink-1)" }}>{params.length}</b></span>
      <button className="btn xs"><Icon name="plus" size={12}/> Параметр</button>
    </div>
    <div className="input-group">
      <span className="icon"><Icon name="search" size={13}/></span>
      <input placeholder="Поиск параметра"/>
    </div>
    {params.map(p => (
      <div
        key={p.code}
        className={"param-card" + (activeChip === p.code ? " active" : "")}
        onClick={() => setActiveChip(p.code)}>
        <div className="pc-head">
          <span className="pc-label">{p.label}</span>
          {p.required && <span className="tag amber">обяз.</span>}
        </div>
        <div className="pc-meta">
          <span className="pc-code">{p.code}</span>
          <span>·</span>
          <span>{p.type}</span>
        </div>
      </div>
    ))}
  </div>
);

const QuestionnaireTab = () => {
  const [page, setPage] = React.useState(2);
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <span style={{ fontSize: 12, color: "var(--ink-3)" }}>Страниц: 4 · Вопросов: 12</span>
        <button className="btn xs"><Icon name="plus" size={12}/> Страница</button>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 4, marginBottom: 14 }}>
        {QUESTIONNAIRE_PAGES.map(qp => (
          <div key={qp.i}
               onClick={() => setPage(qp.i)}
               className={"q-card" + (page === qp.i ? " active" : "")}
               style={{ cursor: "pointer", padding: "10px 12px", marginBottom: 0, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <span style={{ width: 22, height: 22, borderRadius: 6, background: page === qp.i ? "var(--brand)" : "var(--bg-soft)", color: page === qp.i ? "white" : "var(--ink-2)", fontSize: 11, fontWeight: 600, display: "grid", placeItems: "center" }}>{qp.i}</span>
              <span style={{ fontSize: 13, fontWeight: 500 }}>{qp.t}</span>
            </div>
            <span className="muted" style={{ fontSize: 11.5 }}>{RENT_QUESTIONS.filter(q=>q.page===qp.i).length} вопр.</span>
          </div>
        ))}
      </div>
      <div style={{ fontSize: 11, color: "var(--ink-3)", letterSpacing: "0.05em", textTransform: "uppercase", fontWeight: 600, marginBottom: 8 }}>Вопросы — стр. {page}</div>
      {RENT_QUESTIONS.filter(q => q.page === page).map((q, i) => (
        <div key={q.id} className="q-card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span className="q-num">Q{q.id.slice(1)} · {q.type}</span>
            <button className="tb-btn" style={{ width: 22, height: 22 }}><Icon name="more-h" size={12}/></button>
          </div>
          <div className="q-label">{q.label}</div>
          <div className="pc-meta"><span className="pc-code">{q.param}</span></div>
        </div>
      ))}
    </div>
  );
};

const PhrasingsTab = () => {
  const phrs = [
    { name: "Стандартный набор прав сторон", count: 3 },
    { name: "Ответственность за нарушение сроков", count: 4 },
    { name: "Форс-мажор", count: 2 },
    { name: "Подсудность споров", count: 3 },
  ];
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <span style={{ fontSize: 12, color: "var(--ink-3)" }}>Фраз: 4</span>
        <button className="btn xs"><Icon name="plus" size={12}/> Фраза</button>
      </div>
      {phrs.map(p => (
        <div key={p.name} className="param-card" style={{ marginBottom: 8 }}>
          <div className="pc-head">
            <span className="pc-label">{p.name}</span>
            <Icon name="chev-right" size={12}/>
          </div>
          <div className="pc-meta">
            <span className="tag" style={{ background: "oklch(0.96 0.045 320)", color: "oklch(0.42 0.13 320)", borderColor: "oklch(0.85 0.06 320)" }}>{p.count} вариантов</span>
          </div>
        </div>
      ))}
    </div>
  );
};

const ScriptsTab = () => (
  <div className="empty">
    <div className="ill"><Icon name="code" size={22}/></div>
    <div style={{ fontSize: 13.5, fontWeight: 500, color: "var(--ink-1)", marginBottom: 4 }}>Скриптов пока нет</div>
    <div style={{ fontSize: 12, color: "var(--ink-3)", textAlign: "center", maxWidth: 220 }}>
      Скрипты позволят вычислять значения параметров на&nbsp;лету. Скоро появятся в&nbsp;DocForge.
    </div>
  </div>
);

const ContextMenu = ({ x, y, onClose, onAction }) => (
  <>
    <div style={{ position: "fixed", inset: 0, zIndex: 240 }} onClick={onClose} onContextMenu={(e)=>{e.preventDefault();onClose();}}/>
    <div className="ctx-menu" style={{ left: x, top: y }}>
      <div className="ctx-item" onClick={()=>onAction("param")}><Icon name="param" size={14}/> Вставить параметр <span className="ctx-meta">⌘P</span></div>
      <div className="ctx-item" onClick={()=>onAction("cond")}><Icon name="branch" size={14}/> Вставить условный блок</div>
      <div className="ctx-item" onClick={()=>onAction("clause")}><Icon name="hash" size={14}/> Вставить нумерованный пункт</div>
      <div className="ctx-sep"/>
      <div className="ctx-item">Обернуть в условный inline</div>
      <div className="ctx-item">Вставить ссылку на пункт</div>
      <div className="ctx-sep"/>
      <div className="ctx-item">Копировать <span className="ctx-meta">⌘C</span></div>
      <div className="ctx-item">Вставить <span className="ctx-meta">⌘V</span></div>
    </div>
  </>
);

const SlashMenu = () => (
  <div className="slash-menu" style={{ top: 220, left: 280 }}>
    <div style={{ padding: "6px 8px", fontSize: 11, color: "var(--ink-3)", letterSpacing: "0.05em", textTransform: "uppercase", fontWeight: 600 }}>Вставить</div>
    <div className="slash-item active"><div className="slash-i"><Icon name="param" size={14}/></div> Параметр <span className="slash-meta">/par</span></div>
    <div className="slash-item"><div className="slash-i"><Icon name="branch" size={14}/></div> Условный блок <span className="slash-meta">/if</span></div>
    <div className="slash-item"><div className="slash-i"><Icon name="hash" size={14}/></div> Нумерованный пункт <span className="slash-meta">/§</span></div>
    <div className="slash-item"><div className="slash-i"><Icon name="table" size={14}/></div> Таблица <span className="slash-meta">/tbl</span></div>
    <div className="slash-item"><div className="slash-i"><Icon name="image" size={14}/></div> Изображение <span className="slash-meta">/img</span></div>
    <div className="slash-item"><div className="slash-i"><Icon name="page-break" size={14}/></div> Разрыв страницы <span className="slash-meta">/pb</span></div>
  </div>
);

const SearchPanel = ({ onClose }) => (
  <div className="search-panel">
    <div className="input-group" style={{ height: 28, width: 220 }}>
      <span className="icon"><Icon name="search" size={13}/></span>
      <input placeholder="Найти" defaultValue="арендатор"/>
    </div>
    <span style={{ fontSize: 11.5, color: "var(--ink-3)" }}><b>3</b> совпадения</span>
    <div className="input-group" style={{ height: 28, width: 220 }}>
      <span className="icon"><Icon name="edit" size={12}/></span>
      <input placeholder="Заменить на"/>
    </div>
    <button className="btn xs icon-only"><Icon name="chev-left" size={12}/></button>
    <button className="btn xs icon-only"><Icon name="chev-right" size={12}/></button>
    <button className="btn xs">Заменить</button>
    <button className="btn xs">Заменить всё</button>
    <button className="btn xs ghost icon-only" style={{ marginLeft: "auto" }} onClick={onClose}><Icon name="x" size={13}/></button>
  </div>
);

const ScrollableTabs = ({ items, activeKey, onChange }) => {
  const scRef = React.useRef(null);
  const [overflows, setOverflows] = React.useState(false);
  const [canL, setCanL] = React.useState(false);
  const [canR, setCanR] = React.useState(false);

  const recheck = React.useCallback(() => {
    const el = scRef.current; if (!el) return;
    const ov = el.scrollWidth > el.clientWidth + 1;
    setOverflows(ov);
    setCanL(el.scrollLeft > 1);
    setCanR(el.scrollLeft < el.scrollWidth - el.clientWidth - 1);
  }, []);

  React.useEffect(() => {
    recheck();
    const el = scRef.current; if (!el) return;
    const ro = new ResizeObserver(recheck); ro.observe(el);
    el.addEventListener("scroll", recheck);
    return () => { ro.disconnect(); el.removeEventListener("scroll", recheck); };
  }, [recheck, items.length]);

  React.useEffect(() => {
    const el = scRef.current; if (!el) return;
    const active = el.querySelector(".tab.active");
    if (active && active.scrollIntoView) {
      const r = active.getBoundingClientRect();
      const er = el.getBoundingClientRect();
      if (r.left < er.left || r.right > er.right) {
        el.scrollTo({ left: active.offsetLeft - 12, behavior: "smooth" });
      }
    }
  }, [activeKey]);

  const nudge = (dir) => {
    const el = scRef.current; if (!el) return;
    el.scrollBy({ left: dir * 120, behavior: "smooth" });
  };

  return (
    <div className="tabs">
          <div className="tabs-scroller" ref={scRef}>
            {items.map(([k, l]) => (
              <div key={k} className={"tab" + (activeKey === k ? " active" : "")} onClick={() => onChange(k)}>{l}</div>
            ))}
          </div>
      {overflows && (
        <div className="tabs-nav">
          <button disabled={!canL} onClick={() => nudge(-1)} title="Назад"><Icon name="chev-left" size={13}/></button>
          <button disabled={!canR} onClick={() => nudge(1)} title="Вперёд"><Icon name="chev-right" size={13}/></button>
        </div>
      )}
    </div>
  );
};

const TemplateDesigner = ({ go, openExport, openPageSettings, published, setPublished, hasUnsaved, setUnsaved }) => {
  const [tab, setTab] = React.useState("params");
  const [activeChip, setActiveChip] = React.useState("rent_amount");
  const [marks, setMarks] = React.useState({ bold: false, italic: false, under: false, strike: false, align: "left" });
  const [fontFamily, setFontFamily] = React.useState("Times New Roman");
  const [fontSize, setFontSize] = React.useState("12");
  const [showSearch, setShowSearch] = React.useState(false);
  const [showSlash, setShowSlash] = React.useState(false);
  const [ctx, setCtx] = React.useState(null);
  const [sideW, setSideW] = React.useState(340);
  const dragRef = React.useRef(null);

  const startDrag = (e) => {
    e.preventDefault();
    const startX = e.clientX;
    const startW = sideW;
    const onMove = (ev) => {
      const delta = startX - ev.clientX;
      const next = Math.max(260, Math.min(560, startW + delta));
      setSideW(next);
    };
    const onUp = () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
      if (dragRef.current) dragRef.current.classList.remove("dragging");
    };
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    if (dragRef.current) dragRef.current.classList.add("dragging");
  };

  const toggle = (k, v) => {
    setMarks(m => v != null ? { ...m, [k]: v } : { ...m, [k]: !m[k] });
    setUnsaved(true);
  };

  return (
    <div className="designer" onClick={() => setShowSlash(false)}>
      <div className="designer-bar">
        <button className="icon-btn" onClick={() => go("templates")} title="Назад"><Icon name="chev-left" size={16}/></button>
        <h2>Договор аренды нежилого помещения</h2>
        <span className="tag" style={{ flexShrink: 0 }}>v2.3</span>
        {hasUnsaved && !published && (
          <span className="unsaved" title="Несохранённые изменения"><Icon name="alert" size={11}/> Не сохранено</span>
        )}
        {published && <span className="tag green"><span className="dot"/>Опубликован</span>}
        <div className="actions">
          <div className="avatar-stack">
            {PARTICIPANTS.slice(0,2).map(p => (
              <div key={p.name} className="avatar sm" style={{ background: `linear-gradient(135deg, oklch(0.6 0.14 ${p.hue}), oklch(0.55 0.16 ${(p.hue+30)%360}))` }} title={p.name}>{p.initials}</div>
            ))}
          </div>
          <span className="sep" style={{ width: 1, height: 18, background: "var(--line)", margin: "0 4px" }}/>
          <button className="btn sm icon-only" onClick={() => setShowSearch(s => !s)} title="Найти и заменить"><Icon name="search" size={13}/></button>
          <button className="btn sm icon-only" onClick={openPageSettings} title="Параметры страницы"><Icon name="settings" size={13}/></button>
          <button className="btn sm icon-only" onClick={openExport} title="Экспорт DOCX/PDF"><Icon name="download" size={13}/></button>
          {!published
            ? <>
                <button className="btn sm primary" disabled={!hasUnsaved} onClick={() => setUnsaved(false)}><Icon name="save" size={13}/> Сохранить</button>
                <button className="btn sm success icon-only" onClick={() => setPublished(true)} title="Опубликовать шаблон"><Icon name="publish" size={13}/></button>
              </>
            : <button className="btn sm danger icon-only" onClick={() => setPublished(false)} title="Снять с публикации"><Icon name="lock" size={13}/></button>}
        </div>
      </div>
      {published && (
        <div className="banner">
          <Icon name="info" size={14}/>
          <span><b className="b-strong">Шаблон опубликован.</b> Редактирование заблокировано — все документы, созданные после, наследуют эту версию.</span>
          <button className="btn xs" onClick={() => setPublished(false)}>Снять с публикации</button>
        </div>
      )}
      <div className="designer-body" style={{ "--side-w": sideW + "px" }}>
        <div className="editor-pane">
          <Toolbar
            marks={marks} toggleMark={toggle}
            onPickFont={setFontFamily} fontFamily={fontFamily}
            fontSize={fontSize} setFontSize={setFontSize}
            onInsert={()=>setUnsaved(true)}
            openSearch={()=>setShowSearch(s=>!s)}
          />
          {showSearch && <SearchPanel onClose={()=>setShowSearch(false)}/>}
          <div className="editor-canvas"
               onContextMenu={(e)=>{ e.preventDefault(); if(!published) setCtx({x: e.clientX, y: e.clientY}); }}
               onDoubleClick={(e)=>{ if(!published) { e.stopPropagation(); setShowSlash(s => !s); }}}>
            <div style={{ position: "relative" }}>
              <RentDoc activeChip={activeChip} setActiveChip={setActiveChip} published={published}/>
              {showSlash && !published && <SlashMenu/>}
            </div>
          </div>
        </div>
        <div
          ref={dragRef}
          className="side-resizer"
          onMouseDown={startDrag}
          title="Потяните, чтобы изменить размер"
        />
        <div className="designer-side">
          <ScrollableTabs
            items={[["params","Параметры"],["q","Опросный лист"],["phrasings","Формулировки"],["scripts","Скрипты"]]}
            activeKey={tab}
            onChange={setTab}
          />
          <div className="tab-body">
            {tab === "params" && <ParamsTab params={RENT_PARAMS} activeChip={activeChip} setActiveChip={setActiveChip}/>}
            {tab === "q" && <QuestionnaireTab/>}
            {tab === "phrasings" && <PhrasingsTab/>}
            {tab === "scripts" && <ScriptsTab/>}
          </div>
        </div>
      </div>
      {ctx && <ContextMenu x={ctx.x} y={ctx.y} onClose={()=>setCtx(null)} onAction={()=>{ setCtx(null); setUnsaved(true); }}/>}
    </div>
  );
};
