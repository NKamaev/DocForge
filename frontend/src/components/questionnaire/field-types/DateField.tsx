import { DatePicker } from 'antd'
import dayjs from 'dayjs'

interface Props {
  value: unknown
  onChange: (val: unknown) => void
  disabled?: boolean
}

export default function DateField({ value, onChange, disabled }: Props) {
  const dayjsValue = typeof value === 'string' && value ? dayjs(value) : null

  return (
    <DatePicker
      value={dayjsValue}
      onChange={d => onChange(d ? d.format('YYYY-MM-DD') : null)}
      disabled={disabled}
      format="DD.MM.YYYY"
      style={{ width: '100%' }}
      placeholder="дд.мм.гггг"
    />
  )
}
