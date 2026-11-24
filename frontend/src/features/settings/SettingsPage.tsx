import { useEffect } from 'react'
import { App, Alert, Button, Card, Checkbox, Form, InputNumber, Space, Spin, Switch, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { managerApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { DayOfWeek, LimitRule } from '../../types/api'

const DAYS: { label: string; value: DayOfWeek }[] = [
  { label: 'Пн', value: 'MONDAY' }, { label: 'Вт', value: 'TUESDAY' }, { label: 'Ср', value: 'WEDNESDAY' },
  { label: 'Чт', value: 'THURSDAY' }, { label: 'Пт', value: 'FRIDAY' }, { label: 'Сб', value: 'SATURDAY' },
  { label: 'Вс', value: 'SUNDAY' },
]

interface SettingsForm {
  officeCapacity: number
  allowSameDayBooking: boolean
  cancellationDeadlineHours: number
  workingDays: DayOfWeek[]
  teamVisibleToEmployees: boolean
}

interface LimitsForm {
  weeklyEnabled: boolean
  weeklyMax: number
  monthlyEnabled: boolean
  monthlyMax: number
}

export function SettingsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [settingsForm] = Form.useForm<SettingsForm>()
  const [limitsForm] = Form.useForm<LimitsForm>()

  const settings = useQuery({ queryKey: ['settings'], queryFn: managerApi.settings })
  const rules = useQuery({ queryKey: ['limit-rules'], queryFn: managerApi.limitRules })

  useEffect(() => {
    if (settings.data) settingsForm.setFieldsValue(settings.data)
  }, [settings.data, settingsForm])

  useEffect(() => {
    if (!rules.data) return
    const w = rules.data.find((r) => r.periodType === 'WEEKLY')
    const m = rules.data.find((r) => r.periodType === 'MONTHLY')
    limitsForm.setFieldsValue({
      weeklyEnabled: w?.enabled ?? false, weeklyMax: w?.maxDays ?? 1,
      monthlyEnabled: m?.enabled ?? false, monthlyMax: m?.maxDays ?? 4,
    })
  }, [rules.data, limitsForm])

  const afterSave = () => {
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
    queryClient.invalidateQueries({ queryKey: ['my-stats'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }

  const saveSettings = useMutation({
    mutationFn: managerApi.saveSettings,
    onSuccess: () => { message.success('Настройки сохранены'); queryClient.invalidateQueries({ queryKey: ['settings'] }); afterSave() },
    onError: (e) => message.error(errorMessage(e)),
  })

  const saveLimits = useMutation({
    mutationFn: (v: LimitsForm) => {
      const payload: LimitRule[] = [
        { periodType: 'WEEKLY', maxDays: v.weeklyMax, enabled: v.weeklyEnabled },
        { periodType: 'MONTHLY', maxDays: v.monthlyMax, enabled: v.monthlyEnabled },
      ]
      return managerApi.saveLimitRules(payload)
    },
    onSuccess: () => { message.success('Лимиты сохранены'); queryClient.invalidateQueries({ queryKey: ['limit-rules'] }); afterSave() },
    onError: (e) => message.error(errorMessage(e)),
  })

  if (settings.isLoading || rules.isLoading) return <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
  if (settings.error || rules.error) {
    return <Alert type="error" showIcon message={errorMessage(settings.error ?? rules.error)} />
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%', maxWidth: 640 }}>
      <Typography.Title level={3} style={{ margin: 0 }}>Настройки</Typography.Title>

      <Card title="Офис и бронирование">
        <Form<SettingsForm> form={settingsForm} layout="vertical" onFinish={(v) => saveSettings.mutate(v)}>
          <Form.Item name="officeCapacity" label="Количество рабочих мест"
            rules={[{ required: true, message: 'Укажите количество мест' }, { type: 'number', min: 1, max: 1000, message: 'От 1 до 1000' }]}
            extra="Нельзя установить значение меньше числа уже оформленных бронирований на будущие даты">
            <InputNumber min={1} max={1000} precision={0} />
          </Form.Item>
          <Form.Item name="allowSameDayBooking" label="Разрешить запись на текущий день" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="teamVisibleToEmployees" label="Показывать сотрудникам имена коллег в командном календаре"
            valuePropName="checked"
            extra="Выключено: сотрудники видят только количество людей в офисе и удалённо. Email коллег не показывается никогда.">
            <Switch />
          </Form.Item>
          <Form.Item name="cancellationDeadlineHours" label="Срок отмены, часов до начала дня"
            rules={[{ required: true, message: 'Укажите срок' }, { type: 'number', min: 0, max: 720, message: 'От 0 до 720' }]}
            extra="0 — отмена возможна до начала выбранного дня">
            <InputNumber min={0} max={720} precision={0} />
          </Form.Item>
          <Form.Item name="workingDays" label="Рабочие дни недели"
            rules={[{ required: true, type: 'array', min: 1, message: 'Выберите хотя бы один день' }]}>
            <Checkbox.Group options={DAYS} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={saveSettings.isPending}>Сохранить</Button>
        </Form>
      </Card>

      <Card title="Лимиты удалённой работы">
        <Form<LimitsForm> form={limitsForm} layout="vertical" onFinish={(v) => saveLimits.mutate(v)}>
          <Space align="end" wrap>
            <Form.Item name="weeklyEnabled" label="Недельный лимит" valuePropName="checked"><Switch /></Form.Item>
            <Form.Item name="weeklyMax" label="Дней в неделю" rules={[{ type: 'number', min: 0, max: 7, message: 'От 0 до 7' }]}>
              <InputNumber min={0} max={7} precision={0} />
            </Form.Item>
          </Space>
          <br />
          <Space align="end" wrap>
            <Form.Item name="monthlyEnabled" label="Месячный лимит" valuePropName="checked"><Switch /></Form.Item>
            <Form.Item name="monthlyMax" label="Дней в месяц" rules={[{ type: 'number', min: 0, max: 31, message: 'От 0 до 31' }]}>
              <InputNumber min={0} max={31} precision={0} />
            </Form.Item>
          </Space>
          <br />
          <Button type="primary" htmlType="submit" loading={saveLimits.isPending}>Сохранить лимиты</Button>
        </Form>
      </Card>
    </Space>
  )
}
