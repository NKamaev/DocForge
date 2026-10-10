// workspace.jsx — Document Workspace (questionnaire fill)

const ParticipantsPanel = () => (
  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
    <div className="avatar-stack">
      {PARTICIPANTS.map(p => (
        <div key={p.name} className="avatar sm"
             title={p.name + " — онлайн"}
             style={{ background: `linear-gradient(135deg, oklch(0.6 0.14 ${p.hue}), oklch(0.55 0.16 ${(p.hue+30)%360}))`, position: "relative" }}>
          {p.initials}
          <span style={{ position: "absolute", right: -1, bottom: -1, width: 8, height: 8, borderRadius: "50%", background: "var(--accent-green)", boxShadow: "0 0 0 1.5px var(--surface)" }}/>
        </div>
      ))}
    </div>
    <span style={{ fontSize: 12, color: "var(--ink-3)" }}>3 онлайн</span>
  </div>
);

const QField = ({ q, value, setValue, active, onFocus }) => {
  return (
    <div className={"q-card" + (active ? " active" : "")} onClick={onFocus} data-qid={q.id}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span className="q-num">Вопрос {q.id.slice(1)} {q.help && <span className="muted"> · {q.help}</span>}</span>
        {value && <Icon name="check" size={12} style={{ color: "var(--accent-green)" }}/>}
      </div>
      <div className="q-label">{q.label}</div>
      {q.type === "bool" ? (
        <div className="btn-group">
          <button className={"btn xs" + (value === "yes" ? " active" : "")} onClick={(e)=>{e.stopPropagation(); setValue("yes");}}>Да</button>
          <button className={"btn xs" + (value === "no" ? " active" : "")} onClick={(e)=>{e.stopPropagation(); setValue("no");}}>Нет</button>
        </div>
      ) : q.type === "select" ? (
        <select className="select" value={value || ""} onChange={(e)=>setValue(e.target.value)} onClick={(e)=>e.stopPropagation()}>
          <option value="">— Выберите —</option>
          {(q.options || []).map(o => <option key={o}>{o}</option>)}
        </select>
      ) : q.type === "date" ? (
        <div className="input-group">
          <span className="icon"><Icon name="calendar" size={13}/></span>
          <input value={value || ""} onChange={(e)=>setValue(e.target.value)} placeholder="ДД.ММ.ГГГГ" onClick={(e)=>e.stopPropagation()}/>
        </div>
      ) : (
        <input className="input" value={value || ""} onChange={(e)=>setValue(e.target.value)} onClick={(e)=>e.stopPropagation()}/>
      )}
      <div className="pc-meta" style={{ marginTop: 4 }}>
        <span className="pc-code">{q.param}</span>
        <span>·</span>
        <span style={{ color: "var(--ink-3)" }}>привязан к чипу в документе</span>
      </div>
    </div>
  );
};

const RevisionsDrawer = ({ onClose }) => (
  <>
    <div className="drawer-veil" onClick={onClose}/>
    <aside className="drawer">
      <div className="drawer-head">
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 550 }}>История изменений</h3>
        <button className="icon-btn" onClick={onClose}><Icon name="x" size={14}/></button>
      </div>
      <div className="drawer-body">
        {REVISIONS.map((r, i) => (
          <div key={r.id} style={{ paddingLeft: 22, position: "relative", paddingBottom: 18, borderLeft: i === REVISIONS.length-1 ? "none" : "1px dashed var(--line)" }}>
            <div style={{ position: "absolute", left: -5, top: 4, width: 10, height: 10, borderRadius: "50%", background: i === 0 ? "var(--brand)" : "var(--surface)", border: "2px solid var(--brand-line)" }}/>
            <div style={{ fontSize: 12, color: "var(--ink-3)" }}>{r.when} · {r.who}</div>
            <div style={{ fontSize: 13.5, fontWeight: 500, margin: "2px 0 6px" }}>{r.note}</div>
            <button className="btn xs">Восстановить</button>
          </div>
        ))}
      </div>
    </aside>
  </>
);

const PageSettingsDrawer = ({ onClose }) => (
  <>
    <div className="drawer-veil" onClick={onClose}/>
    <aside className="drawer">
      <div className="drawer-head">
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 550 }}>Параметры страницы</h3>
        <button className="icon-btn" onClick={onClose}><Icon name="x" size={14}/></button>
      </div>
      <div className="drawer-body" style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div className="field">
          <label className="field-lbl">Размер</label>
          <div className="btn-group">
            <button className="btn sm active">A4</button>
            <button className="btn sm">A3</button>
            <button className="btn sm">Letter</button>
          </div>
        </div>
        <div className="field">
          <label className="field-lbl">Ориентация</label>
          <div className="btn-group">
            <button className="btn sm active">Книжная</button>
            <button className="btn sm">Альбомная</button>
          </div>
        </div>
        <div className="field">
          <label className="field-lbl">Поля (мм)</label>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <div className="input-group" style={{ height: 32 }}><span style={{ fontSize: 11, color: "var(--ink-3)", marginRight: 6 }}>Верх</span><input defaultValue="20"/></div>
            <div className="input-group" style={{ height: 32 }}><span style={{ fontSize: 11, color: "var(--ink-3)", marginRight: 6 }}>Низ</span><input defaultValue="20"/></div>
            <div className="input-group" style={{ height: 32 }}><span style={{ fontSize: 11, color: "var(--ink-3)", marginRight: 6 }}>Лево</span><input defaultValue="30"/></div>
            <div className="input-group" style={{ height: 32 }}><span style={{ fontSize: 11, color: "var(--ink-3)", marginRight: 6 }}>Право</span><input defaultValue="15"/></div>
          </div>
        </div>
        <div className="field">
          <label className="field-lbl">Шрифт по умолчанию</label>
          <select className="select"><option>Times New Roman</option><option>Source Serif 4</option></select>
        </div>
        <div className="field">
          <label className="field-lbl">Размер · межстрочный</label>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <select className="select"><option>14 pt</option><option>12 pt</option></select>
            <select className="select"><option>1.5</option><option>1.0</option><option>2.0</option></select>
          </div>
        </div>
      </div>
    </aside>
  </>
);

