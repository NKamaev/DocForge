// app.jsx — root + tweaks

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "primaryHue": 232,
  "density": "regular",
  "fonts": "grotesk",
  "dark": false,
  "screen": "designer",
  "published": false,
  "unsaved": true
}/*EDITMODE-END*/;

const HUE_PRESETS = [
  { key: "indigo", h: 232, label: "Индиго" },
  { key: "blue", h: 248, label: "Синий" },
  { key: "teal", h: 195, label: "Бирюзовый" },
  { key: "green", h: 150, label: "Зелёный" },
  { key: "amber", h: 60, label: "Янтарный" },
  { key: "coral", h: 25, label: "Коралл" },
  { key: "violet", h: 295, label: "Фиолетовый" },
];

function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const [screen, setScreenLocal] = React.useState(t.screen || "designer");
  const [signedIn, setSignedIn] = React.useState(true);
  const [createTpl, setCreateTpl] = React.useState(false);
  const [createDoc, setCreateDoc] = React.useState(false);
  const [exporting, setExporting] = React.useState(false);
  const [pageSettings, setPageSettings] = React.useState(false);
  const [history, setHistory] = React.useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = React.useState(false);
  const [published, setPublished] = React.useState(t.published);
  const [unsaved, setUnsaved] = React.useState(t.unsaved);

  // Sync screen with tweak
  React.useEffect(() => { if (t.screen && t.screen !== screen) setScreenLocal(t.screen); }, [t.screen]);
  React.useEffect(() => { setPublished(t.published); }, [t.published]);

  const go = (s) => {
    setScreenLocal(s);
    setTweak("screen", s);
  };

  const user = { name: "Анна Соколова", firstName: "Анна", initials: "АС", role: "Юрист, ООО «Северные окна»" };

  const rootStyle = { "--brand-h": t.primaryHue };

  if (!signedIn) {
    return (
      <div data-density={t.density} data-theme={t.dark ? "dark" : "light"} data-fonts={t.fonts} style={rootStyle}>
        <Login onSignIn={() => setSignedIn(true)}/>
        <TweaksUI t={t} setTweak={setTweak}/>
      </div>
    );
  }

  const isFullPane = screen === "designer" || screen === "workspace";

  return (
    <div data-density={t.density} data-theme={t.dark ? "dark" : "light"} data-fonts={t.fonts} style={rootStyle}>
      <div className={"app" + (sidebarCollapsed ? " sidebar-collapsed" : "")}>
        <Topbar user={user} onSignOut={() => setSignedIn(false)}
          onToggleSidebar={() => setSidebarCollapsed(c => !c)}
          sidebarCollapsed={sidebarCollapsed}
          active={screen} setActive={go}/>
        <Sidebar active={screen} setActive={go} collapsed={sidebarCollapsed} onToggle={() => setSidebarCollapsed(c => !c)}/>
        <main className="main" style={isFullPane ? { padding: 0 } : {}}>
          {screen === "home" && <Home go={go} user={user}/>}
          {screen === "templates" && <Templates go={go} openCreate={() => setCreateTpl(true)}/>}
          {screen === "documents" && <Documents go={go}/>}
          {screen === "directories" && <Directories go={go} openDir={() => go("directory-detail")}/>}
          {screen === "directory-detail" && <DirectoryDetail go={go}/>}
          {screen === "designer" && <TemplateDesigner
            go={go}
            openExport={() => setExporting(true)}
            openPageSettings={() => setPageSettings(true)}
            published={published}
            setPublished={(v) => { setPublished(v); setTweak("published", v); }}
            hasUnsaved={unsaved}
            setUnsaved={(v) => { setUnsaved(v); setTweak("unsaved", v); }}
          />}
          {screen === "workspace" && <Workspace go={go} openHistory={() => setHistory(true)} openExport={() => setExporting(true)}/>}
        </main>
      </div>

      {createTpl && <CreateTemplateModal onClose={() => setCreateTpl(false)} onCreate={() => { setCreateTpl(false); go("designer"); }}/>}
      {createDoc && <CreateDocumentModal onClose={() => setCreateDoc(false)} onCreate={() => { setCreateDoc(false); go("workspace"); }}/>}
      {exporting && <ExportModal onClose={() => setExporting(false)}/>}
      {pageSettings && <PageSettingsDrawer onClose={() => setPageSettings(false)}/>}
      {history && <RevisionsDrawer onClose={() => setHistory(false)}/>}

      <TweaksUI t={t} setTweak={setTweak}/>
    </div>
  );
}

const SCREEN_OPTIONS = [
  ["home", "Главная"],
  ["templates", "Шаблоны"],
  ["designer", "Конструктор"],
  ["documents", "Документы"],
  ["workspace", "Заполнение"],
  ["directories", "Справочники"],
  ["directory-detail", "Справочник"],
];

const TweaksUI = ({ t, setTweak }) => (
  <TweaksPanel>
    <TweakSection label="Навигация"/>
    <TweakSelect label="Экран" value={t.screen}
      options={SCREEN_OPTIONS.map(([v,l]) => ({ value: v, label: l }))}
      onChange={(v) => setTweak("screen", v)}/>

    <TweakSection label="Тема"/>
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={{ fontSize: 11, color: "rgba(41,38,27,.72)", fontWeight: 500 }}>Основной цвет</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 4 }}>
        {HUE_PRESETS.map(p => (
          <button key={p.key} title={p.label}
                  onClick={() => setTweak("primaryHue", p.h)}
                  style={{ height: 22, borderRadius: 5, border: t.primaryHue === p.h ? "1.5px solid #29261b" : "1px solid rgba(0,0,0,.12)",
                           background: `oklch(0.55 0.16 ${p.h})`, cursor: "pointer", padding: 0 }}/>
        ))}
      </div>
    </div>
    <TweakSlider label="Оттенок" value={t.primaryHue} min={0} max={360} step={1} unit="°"
      onChange={(v) => setTweak("primaryHue", v)}/>
    <TweakToggle label="Тёмная тема" value={t.dark}
      onChange={(v) => setTweak("dark", v)}/>

    <TweakSection label="Плотность"/>
    <TweakRadio label="Размер" value={t.density}
      options={["compact", "regular", "comfy"]}
      onChange={(v) => setTweak("density", v)}/>

    <TweakSection label="Типографика"/>
    <TweakRadio label="Шрифт UI" value={t.fonts}
      options={[
        { value: "grotesk", label: "Гротеск" },
        { value: "serif-display", label: "Серифные заголовки" },
        { value: "mono-ui", label: "Моно" },
      ]}
      onChange={(v) => setTweak("fonts", v)}/>

    <TweakSection label="Состояния"/>
    <TweakToggle label="Шаблон опубликован" value={t.published}
      onChange={(v) => setTweak("published", v)}/>
    <TweakToggle label="Несохранённые изменения" value={t.unsaved}
      onChange={(v) => setTweak("unsaved", v)}/>
  </TweaksPanel>
);

ReactDOM.createRoot(document.getElementById("root")).render(<App/>);
