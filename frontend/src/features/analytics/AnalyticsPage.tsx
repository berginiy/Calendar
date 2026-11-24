import { useState } from 'react'
import { Alert, App, Button, Card, Col, DatePicker, Empty, Progress, Row, Space, Statistic, Table, Typography } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { analyticsApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { DayStat, PeriodStat, WeekdayStat } from '../../types/api'
import { DATE_FMT, DAY_NAME, formatDate, todayBishkek } from '../../utils/format'

const bar = (v: number) => <Progress percent={v} size="small" format={(p) => `${p}%`} />

export function AnalyticsPage() {
  const { message } = App.useApp()
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => {
    const t = dayjs(todayBishkek())
    return [t.startOf('month'), t.endOf('month')]
  })
  const [downloading, setDownloading] = useState(false)
  const from = range[0].format(DATE_FMT)
  const to = range[1].format(DATE_FMT)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['analytics', from, to],
    queryFn: () => analyticsApi.get(from, to),
  })

  const exportCsv = async () => {
    setDownloading(true)
    try {
      const blob = await analyticsApi.csv(from, to)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `officeflow-analytics-${from}_${to}.csv`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      message.error(errorMessage(e))
    } finally {
      setDownloading(false)
    }
  }

  const periodColumns = (label: string) => [
    { title: label, dataIndex: 'period', render: (_: string, r: PeriodStat) => `${formatDate(r.from)} — ${formatDate(r.to)}` },
    { title: 'Рабочих дней', dataIndex: 'workingDays' },
    { title: 'Бронирований', dataIndex: 'office' },
    { title: 'Удалённых дней', dataIndex: 'remote' },
    { title: 'Загрузка', dataIndex: 'utilization', width: 200, render: bar },
  ]

  const dayColumns = [
    { title: 'Дата', dataIndex: 'date', render: (d: string) => formatDate(d) },
    { title: 'Забронировано', dataIndex: 'office' },
    { title: 'Загрузка', dataIndex: 'utilization', width: 200, render: bar },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Аналитика</Typography.Title>
        <Space wrap>
          <DatePicker.RangePicker value={range} allowClear={false}
            onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])} />
          <Button icon={<DownloadOutlined />} onClick={exportCsv} loading={downloading}>Скачать CSV</Button>
        </Space>
      </Space>

      <Alert type="info" showIcon
        message="Показатели построены по планируемым бронированиям. Это не фактическое присутствие сотрудников в офисе." />

      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Загрузка офиса" suffix="%" value={data?.overallUtilization} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Офисных бронирований" value={data?.officeBookings} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Удалённых дней" value={data?.remoteDays} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Отменённых записей" value={data?.cancelled} /></Card></Col>
      </Row>

      <Card title="Загрузка по дням недели" loading={isLoading}>
        {data && data.byWeekday.length === 0 ? <Empty description="В периоде нет рабочих дней" /> : (
          <Table<WeekdayStat> rowKey="day" size="small" pagination={false} scroll={{ x: true }}
            dataSource={data?.byWeekday ?? []}
            columns={[
              { title: 'День недели', dataIndex: 'day', render: (d: string) => DAY_NAME[d] ?? d },
              { title: 'Дней в периоде', dataIndex: 'days' },
              { title: 'В среднем занято мест', dataIndex: 'avgOccupied' },
              { title: 'Максимум', dataIndex: 'maxOccupied' },
              { title: 'Средняя загрузка', dataIndex: 'avgUtilization', width: 200, render: bar },
            ]} />
        )}
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title="Наибольший спрос" loading={isLoading}>
            <Table<DayStat> rowKey="date" size="small" pagination={false} dataSource={data?.topDemand ?? []}
              columns={dayColumns} scroll={{ x: true }} />
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="Низкая загрузка" loading={isLoading}>
            <Table<DayStat> rowKey="date" size="small" pagination={false} dataSource={data?.lowDemand ?? []}
              columns={dayColumns} scroll={{ x: true }} />
          </Card>
        </Col>
      </Row>

      <Card title="Динамика по неделям" loading={isLoading}>
        <Table<PeriodStat> rowKey="period" size="small" pagination={{ pageSize: 10, hideOnSinglePage: true }}
          dataSource={data?.weekly ?? []} columns={periodColumns('Неделя')} scroll={{ x: true }} />
      </Card>
      <Card title="Динамика по месяцам" loading={isLoading}>
        <Table<PeriodStat> rowKey="period" size="small" pagination={false}
          dataSource={data?.monthly ?? []} columns={periodColumns('Период')} scroll={{ x: true }} />
      </Card>
    </Space>
  )
}
