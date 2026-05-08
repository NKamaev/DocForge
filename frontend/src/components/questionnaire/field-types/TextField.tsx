import { Input } from 'antd'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

export default function TextField({ value, onChange, disabled }: Props) {
  return (
    <Input
      value={typeof value === 'string' ? value : ''}
      onChange={e => onChange(e.target.value)}
      disabled={disabled}
      allowClear
    />
  )
}