const Workspace = ({ go, openHistory, openExport }) => {
  const [page, setPage] = React.useState(2);
  const [activeQ, setActiveQ] = React.useState("q5");
  const [answers, setAnswers] = React.useState(() => {
    const a = {};
    RENT_QUESTIONS.forEach(q => { a[q.id] = q.value; });
    return a;
  });

  const filledCount = Object.values(answers).filter(v => v != null && v !== "").length;
  const totalCount = RENT_QUESTIONS.length;
  const progress = Math.round(filledCount / totalCount * 100);

  // Map active question to a chip in the doc
  const qToChip = { q1: "landlord_name", q2: "landlord_inn", q3: "tenant_name", q4: "tenant_repr",
    q5: "premise_addr", q6: "premise_area", q7: "premise_addr",
    q8: "rent_amount", q9: "term_months", q10: "start_date", q11: "deposit_months", q12: "city" };

  const activeChip = qToChip[activeQ];

  const pageQuestions = RENT_QUESTIONS.filter(q => q.page === page);

  return (
    <div className="workspace">
      <div className="q-panel">
        <div className="q-head">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
            <button className="btn xs ghost" onClick={() => go("documents")}><Icon name="chev-left" size={12}/> Документы</button>
            <span className="tabular muted" style={{ fontSize: 11 }}>{filledCount} / {totalCount}</span>
          </div>
          <h3>Опросный лист · Аренда офис · ООО «Карелия»</h3>
          <div style={{ fontSize: 12, color: "var(--ink-3)" }}>На основе шаблона «Договор аренды нежилого помещения»</div>
          <div className="q-progress"><div style={{ width: progress + "%" }}/></div>
          <div className="steps" style={{ marginTop: 12 }}>
            {QUESTIONNAIRE_PAGES.map(qp => (
              <div key={qp.i}
                   className={"step" + (page === qp.i ? " active" : (page > qp.i ? " done" : ""))}
                   onClick={() => { setPage(qp.i); const first = RENT_QUESTIONS.find(q=>q.page===qp.i); if (first) setActiveQ(first.id); }}>
                <div className="step-i">ШАГ {qp.i}</div>
                <div className="step-t">{qp.t}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="q-body">
          <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--ink-3)", marginBottom: 8 }}>
            {QUESTIONNAIRE_PAGES.find(p=>p.i===page).t} · {pageQuestions.length} вопр.
          </div>
          {pageQuestions.map(q => (
            <QField
              key={q.id}
              q={q}
              value={answers[q.id]}
              setValue={(v) => setAnswers(a => ({ ...a, [q.id]: v }))}
              active={activeQ === q.id}
              onFocus={() => setActiveQ(q.id)}
            />
          ))}
        </div>
        <div className="q-foot">
          <button className="btn sm" disabled={page === 1} onClick={() => { const np = page - 1; setPage(np); const f = RENT_QUESTIONS.find(q=>q.page===np); if(f) setActiveQ(f.id); }}>
            <Icon name="chev-left" size={12}/> Назад
          </button>
          <span className="muted tabular" style={{ fontSize: 12 }}>Страница {page} из {QUESTIONNAIRE_PAGES.length}</span>
          {page < QUESTIONNAIRE_PAGES.length
            ? <button className="btn sm primary" onClick={() => { const np = page + 1; setPage(np); const f = RENT_QUESTIONS.find(q=>q.page===np); if(f) setActiveQ(f.id); }}>
                Далее <Icon name="chev-right" size={12}/>
              </button>
            : <button className="btn sm success">Готово <Icon name="check" size={12}/></button>}
        </div>
      </div>

      <div className="editor-pane">
        <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 16px", background: "var(--bg-soft)", borderBottom: "1px solid var(--line)" }}>
          <span style={{ fontSize: 12.5, color: "var(--ink-3)" }}>
            Кликните на чип, чтобы перейти к&nbsp;соответствующему вопросу слева. Документ обновляется в&nbsp;реальном времени.
          </span>
          <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 8 }}>
            <ParticipantsPanel/>
            <span style={{ width: 1, height: 18, background: "var(--line)" }}/>
            <button className="btn xs ghost" onClick={openHistory}><Icon name="history" size={13}/> История</button>
            <button className="btn xs ghost"><Icon name="speak" size={13}/> Комментарии</button>
            <span style={{ width: 1, height: 18, background: "var(--line)" }}/>
            <button className="btn xs"><Icon name="download" size={12}/> DOCX</button>
            <button className="btn xs primary" onClick={openExport}><Icon name="download" size={12}/> PDF</button>
          </div>
        </div>
        <div className="editor-canvas">
          <RentDoc activeChip={activeChip} setActiveChip={(id)=>{
            // map chip back to question
            const back = Object.entries(qToChip).find(([qid, c]) => c === id);
            if (back) setActiveQ(back[0]);
          }} published={false}/>
        </div>
      </div>
    </div>
  );
};
