import { useState } from 'react'
import { Alert, Button, Card, Col, DatePicker, Input, Row, Segmented, Space, Statistic, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { managerApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { DashboardEmployee, MyStatus } from '../../types/api'
import { DATE_FMT, STATUS_LABEL, todayBishkek } from '../../utils/format'

const STATUS_COLOR: Record<MyStatus, string> = { OFFICE: 'purple', REMOTE: 'blue', NONE: 'default' }

export function DashboardPage() {
  const [date, setDate] = useState<Dayjs>(() => dayjs(todayBishkek()))
  const [status, setStatus] = useState<string>('')
  const [search, setSearch] = useState('')
  const d = date.format(DATE_FMT)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['dashboard', d, status, search],
    queryFn: () => managerApi.dashboard(d, status || undefined, search || undefined),
  })

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Дашборд руководителя</Typography.Title>
        <DatePicker value={date} allowClear={false} onChange={(v) => v && setDate(v)} format="DD.MM.YYYY" />
      </Space>
      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}
      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Всего сотрудников" value={data?.totalEmployees} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="В офисе" value={data?.inOffice} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Удалённо" value={data?.remote} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Без статуса" value={data?.notSelected} /></Card></Col>
        <Col xs={12} md={8}><Card loading={isLoading}><Statistic title="Всего мест" value={data?.totalSeats} /></Card></Col>
        <Col xs={12} md={8}><Card loading={isLoading}><Statistic title="Занято мест" value={data?.occupiedSeats} /></Card></Col>
        <Col xs={24} md={8}><Card loading={isLoading}><Statistic title="Свободно мест" value={data?.freeSeats} /></Card></Col>
      </Row>
      <Card title="Сотрудники">
        <Space wrap style={{ marginBottom: 16 }}>
          <Segmented value={status} onChange={(v) => setStatus(String(v))}
            options={[
              { label: 'Все', value: '' },
              { label: 'В офисе', value: 'OFFICE' },
              { label: 'Удалённо', value: 'REMOTE' },
              { label: 'Не выбрано', value: 'NONE' },
            ]} />
          <Input.Search allowClear placeholder="Имя или email" onSearch={setSearch} style={{ width: 260 }} />
        </Space>
        <Table<DashboardEmployee> rowKey="userId" loading={isLoading} dataSource={data?.employees ?? []}
          locale={{ emptyText: 'Сотрудники не найдены' }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
          scroll={{ x: true }}
          columns={[
            { title: 'Сотрудник', dataIndex: 'fullName' },
            { title: 'Email', dataIndex: 'email' },
            { title: 'Статус', dataIndex: 'status', render: (s: MyStatus) => <Tag color={STATUS_COLOR[s]}>{STATUS_LABEL[s]}</Tag> },
          ]} />
      </Card>
    </Space>
  )
}
