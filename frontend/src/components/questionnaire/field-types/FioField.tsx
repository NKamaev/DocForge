import { Input, Space } from 'antd'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

/**
 * ФИО-поле: три отдельных инпута (Фамилия, Имя, Отчество).
 * Хранится как единая строка "Фамилия Имя Отчество".
 */
export default function FioField({ value, onChange, disabled }: Props) {
  const str = typeof value === 'string' ? value : ''
  const parts = str.split(' ')
  const lastName  = parts[0] ?? ''
  const firstName = parts[1] ?? ''
  const midName   = parts.slice(2).join(' ')

  const emit = (ln: string, fn: string, mn: string) => {
    const combined = [ln, fn, mn].filter(Boolean).join(' ')
    onChange(combined || undefined)
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={8}>
      <Input
        placeholder="Фамилия"
        value={lastName}
        onChange={e => emit(e.target.value, firstName, midName)}
        disabled={disabled}
      />
      <Input
        placeholder="Имя"
        value={firstName}
        onChange={e => emit(lastName, e.target.value, midName)}
        disabled={disabled}
      />
      <Input
        placeholder="Отчество"
        value={midName}
        onChange={e => emit(lastName, firstName, e.target.value)}
        disabled={disabled}
      />
    </Space>
  )
}
