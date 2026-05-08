import { Col, Drawer, Form, InputNumber, Radio, Row, Select, Divider, Typography } from 'antd'
import { useEffect } from 'react'
import type { DocumentFormatting } from '../../types/template'

const { Text } = Typography

interface Props {
  open: boolean
  onClose: () => void
  formatting: DocumentFormatting
  onChange: (f: DocumentFormatting) => void
}

const PAGE_SIZES = [
  { value: 'A4', label: 'A4 (210 × 297 мм)' },
  { value: 'A3', label: 'A3 (297 × 420 мм)' },
  { value: 'Letter', label: 'Letter (216 × 279 мм)' },
]

const FONT_FAMILIES = [
  { value: 'Times New Roman, serif', label: 'Times New Roman' },
  { value: 'Arial, sans-serif', label: 'Arial' },
  { value: 'Calibri, sans-serif', label: 'Calibri' },
  { value: 'Courier New, monospace', label: 'Courier New' },
]

const LINE_SPACINGS = [
  { value: 1, label: '1.0 — одинарный' },
  { value: 1.15, label: '1.15' },
  { value: 1.5, label: '1.5 — полуторный' },
  { value: 2, label: '2.0 — двойной' },
]

export default function PageSettingsDrawer({ open, onClose, formatting, onChange }: Props) {
  const [form] = Form.useForm<DocumentFormatting & { orientation: 'portrait' | 'landscape' }>()

  useEffect(() => {
    if (open) {
      form.setFieldsValue({
        pageSize: formatting.pageSize,
        orientation: formatting.orientation ?? 'portrait',
        margins: formatting.margins,
        defaultFont: formatting.defaultFont,
        defaultFontSize: formatting.defaultFontSize,
        lineSpacing: formatting.lineSpacing,
        paragraphDefaults: formatting.paragraphDefaults ?? {},
      })
    }
  }, [open, formatting, form])

  const handleValuesChange = (_: unknown, all: typeof formatting & { orientation: 'portrait' | 'landscape' }) => {
    onChange({
      pageSize: all.pageSize,
      orientation: all.orientation,
      margins: all.margins ?? formatting.margins,
      defaultFont: all.defaultFont,
      defaultFontSize: all.defaultFontSize,
      lineSpacing: all.lineSpacing,
      paragraphDefaults: all.paragraphDefaults,
    })
  }

  return (
    <Drawer
      title="Параметры страницы"
      open={open}
      onClose={onClose}
      width={360}
      destroyOnHidden={false}
    >
      <Form
        form={form}
        layout="vertical"
        onValuesChange={handleValuesChange}
        size="small"
      >
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
          Изменения применяются немедленно и сохраняются вместе с шаблоном.
        </Text>

        <Divider orientation="left" plain style={{ fontSize: 12 }}>Страница</Divider>

        <Form.Item label="Формат" name="pageSize">
          <Select options={PAGE_SIZES} />
        </Form.Item>

        <Form.Item label="Ориентация" name="orientation">
          <Radio.Group optionType="button" buttonStyle="solid">
            <Radio.Button value="portrait">Книжная</Radio.Button>
            <Radio.Button value="landscape">Альбомная</Radio.Button>
          </Radio.Group>
        </Form.Item>

        <Divider orientation="left" plain style={{ fontSize: 12 }}>Поля (мм)</Divider>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <Form.Item label="Верхнее" name={['margins', 'top']}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="мм" />
          </Form.Item>
          <Form.Item label="Нижнее" name={['margins', 'bottom']}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="мм" />
          </Form.Item>
          <Form.Item label="Левое" name={['margins', 'left']}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="мм" />
          </Form.Item>
          <Form.Item label="Правое" name={['margins', 'right']}>
            <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="мм" />
          </Form.Item>
        </div>

        <Divider orientation="left" plain style={{ fontSize: 12 }}>Шрифт по умолчанию</Divider>

        <Form.Item label="Гарнитура" name="defaultFont">
          <Select options={FONT_FAMILIES} />
        </Form.Item>

        <Form.Item label="Кегль (пт)" name="defaultFontSize">
          <InputNumber min={6} max={72} style={{ width: '100%' }} addonAfter="пт" />
        </Form.Item>

        <Form.Item label="Межстрочный интервал" name="lineSpacing">
          <Select options={LINE_SPACINGS} />
        </Form.Item>

        <Divider orientation="left" plain style={{ fontSize: 12 }}>Абзац по умолчанию</Divider>

        <Text type="secondary" style={{ fontSize: 11, display: 'block', marginBottom: 8 }}>
          Применяются ко всем абзацам и заголовкам, у которых не задан явный отступ.
        </Text>

        <Row gutter={8}>
          <Col span={12}>
            <Form.Item label="Интервал перед" name={['paragraphDefaults', 'spaceBefore']}>
              <InputNumber min={0} max={200} style={{ width: '100%' }} addonAfter="пт" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="Интервал после" name={['paragraphDefaults', 'spaceAfter']}>
              <InputNumber min={0} max={200} style={{ width: '100%' }} addonAfter="пт" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="Отступ слева" name={['paragraphDefaults', 'marginLeft']}>
              <InputNumber min={0} max={200} style={{ width: '100%' }} addonAfter="мм" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="Отступ справа" name={['paragraphDefaults', 'marginRight']}>
              <InputNumber min={0} max={200} style={{ width: '100%' }} addonAfter="мм" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="Первая строка" name={['paragraphDefaults', 'firstLineIndent']}>
              <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="мм" />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Drawer>
  )
}
