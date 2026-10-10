import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/authApi'
import { useAuthStore } from '../store/authStore'
import type { LoginRequest, RegisterRequest } from '../types/user'
import { Icon } from '../components/Icon'
import { BrandMark } from '../components/BrandMark'

function showNotification(type: 'success' | 'error', message: string) {
  console.log(`[${type.toUpperCase()}]`, message)
  if (type === 'error') {
    alert(message)
  }
}

export default function LoginPage() {
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [tab, setTab] = useState<'signin' | 'signup'>('signin')
  const [loading, setLoading] = useState(false)

  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!login || !password) return
    setLoading(true)
    try {
      if (tab === 'signin') {
        const req: LoginRequest = { login, password }
        const response = await authApi.login(req)
        setAuth(response)
        navigate('/')
       } else {
         if (!displayName) { showNotification('error', 'Введите отображаемое имя'); return }
         const req: RegisterRequest = { login, password, displayName, email: email || undefined }
         const response = await authApi.register(req)
         setAuth(response)
         navigate('/')
       }
     } catch (err: unknown) {
       if (tab === 'signin') {
         showNotification('error', 'Неверный логин или пароль')
       } else {
         const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
         showNotification('error', detail ?? 'Ошибка регистрации')
       }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-screen">
      <div className="login-art">
        <div className="brand-mark">
          <BrandMark size={26} />
          <div className="name" style={{ fontSize: 17 }}>
            <b>Doc</b><span>Forge</span>
          </div>
        </div>
        <div style={{ position: 'relative', zIndex: 1 }}>
          <h1>Конструктор документов&nbsp;— без боли и&nbsp;без копипаста.</h1>
          <p>Соберите шаблон один раз. Заполняйте по&nbsp;опросному листу. Экспортируйте в&nbsp;DOCX или&nbsp;PDF за&nbsp;секунду.</p>
        </div>
        <div className="stamp" />
        <div className="demo-doc" aria-hidden="true">
          <h4>ДОГОВОР АРЕНДЫ № <span className="chip chip-param">№ договора</span></h4>
          <div style={{ textAlign: 'center', fontSize: '8.5pt', margin: '2px 0 8px', color: 'var(--ink-3)' }}>
            г. <span className="chip chip-param">Город</span>, <span className="chip chip-param">Дата</span>
          </div>
          <div className="l" /><div className="l" /><div className="l s" />
          <div style={{ height: 6 }} />
          <div className="l" /><div className="l" />
          <div style={{ height: 4 }} />
          <div style={{ fontSize: '8.5pt' }}>
            <span className="chip chip-cond-inline">если с мебелью</span>
          </div>
          <div className="l s" />
        </div>
        <div style={{ position: 'relative', fontSize: 12, color: 'var(--ink-3)', display: 'flex', gap: 14, zIndex: 1 }}>
          <span>© 2026 DocForge</span>
          <span>·</span>
          <span>Версия 4.2.0</span>
        </div>
      </div>

      <div className="login-form">
        <h2>{tab === 'signin' ? 'Войдите в DocForge' : 'Создайте аккаунт'}</h2>
        <p className="sub">
          {tab === 'signin'
            ? 'Введите логин и пароль, чтобы продолжить.'
            : 'Заполните форму, чтобы получить доступ к рабочему пространству.'}
        </p>
        <div className="tabs pill" style={{ marginBottom: 22, alignSelf: 'flex-start' }}>
          <div className={'tab' + (tab === 'signin' ? ' active' : '')} onClick={() => setTab('signin')}>Вход</div>
          <div className={'tab' + (tab === 'signup' ? ' active' : '')} onClick={() => setTab('signup')}>Регистрация</div>
        </div>

        <form onSubmit={handleSubmit}>
          {tab === 'signup' && (
            <div className="field">
              <label className="field-lbl">Отображаемое имя</label>
              <input
                className="input lg"
                placeholder="Анна Соколова"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                required
              />
            </div>
          )}
          <div className="field">
            <label className="field-lbl" htmlFor="login-input">Логин</label>
            <div className="input-group" style={{ height: 40 }}>
              <span className="icon"><Icon name="user" size={15} /></span>
              <input
                id="login-input"
                placeholder="anna.sokolova"
                value={login}
                onChange={(e) => setLogin(e.target.value)}
                required
                autoComplete="username"
              />
            </div>
          </div>
          {tab === 'signup' && (
            <div className="field">
              <label className="field-lbl">
                Email <span className="muted" style={{ fontWeight: 400 }}>(необязательно)</span>
              </label>
              <input
                className="input lg"
                placeholder="anna@example.ru"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
              />
            </div>
          )}
          <div className="field">
            <label className="field-lbl" htmlFor="password-input">Пароль</label>
            <div className="input-group" style={{ height: 40 }}>
              <span className="icon"><Icon name="lock" size={15} /></span>
              <input
                id="password-input"
                type="password"
                placeholder="Минимум 6 символов"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
                autoComplete={tab === 'signin' ? 'current-password' : 'new-password'}
              />
            </div>
          </div>

          <button
            className="btn primary lg block"
            type="submit"
            disabled={loading}
            style={{ marginTop: 6 }}
          >
            {loading ? 'Загрузка…' : tab === 'signin' ? 'Войти' : 'Создать аккаунт'}
            {!loading && <Icon name="chev-right" size={14} />}
          </button>
        </form>

        <div className="meta">
          {tab === 'signin'
            ? <>Нет аккаунта?{' '}<a href="#" onClick={(e) => { e.preventDefault(); setTab('signup') }}>Зарегистрируйтесь</a></>
            : <>Уже зарегистрированы?{' '}<a href="#" onClick={(e) => { e.preventDefault(); setTab('signin') }}>Войти</a></>}
        </div>
      </div>
    </div>
  )
}
