// modals.jsx — Create modals + export menu

const Modal = ({ title, onClose, children, footer, width }) => (
  <>
    <div className="modal-veil" onClick={onClose}/>
    <div className="modal" style={{ width: width || 480, position: "fixed", left: "50%", top: "50%", transform: "translate(-50%, -50%)" }}>
      <div className="modal-head">
        <h3>{title}</h3>
        <button className="icon-btn" onClick={onClose}><Icon name="x" size={14}/></button>
      </div>
      <div className="modal-body">{children}</div>
      {footer && <div className="modal-foot">{footer}</div>}
    </div>
  </>
);

const CreateTemplateModal = ({ onClose, onCreate }) => (
  <Modal title="Новый шаблон" onClose={onClose}
    footer={<>
      <button className="btn" onClick={onClose}>Отмена</button>
      <button className="btn primary" onClick={onCreate}>Создать <Icon name="chev-right" size={12}/></button>
    </>}>
    <div className="field">
      <label className="field-lbl">Название <span style={{ color: "var(--accent-red)" }}>*</span></label>
      <input className="input" placeholder="Например: Договор поставки" autoFocus/>
    </div>
    <div className="field">
      <label className="field-lbl">Категория</label>
      <select className="select"><option>Договоры</option><option>Кадры</option><option>Доверенности</option><option>Претензии</option></select>
    </div>
    <div className="field">
      <label className="field-lbl">Описание</label>
      <textarea className="textarea" placeholder="Краткое описание для коллег"/>
    </div>
    <div className="field">
      <label className="field-lbl">Начать с</label>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <div className="card hover pad" style={{ padding: 12, borderColor: "var(--brand)", boxShadow: "0 0 0 3px oklch(from var(--brand) l c h / 0.12)" }}>
          <Icon name="doc" size={16}/>
          <div style={{ fontSize: 13, fontWeight: 500, marginTop: 6 }}>Пустого шаблона</div>
          <div style={{ fontSize: 11.5, color: "var(--ink-3)" }}>Чистая страница A4</div>
        </div>
        <div className="card hover pad" style={{ padding: 12 }}>
          <Icon name="upload" size={16}/>
          <div style={{ fontSize: 13, fontWeight: 500, marginTop: 6 }}>Импорта DOCX</div>
          <div style={{ fontSize: 11.5, color: "var(--ink-3)" }}>Загрузить готовый файл</div>
        </div>
      </div>
    </div>
  </Modal>
);

const CreateDocumentModal = ({ onClose, onCreate }) => {
  const [tpl, setTpl] = React.useState("tpl-rent");
  return (
    <Modal title="Новый документ" onClose={onClose}
      footer={<>
        <button className="btn" onClick={onClose}>Отмена</button>
        <button className="btn primary" onClick={onCreate}>Создать <Icon name="chev-right" size={12}/></button>
      </>}>
      <div className="field">
        <label className="field-lbl">Название документа <span style={{ color: "var(--accent-red)" }}>*</span></label>
        <input className="input" placeholder="Аренда офис · Контрагент"/>
      </div>
      <div className="field">
        <label className="field-lbl">Шаблон</label>
        <div style={{ display: "flex", flexDirection: "column", gap: 6, maxHeight: 240, overflowY: "auto", padding: 2 }}>
          {SAMPLE_TEMPLATES.filter(t => t.status === "published").map(t => (
            <div key={t.id} onClick={()=>setTpl(t.id)}
                 className="card hover pad"
                 style={{ padding: "10px 12px", display: "flex", alignItems: "center", gap: 10, cursor: "pointer",
                          borderColor: tpl === t.id ? "var(--brand)" : undefined,
                          boxShadow: tpl === t.id ? "0 0 0 3px oklch(from var(--brand) l c h / 0.12)" : undefined }}>
              <div style={{ width: 26, height: 26, borderRadius: 6, background: "var(--brand-soft)", color: "var(--brand-ink)", display: "grid", placeItems: "center" }}>
                <Icon name="doc" size={13}/>
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 500 }}>{t.name}</div>
                <div style={{ fontSize: 11, color: "var(--ink-3)" }}>{t.category} · использован {t.count} раз</div>
              </div>
              {tpl === t.id && <Icon name="check" size={14} style={{ color: "var(--brand)" }}/>}
            </div>
          ))}
        </div>
      </div>
    </Modal>
  );
};

const ExportModal = ({ onClose }) => (
  <Modal title="Экспорт документа" onClose={onClose} width={440}
    footer={<>
      <button className="btn" onClick={onClose}>Отмена</button>
      <button className="btn primary" onClick={onClose}><Icon name="download" size={13}/> Скачать</button>
    </>}>
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div className="card hover pad" style={{ padding: 14, display: "flex", gap: 12, alignItems: "center", borderColor: "var(--brand)", boxShadow: "0 0 0 3px oklch(from var(--brand) l c h / 0.12)" }}>
        <div style={{ width: 36, height: 36, borderRadius: 8, background: "var(--brand-soft)", color: "var(--brand-ink)", display: "grid", placeItems: "center" }}><Icon name="doc" size={18}/></div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 550 }}>DOCX</div>
          <div style={{ fontSize: 12, color: "var(--ink-3)" }}>Редактируемый Microsoft Word</div>
        </div>
        <Icon name="check" size={16} style={{ color: "var(--brand)" }}/>
      </div>
      <div className="card hover pad" style={{ padding: 14, display: "flex", gap: 12, alignItems: "center" }}>
        <div style={{ width: 36, height: 36, borderRadius: 8, background: "var(--accent-red-soft)", color: "var(--accent-red)", display: "grid", placeItems: "center" }}><Icon name="doc" size={18}/></div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 550 }}>PDF</div>
          <div style={{ fontSize: 12, color: "var(--ink-3)" }}>Готов к отправке и подписи</div>
        </div>
      </div>
    </div>
    <div className="field">
      <label className="field-lbl">Имя файла</label>
      <input className="input" defaultValue="Аренда офис · ООО «Карелия» · 24.04.2026"/>
    </div>
    <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13 }}>
      <input type="checkbox" defaultChecked/> Добавить водяной знак «Черновик»
    </label>
  </Modal>
);
