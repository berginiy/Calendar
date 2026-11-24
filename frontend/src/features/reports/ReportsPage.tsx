import { useState } from 'react'
import { Alert, Button, Card, DatePicker, Select, Space, Table, Tabs, Tag, Typography } from 'antd'
import type { TablePaginationConfig } from 'antd'
import type { SorterResult } from 'antd/es/table/interface'
import { useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { managerApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { AuditRow, PageResponse, ReportRow, SummaryRow } from '../../types/api'

type AnyRow = SummaryRow | ReportRow | AuditRow
import { DATE_FMT, formatDate, formatDateTime, todayBishkek } from '../../utils/format'

type Kind = 'summary' | 'remote' | 'bookings' | 'audit'

const ACTION_LABEL: Record<string, string> = {
  BOOKING_CREATED: 'Бронирование создано',
  BOOKING_CANCELLED: 'Бронирование отменено',
  REMOTE_DAY_CREATED: 'Удалённый день создан',
  REMOTE_DAY_CANCELLED: 'Удалённый день отменён',
  ADMIN_CANCELLED_BOOKING: 'Руководитель отменил бронирование',
  ADMIN_CANCELLED_REMOTE_DAY: 'Руководитель отменил удалённый день',
  LIMIT_RULE_CHANGED: 'Изменён лимит',
  CAPACITY_CHANGED: 'Изменена вместимость',
  SETTINGS_CHANGED: 'Изменены настройки',
}

export function ReportsPage() {
  const [kind, setKind] = useState<Kind>('summary')
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => {
    const t = dayjs(todayBishkek())
    return [t.startOf('month'), t.endOf('month')]
  })
  const [userId, setUserId] = useState<number | undefined>()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [sort, setSort] = useState<{ sortBy?: string; direction?: 'asc' | 'desc' }>({})

  const from = range[0].format(DATE_FMT)
  const to = range[1].format(DATE_FMT)

  const users = useQuery({ queryKey: ['manager-users'], queryFn: managerApi.users })

  const report = useQuery<PageResponse<AnyRow>>({
    queryKey: ['report', kind, from, to, userId, page, size, sort],
    queryFn: async (): Promise<PageResponse<AnyRow>> => {
      const base = { from, to, userId, page, size, ...sort }
      switch (kind) {
        case 'summary': return managerApi.summary(base)
        case 'remote': return managerApi.remoteReport(base)
        case 'bookings': return managerApi.bookingReport(base)
        case 'audit': return managerApi.audit({ from, to, actorUserId: userId, page, size })
      }
    },
  })

  const reset = () => { setPage(0); setSort({}) }

  const onTableChange = (p: TablePaginationConfig, _f: unknown, s: SorterResult<never> | SorterResult<never>[]) => {
    setPage((p.current ?? 1) - 1)
    setSize(p.pageSize ?? 20)
    const one = Array.isArray(s) ? s[0] : s
    setSort(one?.order && one.columnKey
      ? { sortBy: String(one.columnKey), direction: one.order === 'ascend' ? 'asc' : 'desc' }
      : {})
  }

  const sortOrder = (key: string) =>
    sort.sortBy === key ? (sort.direction === 'asc' ? 'ascend' as const : 'descend' as const) : null

  const recordColumns = [
    { title: 'Сотрудник', dataIndex: 'userName', key: 'user', sorter: true, sortOrder: sortOrder('user') },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Дата', dataIndex: 'date', key: 'date', sorter: true, sortOrder: sortOrder('date'), render: formatDate },
    { title: 'Статус', dataIndex: 'status', key: 'status', sorter: true, sortOrder: sortOrder('status'),
      render: (s: string) => s === 'ACTIVE' ? <Tag color="green">Активна</Tag> : <Tag>Отменена</Tag> },
    { title: 'Создана', dataIndex: 'createdAt', key: 'createdAt', sorter: true, sortOrder: sortOrder('createdAt'), render: formatDateTime },
    { title: 'Отменена', dataIndex: 'cancelledAt', render: formatDateTime },
  ]

  const summaryColumns = [
    { title: 'Сотрудник', dataIndex: 'userName', key: 'userName', sorter: true, sortOrder: sortOrder('userName') },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Офисных дней', dataIndex: 'officeDays', key: 'officeDays', sorter: true, sortOrder: sortOrder('officeDays') },
    { title: 'Удалённых дней', dataIndex: 'remoteDays', key: 'remoteDays', sorter: true, sortOrder: sortOrder('remoteDays') },
    { title: 'Отменённых записей', dataIndex: 'cancelled', key: 'cancelled', sorter: true, sortOrder: sortOrder('cancelled') },
  ]

  const auditColumns = [
    { title: 'Время', dataIndex: 'createdAt', render: formatDateTime },
    { title: 'Кто', dataIndex: 'actorName', render: (v: string | null) => v ?? '—' },
    { title: 'Действие', dataIndex: 'action', render: (a: string) => ACTION_LABEL[a] ?? a },
    { title: 'Объект', render: (_: unknown, r: AuditRow) => `${r.entityType} ${r.entityId ?? ''}` },
    { title: 'Детали', dataIndex: 'details' },
  ]

  const columns = kind === 'summary' ? summaryColumns : kind === 'audit' ? auditColumns : recordColumns
  const data = report.data?.content

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>Отчёты</Typography.Title>
      <Card>
        <Space wrap>
          <DatePicker.RangePicker value={range} allowClear={false}
            onChange={(v) => { if (v && v[0] && v[1]) { setRange([v[0], v[1]]); reset() } }} />
          <Select allowClear showSearch placeholder="Все сотрудники" style={{ minWidth: 240 }}
            optionFilterProp="label" value={userId}
            onChange={(v) => { setUserId(v); reset() }}
            options={(users.data ?? []).map((u) => ({ value: u.id, label: `${u.lastName} ${u.firstName}` }))} />
        </Space>
      </Card>
      <Tabs activeKey={kind} onChange={(k) => { setKind(k as Kind); reset() }}
        items={[
          { key: 'summary', label: 'Сводка' },
          { key: 'remote', label: 'Удалённые дни' },
          { key: 'bookings', label: 'Бронирования' },
          { key: 'audit', label: 'Аудит' },
        ]} />
      {report.error && (
        <Alert type="error" showIcon message={errorMessage(report.error)}
          action={<Button size="small" onClick={() => report.refetch()}>Повторить</Button>} />
      )}
      <Table rowKey={(r: { id?: number; userId?: number }) => String(r.id ?? r.userId)}
        loading={report.isLoading} dataSource={data ?? []} columns={columns as never}
        locale={{ emptyText: 'Нет данных за выбранный период' }} scroll={{ x: true }}
        onChange={onTableChange as never}
        pagination={{
          current: page + 1, pageSize: size, total: report.data?.totalElements ?? 0,
          showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: (t) => `Всего: ${t}`,
        }} />
    </Space>
  )
}
