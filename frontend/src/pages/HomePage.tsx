import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { documentsApi } from '../api/documentsApi'
import { templatesApi } from '../api/templatesApi'
import type { DocumentListItem } from '../types/document'
import { Icon } from '../components/Icon'
import { startOfWeek } from '../utils/startOfWeek'

function StatCard({
  icon,
  color,
  num,
  label,
  trend,
}: {
  icon: string
  color?: string
  num: string | number
  label: string
  trend?: string
}) {
  return (
    <div className="card pad" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{
          width: 32, height: 32, borderRadius: 8,
          background: color ?? 'var(--brand-soft)',
          color: 'var(--brand-ink)',
          display: 'grid', placeItems: 'center',
        }}>
          <Icon name={icon} size={16} />
        </div>
        {trend && (
          <span className="tag green">
            <span className="dot" />{trend}
          </span>
        )}
      </div>
      <div style={{ fontSize: 26, fontWeight: 600, letterSpacing: '-0.02em', lineHeight: 1 }}>{num}</div>
      <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{label}</div>
    </div>
  )
}

export default function HomePage() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const [docs, setDocs] = useState<DocumentListItem[]>([])
  const [templateCount, setTemplateCount] = useState<number | null>(null)
  const [weekTemplateCount, setWeekTemplateCount] = useState<number | null>(null)

  useEffect(() => {
    documentsApi.list(0, 4).then((p) => setDocs(p.content)).catch(() => {})
    templatesApi.getTemplates({ page: 0, size: 1 }).then((p) => setTemplateCount(p.totalElements)).catch(() => {})
    templatesApi
      .getTemplates({ page: 0, size: 1, createdAfter: startOfWeek().toISOString() })
      .then((p) => setWeekTemplateCount(p.totalElements))
      .catch(() => {})
  }, [])

  const firstName = user?.displayName?.split(' ')[0] ?? 'пользователь'

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <div className="crumb">
            <Icon name="home" size={12} />
            <span>Главная</span>
          </div>
          <h1>Здравствуйте, {firstName}.</h1>
          <p>Добро пожаловать в DocForge — конструктор документов.</p>
        </div>
        <div className="page-head-actions">
          <button className="btn primary" onClick={() => navigate('/templates')}>
            <Icon name="plus" size={14} /> Новый шаблон
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 22 }}>
        <StatCard
          icon="templates"
          num={templateCount !== null ? templateCount : '—'}
          label="Шаблонов"
          trend={weekTemplateCount !== null ? `+${weekTemplateCount} за неделю` : undefined}
        />
        <StatCard icon="doc" num={docs.length} label="Документов в работе" />
        <StatCard icon="users" num="—" label="Активных соавторов" color="var(--accent-green-soft)" />
        <StatCard icon="download" num="—" label="Экспортов в этом месяце" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: 14 }}>
        <div className="card">
          <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--line)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ margin: 0, fontSize: 14, fontWeight: 550 }}>Продолжить работу</h3>
            <a onClick={() => navigate('/documents')} style={{ fontSize: 12, color: 'var(--brand-ink)', cursor: 'pointer' }}>
              Все документы →
            </a>
          </div>
          <div style={{ padding: '6px 8px' }}>
            {docs.length === 0 && (
              <div style={{ padding: '24px 16px', color: 'var(--ink-3)', fontSize: 13, textAlign: 'center' }}>
                Нет документов. Создайте первый.
              </div>
            )}
            {docs.map((d) => (
              <div
                key={d.id}
                onClick={() => navigate(`/documents/${d.id}/fill`)}
                style={{ display: 'grid', gridTemplateColumns: '26px 1fr auto auto', gap: 12, alignItems: 'center', padding: '10px 10px', borderRadius: 8, cursor: 'pointer' }}
                className="hoverable-row"
              >
                <div style={{ width: 26, height: 26, borderRadius: 6, background: 'var(--brand-soft)', color: 'var(--brand-ink)', display: 'grid', placeItems: 'center' }}>
                  <Icon name="doc" size={14} />
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{d.name}</div>
                  <div style={{ fontSize: 11.5, color: 'var(--ink-3)' }}>
                    {d.status === 'GENERATED' ? 'Сгенерирован' : d.status === 'EXPORTED' ? 'Экспортирован' : 'Черновик'}
                    {' · '}{new Date(d.createdAt).toLocaleDateString('ru')}
                  </div>
                </div>
                <div style={{ width: 110, display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', justifyContent: 'space-between' }}>
                    <span>Статус</span>
                  </div>
                  <div className="q-progress" style={{ marginTop: 0 }}>
                    <div style={{ width: d.status === 'EXPORTED' ? '100%' : d.status === 'GENERATED' ? '75%' : '30%' }} />
                  </div>
                </div>
                <Icon name="chev-right" size={14} />
              </div>
            ))}
          </div>
        </div>

        <div className="card pad" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 550 }}>Быстрые действия</h3>
          <div onClick={() => navigate('/templates')} className="card hover" style={{ padding: 14, display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <div style={{ width: 34, height: 34, borderRadius: 8, background: 'var(--brand-soft)', color: 'var(--brand-ink)', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
              <Icon name="templates" size={16} />
            </div>
            <div>
              <div style={{ fontSize: 13.5, fontWeight: 550 }}>Создать шаблон</div>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>Начните с пустой страницы или выберите готовый шаблон.</div>
            </div>
          </div>
          <div onClick={() => navigate('/documents')} className="card hover" style={{ padding: 14, display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <div style={{ width: 34, height: 34, borderRadius: 8, background: 'var(--accent-green-soft)', color: 'oklch(0.42 0.12 150)', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
              <Icon name="doc" size={16} />
            </div>
            <div>
              <div style={{ fontSize: 13.5, fontWeight: 550 }}>Сгенерировать документ</div>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>Выберите шаблон и заполните опросный лист.</div>
            </div>
          </div>
          <div onClick={() => navigate('/directories')} className="card hover" style={{ padding: 14, display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <div style={{ width: 34, height: 34, borderRadius: 8, background: 'oklch(0.96 0.04 280)', color: 'oklch(0.4 0.13 280)', display: 'grid', placeItems: 'center', flexShrink: 0 }}>
              <Icon name="book" size={16} />
            </div>
            <div>
              <div style={{ fontSize: 13.5, fontWeight: 550 }}>Справочники</div>
              <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>Города, организации, сотрудники.</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
