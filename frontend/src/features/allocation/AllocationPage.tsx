import { useEffect, useMemo, useState } from 'react'
import {
  Alert, App, Button, Card, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Popconfirm, Radio, Select,
  Space, Switch, Table, Tag, Typography,
} from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { allocationApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type {
  AllocationDaySummary, AllocationPolicy, AllocationResult, AllocationRun, AllocationSettings, Outcome, OverviewDay,
} from '../../types/api'
import { DATE_FMT, formatDate, formatDateTime, todayBishkek } from '../../utils/format'
import { ApplyTag, OutcomeTag, POLICY_LABEL, PREF_LABEL, RUN_STATUS, SOURCE_LABEL } from './allocationUi'

interface AdjustForm {
  outcome: Outcome
  reason: string
}

export function AllocationPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [settingsForm] = Form.useForm<AllocationSettings>()
  const [adjustForm] = Form.useForm<AdjustForm>()
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => {
    const t = dayjs(todayBishkek()).add(1, 'day')
    return [t, t.add(13, 'day')]
  })
  const [runId, setRunId] = useState<number | null>(null)
  const [outcomeFilter, setOutcomeFilter] = useState<Outcome | 'ALL'>('ALL')
  const [adjusting, setAdjusting] = useState<AllocationResult | null>(null)
  const from = range[0].format(DATE_FMT)
  const to = range[1].format(DATE_FMT)

  const settings = useQuery({ queryKey: ['allocation-settings'], queryFn: allocationApi.settings })
  const overview = useQuery({ queryKey: ['allocation-overview', from, to], queryFn: () => allocationApi.overview(from, to) })
  const runs = useQuery({ queryKey: ['allocation-runs'], queryFn: allocationApi.runs })
  const detail = useQuery({
    queryKey: ['allocation-run', runId], queryFn: () => allocationApi.get(runId as number), enabled: runId !== null,
  })

  useEffect(() => {
    if (settings.data) settingsForm.setFieldsValue(settings.data)
  }, [settings.data, settingsForm])

  const reload = () => {
    for (const k of ['allocation-settings', 'allocation-overview', 'allocation-runs', 'allocation-run', 'preferences',
      'calendar', 'dashboard', 'team', 'notifications']) {
      queryClient.invalidateQueries({ queryKey: [k] })
    }
  }
  const fail = (e: unknown) => { message.error(errorMessage(e)); reload() }

  const saveSettings = useMutation({
    mutationFn: allocationApi.saveSettings,
    onSuccess: () => { message.success('Настройки сохранены'); reload() },
    onError: fail,
  })
  const run = useMutation({
    mutationFn: () => allocationApi.run(from, to),
    onSuccess: (d) => { message.success('Черновик распределения рассчитан'); setRunId(d.run.id); reload() },
    onError: fail,
  })
  const confirm = useMutation({
    mutationFn: (id: number) => allocationApi.confirm(id),
    onSuccess: (d) => {
      const c = d.run
      message.success(`Распределение подтверждено: применено ${c.applied}, пропущено ${c.skipped}${c.failed ? `, ошибок ${c.failed}` : ''}`)
      reload()
    },
    onError: fail,
  })
  const discard = useMutation({
    mutationFn: (id: number) => allocationApi.discard(id),
    onSuccess: () => { message.success('Черновик отклонён, записи не созданы'); reload() },
    onError: fail,
  })
  const adjust = useMutation({
    mutationFn: (v: AdjustForm) => allocationApi.adjust(runId as number, (adjusting as AllocationResult).id, v.outcome, v.reason),
    onSuccess: () => { message.success('Результат изменён'); setAdjusting(null); reload() },
    onError: (e) => message.error(errorMessage(e)),
  })

  const exportCsv = async (id: number) => {
    try {
      const blob = await allocationApi.csv(id)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `allocation-${id}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      message.error(errorMessage(e))
    }
  }

  const d = detail.data
  const results = useMemo(
    () => (d?.results ?? []).filter((r) => outcomeFilter === 'ALL' || r.outcome === outcomeFilter),
    [d, outcomeFilter],
  )
  const willCreate = (d?.results ?? []).filter((r) => r.applyStatus === 'PENDING')
  const isDraft = d?.run.status === 'DRAFT'
  const enabled = settings.data?.enabled ?? false

  const openAdjust = (r: AllocationResult) => {
    setAdjusting(r)
    adjustForm.resetFields()
    adjustForm.setFieldsValue({ outcome: r.outcome === 'NONE' ? 'OFFICE' : 'NONE' })
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>Умное распределение</Typography.Title>

      <Alert type="info" showIcon message="Как это работает"
        description="Сотрудники указывают предпочтения (офис, удалённо, гибко). Вы запускаете расчёт — получается черновик, который ничего не меняет. Записи в календаре появляются только после подтверждения; уже существующие записи никогда не изменяются." />

      <Card title="Настройки">
        <Form<AllocationSettings> form={settingsForm} layout="vertical" onFinish={(v) => saveSettings.mutate(v)}
          initialValues={{ enabled: false, policy: 'ROTATION', preferenceDeadlineHours: 24 }}>
          <Space wrap align="end" size="large">
            <Form.Item name="enabled" label="Автоматическое распределение" valuePropName="checked"><Switch /></Form.Item>
            <Form.Item name="policy" label="Политика при нехватке мест">
              <Select style={{ width: 360 }}
                options={(Object.keys(POLICY_LABEL) as AllocationPolicy[]).map((p) => ({ value: p, label: POLICY_LABEL[p] }))} />
            </Form.Item>
            <Form.Item name="preferenceDeadlineHours" label="Приём предпочтений до начала дня, часов"
              rules={[{ type: 'number', min: 0, max: 720, message: 'От 0 до 720' }]}>
              <InputNumber min={0} max={720} precision={0} />
            </Form.Item>
            <Form.Item><Button type="primary" htmlType="submit" loading={saveSettings.isPending}>Сохранить</Button></Form.Item>
          </Space>
        </Form>
      </Card>

      <Card title="Период и спрос" extra={
        <Space wrap>
          <DatePicker.RangePicker value={range} allowClear={false}
            onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])} />
          <Popconfirm title="Рассчитать черновик распределения?"
            description="Расчёт ничего не меняет в календаре. Реальные записи появятся только после подтверждения."
            okText="Рассчитать" cancelText="Назад" onConfirm={() => run.mutate()} disabled={!enabled}>
            <Button type="primary" disabled={!enabled} loading={run.isPending}>Рассчитать распределение</Button>
          </Popconfirm>
        </Space>}>
        {!enabled && <Alert type="warning" showIcon style={{ marginBottom: 12 }}
          message="Автоматическое распределение выключено. Включите его в настройках выше." />}
        {overview.error && <Alert type="error" showIcon message={errorMessage(overview.error)} style={{ marginBottom: 12 }} />}
        <Table<OverviewDay> rowKey="date" size="small" loading={overview.isLoading} pagination={false}
          dataSource={(overview.data?.days ?? []).filter((x) => x.workingDay)} scroll={{ x: true }}
          locale={{ emptyText: 'В периоде нет рабочих дней' }}
          columns={[
            { title: 'Дата', dataIndex: 'date', render: (v: string) => formatDate(v) },
            { title: 'Мест', dataIndex: 'capacity' },
            { title: 'Уже занято', dataIndex: 'occupiedOffice' },
            { title: 'Свободно', dataIndex: 'freeSeats' },
            { title: 'Хотят офис', dataIndex: 'officePreferences' },
            { title: 'Хотят удалённо', dataIndex: 'remotePreferences' },
            { title: 'Гибко', dataIndex: 'flexiblePreferences' },
            { title: 'Не указали', dataIndex: 'noPreference' },
            { title: 'Конфликт', render: (_, x) => x.officePreferences > x.freeSeats
              ? <Tag color="red">Желающих больше мест</Tag> : <Tag color="green">Мест хватает</Tag> },
          ]} />
      </Card>

      {d && (
        <Card title={`Распределение №${d.run.id}`}
          extra={
            <Space wrap>
              <Tag color={RUN_STATUS[d.run.status].color}>{RUN_STATUS[d.run.status].label}</Tag>
              <Button icon={<DownloadOutlined />} onClick={() => exportCsv(d.run.id)}>Скачать CSV</Button>
              {isDraft && (
                <>
                  <Popconfirm title="Отклонить черновик?" description="Записи созданы не будут." okText="Отклонить"
                    cancelText="Назад" onConfirm={() => discard.mutate(d.run.id)}>
                    <Button danger loading={discard.isPending}>Отклонить</Button>
                  </Popconfirm>
                  <Popconfirm title="Подтвердить распределение?"
                    description={`Будет создано записей: ${willCreate.length} (офис ${willCreate.filter((r) => r.outcome === 'OFFICE').length}, `
                      + `удалённо ${willCreate.filter((r) => r.outcome === 'REMOTE').length}). Сотрудники получат уведомления. `
                      + 'Если место или лимит успели занять, такая запись будет пропущена.'}
                    okText="Подтвердить" cancelText="Назад" onConfirm={() => confirm.mutate(d.run.id)}>
                    <Button type="primary" loading={confirm.isPending}>Подтвердить</Button>
                  </Popconfirm>
                </>
              )}
            </Space>}>
          <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="Период">{formatDate(d.run.rangeFrom)} — {formatDate(d.run.rangeTo)}</Descriptions.Item>
            <Descriptions.Item label="Политика">{POLICY_LABEL[d.run.policy]}</Descriptions.Item>
            <Descriptions.Item label="Мест в офисе">{d.run.capacity}</Descriptions.Item>
            <Descriptions.Item label="Офис">{d.run.office}</Descriptions.Item>
            <Descriptions.Item label="Удалённо">{d.run.remote}</Descriptions.Item>
            <Descriptions.Item label="Без назначения">{d.run.none}</Descriptions.Item>
            <Descriptions.Item label="Применено / пропущено">{d.run.applied} / {d.run.skipped}</Descriptions.Item>
            {d.run.confirmedAt && <Descriptions.Item label="Подтвердил">{d.run.confirmedByName}, {formatDateTime(d.run.confirmedAt)}</Descriptions.Item>}
          </Descriptions>

          <Table<AllocationDaySummary> rowKey="date" size="small" pagination={false} dataSource={d.days}
            scroll={{ x: true }} style={{ marginBottom: 16 }}
            columns={[
              { title: 'Дата', dataIndex: 'date', render: (v: string) => formatDate(v) },
              { title: 'Хотели офис', dataIndex: 'officeWanted' },
              { title: 'Назначен офис', dataIndex: 'officeAssigned' },
              { title: 'Назначена удалёнка', dataIndex: 'remoteAssigned' },
              { title: 'Без назначения', dataIndex: 'unassigned' },
            ]} />

          <Space style={{ marginBottom: 8 }}>
            <Typography.Text>Показать:</Typography.Text>
            <Radio.Group value={outcomeFilter} onChange={(e) => setOutcomeFilter(e.target.value)} optionType="button" size="small"
              options={[{ label: 'Все', value: 'ALL' }, { label: 'Офис', value: 'OFFICE' }, { label: 'Удалённо', value: 'REMOTE' },
                { label: 'Без назначения', value: 'NONE' }]} />
          </Space>
          <Table<AllocationResult> rowKey="id" size="small" dataSource={results} scroll={{ x: true }}
            pagination={{ pageSize: 20, hideOnSinglePage: true }}
            columns={[
              { title: 'Дата', dataIndex: 'date', render: (v: string) => formatDate(v) },
              { title: 'Сотрудник', dataIndex: 'userName' },
              { title: 'Предпочтение', dataIndex: 'preference', render: (v) => PREF_LABEL[v as keyof typeof PREF_LABEL] },
              { title: 'Результат', dataIndex: 'outcome', render: (v: Outcome) => <OutcomeTag outcome={v} /> },
              { title: 'Источник', dataIndex: 'source', render: (v: keyof typeof SOURCE_LABEL) => SOURCE_LABEL[v] },
              { title: 'Применение', dataIndex: 'applyStatus', render: (_, r) => (
                <Space direction="vertical" size={0}>
                  <ApplyTag status={r.applyStatus} />
                  {r.applyMessage && r.applyStatus !== 'APPLIED' && <Typography.Text type="secondary">{r.applyMessage}</Typography.Text>}
                </Space>) },
              { title: 'Объяснение', dataIndex: 'explanation', width: 360 },
              { title: '', key: 'a', render: (_, r) => isDraft && r.source !== 'EXISTING'
                ? <Button size="small" onClick={() => openAdjust(r)}>Изменить</Button> : null },
            ]} />
        </Card>
      )}

      <Card title="История распределений">
        <Table<AllocationRun> rowKey="id" size="small" loading={runs.isLoading} dataSource={runs.data ?? []}
          scroll={{ x: true }} pagination={{ pageSize: 10, hideOnSinglePage: true }}
          locale={{ emptyText: 'Распределений ещё не было' }}
          columns={[
            { title: '№', dataIndex: 'id' },
            { title: 'Период', render: (_, r) => `${formatDate(r.rangeFrom)} — ${formatDate(r.rangeTo)}` },
            { title: 'Статус', dataIndex: 'status', render: (v: AllocationRun['status']) => <Tag color={RUN_STATUS[v].color}>{RUN_STATUS[v].label}</Tag> },
            { title: 'Запустил', dataIndex: 'createdByName' },
            { title: 'Создан', dataIndex: 'createdAt', render: formatDateTime },
            { title: 'Офис / удалённо / без', render: (_, r) => `${r.office} / ${r.remote} / ${r.none}` },
            { title: '', key: 'open', render: (_, r) => <Button size="small" onClick={() => setRunId(r.id)}>Открыть</Button> },
          ]} />
      </Card>

      <Modal open={adjusting !== null} destroyOnClose onCancel={() => setAdjusting(null)}
        title={adjusting ? `Изменить результат: ${adjusting.userName}, ${formatDate(adjusting.date)}` : ''}
        okText="Сохранить" cancelText="Отмена" confirmLoading={adjust.isPending}
        onOk={() => adjustForm.validateFields().then((v) => adjust.mutate(v)).catch(() => undefined)}>
        <Form<AdjustForm> form={adjustForm} layout="vertical" requiredMark={false}>
          <Form.Item name="outcome" label="Новый результат" rules={[{ required: true }]}>
            <Select options={[{ value: 'OFFICE', label: 'Офис' }, { value: 'REMOTE', label: 'Удалённо' }, { value: 'NONE', label: 'Без назначения' }]} />
          </Form.Item>
          <Form.Item name="reason" label="Причина изменения"
            rules={[{ required: true, whitespace: true, message: 'Укажите причину' }, { max: 500, message: 'Не длиннее 500 символов' }]}
            extra="Причина сохраняется в журнале аудита. Сотрудник увидит только то, что результат скорректирован руководителем.">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  )
}
