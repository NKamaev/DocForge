import { Switch } from 'antd'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

export default function BooleanField({ value, onChange, disabled }: Props) {
  const checked = value === true || value === 'true'

  return (
    <Switch
      checked={checked}
      onChange={v => onChange(v)}
      disabled={disabled}
      checkedChildren="Да"
      unCheckedChildren="Нет"
    />
  )
}
