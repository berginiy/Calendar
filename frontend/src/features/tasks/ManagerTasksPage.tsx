import { useState } from 'react'
import {
  Alert, App, Button, Card, Col, DatePicker, Form, Input, Modal, Popconfirm, Progress, Row, Select, Space,
  Statistic, Table, Tag, TimePicker, Typography,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { managerApi, managerTasksApi, type ManagerTaskForm } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { Task, TaskPriority, TaskStatus } from '../../types/api'
import { DATE_FMT, formatDate, todayBishkek } from '../../utils/format'
import { PRIORITY_OPTIONS, PriorityTag, STATUS_LABEL, STATUS_OPTIONS, shortTime, toTime } from './taskUi'

interface FormValues {
  assigneeId: number
  title: string
  description?: string
  taskDate: Dayjs
  dueTime?: Dayjs
  priority: TaskPriority
  status: TaskStatus
}

const STATUS_COLOR: Record<TaskStatus, string> = { TODO: 'default', IN_PROGRESS: 'processing', DONE: 'success' }

export function ManagerTasksPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs(todayBishkek()), dayjs(todayBishkek())])
  const [assigneeId, setAssigneeId] = useState<number | undefined>()
  const [status, setStatus] = useState<TaskStatus | undefined>()
  const [priority, setPriority] = useState<TaskPriority | undefined>()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [editing, setEditing] = useState<Task | 'new' | null>(null)

  const from = range[0].format(DATE_FMT)
  const to = range[1].format(DATE_FMT)

  const users = useQuery({ queryKey: ['manager-users'], queryFn: managerApi.users })
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['manager-tasks', from, to, assigneeId, status, priority, page, size],
    queryFn: () => managerTasksApi.list({ from, to, assigneeId, status, priority, page, size }),
  })

  const reload = () => queryClient.invalidateQueries({ queryKey: ['manager-tasks'] })
  const fail = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body: ManagerTaskForm = {
        assigneeId: v.assigneeId, title: v.title, description: v.description,
        taskDate: v.taskDate.format(DATE_FMT), dueTime: v.dueTime ? v.dueTime.format('HH:mm') : undefined,
        priority: v.priority, status: v.status,
      }
      return editing === 'new' ? managerTasksApi.create(body) : managerTasksApi.update((editing as Task).id, body)
    },
    onSuccess: () => { message.success('Задача сохранена'); setEditing(null); reload() },
    onError: fail,
  })
  const remove = useMutation({
    mutationFn: (id: number) => managerTasksApi.remove(id),
    onSuccess: () => { message.success('Задача удалена'); reload() },
    onError: fail,
  })

  const open = (target: Task | 'new') => {
    setEditing(target)
    form.resetFields()
    if (target === 'new') {
      form.setFieldsValue({ taskDate: range[0], priority: 'NORMAL', status: 'TODO' })
    } else {
      form.setFieldsValue({
        assigneeId: target.assigneeId, title: target.title, description: target.description ?? undefined,
        taskDate: dayjs(target.taskDate), dueTime: toTime(target.dueTime), priority: target.priority, status: target.status,
      })
    }
  }

  const p = data?.progress
  const assigneeOptions = (users.data ?? []).filter((u) => u.enabled)
    .map((u) => ({ value: u.id, label: `${u.lastName} ${u.firstName}` }))

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Задачи команды</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>Назначить задачу</Button>
      </Space>

      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}

      <Card>
        <Space wrap>
          <DatePicker.RangePicker value={range} allowClear={false}
            onChange={(v) => { if (v && v[0] && v[1]) { setRange([v[0], v[1]]); setPage(0) } }} />
          <Select allowClear showSearch placeholder="Все сотрудники" style={{ minWidth: 220 }} optionFilterProp="label"
            value={assigneeId} options={assigneeOptions} onChange={(v) => { setAssigneeId(v); setPage(0) }} />
          <Select allowClear placeholder="Любой статус" style={{ width: 160 }} value={status} options={STATUS_OPTIONS}
            onChange={(v) => { setStatus(v); setPage(0) }} />
          <Select allowClear placeholder="Любой приоритет" style={{ width: 170 }} value={priority} options={PRIORITY_OPTIONS}
            onChange={(v) => { setPriority(v); setPage(0) }} />
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Всего задач" value={p?.total} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="Выполнено" value={p?.done} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="В работе" value={p?.inProgress} /></Card></Col>
        <Col xs={12} md={6}><Card loading={isLoading}><Statistic title="К выполнению" value={p?.todo} /></Card></Col>
      </Row>
      <Progress percent={p?.percent ?? 0} aria-label="Общий прогресс по задачам" />

      <Card>
        <Table<Task> rowKey="id" loading={isLoading} dataSource={data?.tasks.content ?? []} scroll={{ x: true }}
          locale={{ emptyText: 'Задач за выбранный период нет' }}
          onChange={(pg) => { setPage((pg.current ?? 1) - 1); setSize(pg.pageSize ?? 20) }}
          pagination={{ current: page + 1, pageSize: size, total: data?.tasks.totalElements ?? 0,
            showSizeChanger: true, showTotal: (t) => `Всего: ${t}` }}
          columns={[
            { title: 'Дата', dataIndex: 'taskDate', render: (v: string, t) => `${formatDate(v)}${t.dueTime ? ' ' + shortTime(t.dueTime) : ''}` },
            { title: 'Задача', render: (_, t) => (
              <div>
                <Typography.Text strong>{t.title}</Typography.Text>
                {t.description && <div><Typography.Text type="secondary">{t.description}</Typography.Text></div>}
              </div>) },
            { title: 'Исполнитель', dataIndex: 'assigneeName' },
            { title: 'Приоритет', dataIndex: 'priority', render: (v: TaskPriority) => <PriorityTag priority={v} /> },
            { title: 'Статус', dataIndex: 'status', render: (v: TaskStatus) => <Tag color={STATUS_COLOR[v]}>{STATUS_LABEL[v]}</Tag> },
            { title: 'Постановщик', dataIndex: 'createdByName' },
            { title: '', key: 'actions', render: (_, t) => (
              <Space>
                <Button size="small" onClick={() => open(t)}>Изменить</Button>
                <Popconfirm title="Удалить задачу?" okText="Удалить" cancelText="Назад" okButtonProps={{ danger: true }}
                  onConfirm={() => remove.mutate(t.id)}>
                  <Button size="small" danger>Удалить</Button>
                </Popconfirm>
              </Space>) },
          ]} />
      </Card>

      <Modal open={editing !== null} destroyOnClose onCancel={() => setEditing(null)}
        title={editing === 'new' ? 'Новая задача' : 'Редактирование задачи'}
        okText="Сохранить" cancelText="Отмена" confirmLoading={save.isPending}
        onOk={() => form.validateFields().then((v) => save.mutate(v)).catch(() => undefined)}>
        <Form<FormValues> form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="assigneeId" label="Исполнитель" rules={[{ required: true, message: 'Выберите сотрудника' }]}>
            <Select showSearch optionFilterProp="label" options={assigneeOptions} placeholder="Сотрудник" />
          </Form.Item>
          <Form.Item name="title" label="Название"
            rules={[{ required: true, whitespace: true, message: 'Укажите название задачи' }, { max: 200, message: 'Не длиннее 200 символов' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Описание" rules={[{ max: 2000, message: 'Не длиннее 2000 символов' }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Space wrap>
            <Form.Item name="taskDate" label="Дата" rules={[{ required: true, message: 'Укажите дату' }]}>
              <DatePicker format="DD.MM.YYYY" allowClear={false} />
            </Form.Item>
            <Form.Item name="dueTime" label="Время"><TimePicker format="HH:mm" minuteStep={5} /></Form.Item>
            <Form.Item name="priority" label="Приоритет"><Select options={PRIORITY_OPTIONS} style={{ width: 140 }} /></Form.Item>
            <Form.Item name="status" label="Статус"><Select options={STATUS_OPTIONS} style={{ width: 150 }} /></Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  )
}
