import { useState } from 'react'
import { App, Alert, Button, Card, Col, DatePicker, Popconfirm, Progress, Row, Space, Statistic, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { presenceApi, statsApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { LimitUsage, PresenceDto } from '../../types/api'
import { DATE_FMT, formatDate, formatDateTime, todayBishkek } from '../../utils/format'

function LimitCard({ title, usage }: { title: string; usage?: LimitUsage }) {
  if (!usage) return <Card title={title} loading />
  return (
    <Card title={title}>
      {usage.unlimited ? (
        <>
          <Statistic title="Использовано" value={usage.used} />
          <Typography.Text type="secondary">Без ограничений</Typography.Text>
        </>
      ) : (
        <>
          <Row gutter={16}>
            <Col span={8}><Statistic title="Использовано" value={usage.used} /></Col>
            <Col span={8}><Statistic title="Лимит" value={usage.max ?? 0} /></Col>
            <Col span={8}><Statistic title="Осталось" value={usage.remaining ?? 0} /></Col>
          </Row>
          <Progress percent={usage.max ? Math.min(100, Math.round((usage.used / usage.max) * 100)) : 100}
            status={usage.remaining === 0 ? 'exception' : 'normal'} style={{ marginTop: 12 }} />
        </>
      )}
      <Typography.Text type="secondary">
        {formatDate(usage.periodFrom)} — {formatDate(usage.periodTo)}
      </Typography.Text>
    </Card>
  )
}

export function MyStatisticsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => {
    const t = dayjs(todayBishkek())
    return [t.startOf('month'), t.endOf('month')]
  })
  const from = range[0].format(DATE_FMT)
  const to = range[1].format(DATE_FMT)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['my-stats', from, to],
    queryFn: () => statsApi.my(from, to),
  })

  const cancel = useMutation({
    mutationFn: (id: number) => presenceApi.cancelRemote(id),
    onSuccess: () => {
      message.success('Отметка об удалённой работе отменена')
      queryClient.invalidateQueries({ queryKey: ['my-stats'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
    onError: (e) => message.error(errorMessage(e)),
  })

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Title level={3} style={{ margin: 0 }}>Моя статистика удалённой работы</Typography.Title>
      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}><LimitCard title="Текущая неделя" usage={data?.week} /></Col>
        <Col xs={24} md={12}><LimitCard title="Текущий месяц" usage={data?.month} /></Col>
      </Row>
      <Card title="История удалённых дней"
        extra={
          <DatePicker.RangePicker value={range} allowClear={false}
            onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])} />
        }>
        <Table<PresenceDto> rowKey="id" loading={isLoading} dataSource={data?.remoteDays ?? []}
          locale={{ emptyText: 'За выбранный период удалённых дней нет' }}
          pagination={{ pageSize: 10, hideOnSinglePage: true }} scroll={{ x: true }}
          columns={[
            { title: 'Дата', dataIndex: 'date', render: formatDate },
            { title: 'Статус', dataIndex: 'status',
              render: (s) => s === 'ACTIVE' ? <Tag color="blue">Активна</Tag> : <Tag>Отменена</Tag> },
            { title: 'Создана', dataIndex: 'createdAt', render: formatDateTime },
            { title: 'Отменена', dataIndex: 'cancelledAt', render: formatDateTime },
            { title: '', key: 'actions', render: (_, r) => r.status === 'ACTIVE' && (
              <Popconfirm title="Отменить отметку?" okText="Отменить" cancelText="Назад" onConfirm={() => cancel.mutate(r.id)}>
                <Button size="small" danger loading={cancel.isPending}>Отменить</Button>
              </Popconfirm>
            ) },
          ]} />
      </Card>
    </Space>
  )
}
