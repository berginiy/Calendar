import { useState } from 'react'
import { Alert, App, Button, Card, Segmented, Space, Spin, Table, Tag, Tooltip, Typography } from 'antd'
import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { preferencesApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { Outcome, PrefStatus, PreferenceDay, PreferenceType } from '../../types/api'
import { DATE_FMT, formatDate, todayBishkek } from '../../utils/format'

type Choice = PreferenceType | 'NONE'

const CHOICES: { label: string; value: Choice }[] = [
  { label: 'Офис', value: 'OFFICE' },
  { label: 'Удалённо', value: 'REMOTE' },
  { label: 'Гибко', value: 'FLEXIBLE' },
  { label: 'Не указано', value: 'NONE' },
]

const STATUS_TAG: Record<PrefStatus, { color: string; label: string }> = {
  PENDING: { color: 'default', label: 'Ожидает распределения' },
  PRELIMINARY: { color: 'orange', label: 'Предварительно' },
  SATISFIED: { color: 'green', label: 'Учтено' },
  ASSIGNED: { color: 'blue', label: 'Назначено иначе' },
  NOT_SATISFIED: { color: 'red', label: 'Не удовлетворено' },
}

const OUTCOME_TEXT: Record<Outcome, string> = { OFFICE: 'офис', REMOTE: 'удалённо', NONE: 'без назначения' }

const monday = (d: dayjs.Dayjs) => d.subtract((d.day() + 6) % 7, 'day')

export function PreferencesPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [weekStart, setWeekStart] = useState(() => monday(dayjs(todayBishkek())))
  const [draft, setDraft] = useState<Record<string, Choice>>({})
  const from = weekStart.format(DATE_FMT)
  const to = weekStart.add(6, 'day').format(DATE_FMT)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['preferences', from, to],
    queryFn: () => preferencesApi.list(from, to),
  })

  const days = (data?.days ?? []).filter((d) => d.workingDay)
  const current = (d: PreferenceDay): Choice => draft[d.date] ?? d.preference ?? 'NONE'
  const changed = days.filter((d) => draft[d.date] !== undefined && draft[d.date] !== (d.preference ?? 'NONE'))

  const save = useMutation({
    mutationFn: () => preferencesApi.bulk(changed.map((d) => ({
      date: d.date, preference: draft[d.date] === 'NONE' ? null : (draft[d.date] as PreferenceType),
    }))),
    onSuccess: () => {
      message.success('Предпочтения сохранены')
      setDraft({})
      queryClient.invalidateQueries({ queryKey: ['preferences'] })
    },
    onError: (e) => { message.error(errorMessage(e)); queryClient.invalidateQueries({ queryKey: ['preferences'] }) },
  })

  const shift = (n: number) => { setDraft({}); setWeekStart(weekStart.add(n, 'week')) }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>
          Мои предпочтения: {weekStart.format('D MMM')} — {weekStart.add(6, 'day').format('D MMM YYYY')}
        </Typography.Title>
        <Space>
          <Button icon={<LeftOutlined />} aria-label="Предыдущая неделя" onClick={() => shift(-1)} />
          <Button onClick={() => { setDraft({}); setWeekStart(monday(dayjs(todayBishkek()))) }}>Эта неделя</Button>
          <Button icon={<RightOutlined />} aria-label="Следующая неделя" onClick={() => shift(1)} />
        </Space>
      </Space>

      <Alert type="info" showIcon
        message="Предпочтение — это пожелание, а не гарантия. Итоговое место в офисе определяет распределение; пока оно не подтверждено руководителем, результат предварительный." />

      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}
      {data && !data.enabled && (
        <Alert type="warning" showIcon message="Автоматическое распределение сейчас выключено руководителем — предпочтения не принимаются." />
      )}

      <Card extra={
        <Space>
          {changed.length > 0 && <Typography.Text type="secondary">Несохранённых изменений: {changed.length}</Typography.Text>}
          <Button type="primary" disabled={changed.length === 0} loading={save.isPending} onClick={() => save.mutate()}>
            Сохранить
          </Button>
        </Space>
      }>
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : (
          <Table<PreferenceDay> rowKey="date" size="middle" pagination={false} dataSource={days} scroll={{ x: true }}
            locale={{ emptyText: 'В этой неделе нет рабочих дней' }}
            columns={[
              { title: 'Дата', dataIndex: 'date', render: (d: string) => formatDate(d) },
              { title: 'Предпочтение', render: (_, d) => (
                <Tooltip title={d.editable ? undefined : d.editableReason}>
                  <span>
                    <Segmented size="small" value={current(d)} options={CHOICES} disabled={!d.editable}
                      aria-label={`Предпочтение на ${formatDate(d.date)}`}
                      onChange={(v) => setDraft({ ...draft, [d.date]: v as Choice })} />
                  </span>
                </Tooltip>) },
              { title: 'Статус', render: (_, d) => {
                if (!d.status) return <Typography.Text type="secondary">—</Typography.Text>
                const tag = STATUS_TAG[d.status]
                return (
                  <Space direction="vertical" size={2}>
                    <Tag color={tag.color} style={{ marginInlineEnd: 0 }}>
                      {tag.label}{d.status === 'PRELIMINARY' && d.outcome ? `: ${OUTCOME_TEXT[d.outcome]}` : ''}
                    </Tag>
                    {d.status === 'PRELIMINARY' && <Typography.Text type="secondary">Ещё не подтверждено руководителем</Typography.Text>}
                  </Space>)
              } },
              { title: 'Пояснение', render: (_, d) => d.explanation
                ? <Typography.Text>{d.explanation}</Typography.Text>
                : (!d.editable && d.editableReason ? <Typography.Text type="secondary">{d.editableReason}</Typography.Text> : null) },
            ]} />
        )}
      </Card>
    </Space>
  )
}
