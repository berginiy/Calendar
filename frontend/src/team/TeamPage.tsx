import { useMemo, useState } from 'react'
import { Alert, Button, Card, Empty, Input, Segmented, Space, Table, Tag, Typography } from 'antd'
import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { teamApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { MyStatus, TeamDay } from '../../types/api'
import { DATE_FMT, STATUS_LABEL, formatDate, todayBishkek } from '../../utils/format'

type Mode = 'week' | 'month'

const SHORT: Record<MyStatus, string> = { OFFICE: 'О', REMOTE: 'У', NONE: '—' }
const CSS: Record<MyStatus, string> = { OFFICE: 'team-office', REMOTE: 'team-remote', NONE: 'team-none' }

interface Row {
  userId: number
  fullName: string
  byDate: Record<string, MyStatus>
}

export function TeamPage() {
  const [mode, setMode] = useState<Mode>('week')
  const [anchor, setAnchor] = useState(() => dayjs(todayBishkek()))
  const [status, setStatus] = useState<string>('')
  const [search, setSearch] = useState('')

  const from = mode === 'week' ? anchor.subtract((anchor.day() + 6) % 7, 'day') : anchor.startOf('month')
  const to = mode === 'week' ? from.add(6, 'day') : anchor.endOf('month')
  const f = from.format(DATE_FMT)
  const t = to.format(DATE_FMT)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['team', f, t],
    queryFn: () => teamApi.get(f, t),
  })

  const step = (dir: 1 | -1) => setAnchor(anchor.add(dir, mode))
  const days: TeamDay[] = useMemo(() => (data?.days ?? []).filter((d) => d.workingDay), [data])

  const rows: Row[] = useMemo(() => {
    if (!data || data.visibility !== 'FULL') return []
    const map = new Map<number, Row>()
    for (const d of days) {
      for (const m of d.members) {
        const row = map.get(m.userId) ?? { userId: m.userId, fullName: m.fullName, byDate: {} }
        row.byDate[d.date] = m.status
        map.set(m.userId, row)
      }
    }
    const q = search.trim().toLowerCase()
    return [...map.values()].filter((r) =>
      (!q || r.fullName.toLowerCase().includes(q)) &&
      (!status || Object.values(r.byDate).includes(status as MyStatus)))
  }, [data, days, search, status])

  const title = mode === 'week'
    ? `${from.format('D MMM')} — ${to.format('D MMM YYYY')}`
    : from.format('MMMM YYYY')

  const summaryColumns = [
    { title: 'Дата', dataIndex: 'date', render: (d: string) => formatDate(d) },
    { title: 'В офисе', dataIndex: 'office' },
    { title: 'Удалённо', dataIndex: 'remote' },
    { title: 'Без записи', dataIndex: 'notSelected' },
  ]

  const matrixColumns = [
    { title: 'Сотрудник', dataIndex: 'fullName', fixed: 'left' as const, width: 180 },
    ...days.map((d) => ({
      title: <span title={formatDate(d.date)}>{dayjs(d.date).format('dd D')}</span>,
      key: d.date, align: 'center' as const, width: 64,
      render: (_: unknown, r: Row) => {
        const s = r.byDate[d.date] ?? 'NONE'
        return <span className={`team-cell ${CSS[s]}`} title={STATUS_LABEL[s]} aria-label={STATUS_LABEL[s]}>{SHORT[s]}</span>
      },
    })),
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0, textTransform: 'capitalize' }}>Команда: {title}</Typography.Title>
        <Space wrap>
          <Segmented value={mode} onChange={(v) => setMode(v as Mode)}
            options={[{ label: 'Неделя', value: 'week' }, { label: 'Месяц', value: 'month' }]} />
          <Button icon={<LeftOutlined />} onClick={() => step(-1)} aria-label="Назад" />
          <Button onClick={() => setAnchor(dayjs(todayBishkek()))}>Сегодня</Button>
          <Button icon={<RightOutlined />} onClick={() => step(1)} aria-label="Вперёд" />
        </Space>
      </Space>

      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}

      <Alert type="info" showIcon
        message="Показаны планируемые записи, а не фактическое присутствие в офисе." />

      {data?.visibility === 'COUNTS_ONLY' && (
        <Alert type="warning" showIcon
          message="Вам доступны только количественные данные. Имена коллег показывает руководитель, если включит эту возможность в настройках." />
      )}

      <Card title="Сводка по дням" size="small">
        <Table<TeamDay> rowKey="date" size="small" loading={isLoading} dataSource={days} columns={summaryColumns}
          pagination={false} scroll={{ x: true }} locale={{ emptyText: 'Рабочих дней в периоде нет' }} />
      </Card>

      {data?.visibility === 'FULL' && (
        <Card title="Кто где работает" size="small"
          extra={<Typography.Text type="secondary">О — офис, У — удалённо, — нет записи</Typography.Text>}>
          <Space wrap style={{ marginBottom: 12 }}>
            <Segmented value={status} onChange={(v) => setStatus(String(v))}
              options={[
                { label: 'Все', value: '' },
                { label: <Tag color="purple">В офисе</Tag>, value: 'OFFICE' },
                { label: <Tag color="blue">Удалённо</Tag>, value: 'REMOTE' },
              ]} />
            <Input.Search allowClear placeholder="Поиск по имени" onSearch={setSearch}
              onChange={(e) => !e.target.value && setSearch('')} style={{ width: 240 }} />
          </Space>
          {rows.length === 0 && !isLoading ? (
            <Empty description="Сотрудники не найдены" />
          ) : (
            <Table<Row> rowKey="userId" size="small" loading={isLoading} dataSource={rows} columns={matrixColumns}
              pagination={{ pageSize: 25, hideOnSinglePage: true }} scroll={{ x: 'max-content' }} />
          )}
        </Card>
      )}
    </Space>
  )
}
