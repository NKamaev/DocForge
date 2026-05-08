import { InputNumber } from 'antd'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

export default function NumberField({ value, onChange, disabled }: Props) {
  const numVal = typeof value === 'number' ? value
    : typeof value === 'string' && value !== '' ? Number(value)
    : undefined

  return (
    <InputNumber
      value={numVal}
      onChange={v => onChange(v ?? undefined)}
      disabled={disabled}
      style={{ width: '100%' }}
      decimalSeparator=","
      step={1}
    />
  )
}
