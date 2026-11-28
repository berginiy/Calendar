import { useState } from 'react'
import {
  Alert, App, Button, Card, Checkbox, DatePicker, Empty, Form, Input, List, Modal, Popconfirm, Progress, Select,
  Space, Spin, TimePicker, Typography,
} from 'antd'
import { LeftOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { tasksApi, type TaskForm } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { Task, TaskPriority, TaskStatus } from '../../types/api'
import { DATE_FMT, formatDate, todayBishkek } from '../../utils/format'
import { PRIORITY_OPTIONS, PriorityTag, STATUS_OPTIONS, shortTime, toTime } from './taskUi'

interface FormValues {
  title: string
  description?: string
  taskDate: Dayjs
  dueTime?: Dayjs
  priority: TaskPriority
}

export function TasksPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [date, setDate] = useState(() => dayjs(todayBishkek()))
  const [editing, setEditing] = useState<Task | 'new' | null>(null)
  const [form] = Form.useForm<FormValues>()
  const d = date.format(DATE_FMT)

  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['tasks', d], queryFn: () => tasksApi.day(d) })

  const reload = () => queryClient.invalidateQueries({ queryKey: ['tasks'] })
  const fail = (e: unknown) => message.error(errorMessage(e))

  const setStatus = useMutation({
    mutationFn: (v: { id: number; status: TaskStatus }) => tasksApi.setStatus(v.id, v.status),
    onSuccess: reload,
    onError: fail,
  })
  const remove = useMutation({
    mutationFn: (id: number) => tasksApi.remove(id),
    onSuccess: () => { message.success('Задача удалена'); reload() },
    onError: fail,
  })
  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body: TaskForm = {
        title: v.title,
        description: v.description,
        taskDate: v.taskDate.format(DATE_FMT),
        dueTime: v.dueTime ? v.dueTime.format('HH:mm') : undefined,
        priority: v.priority,
      }
      return editing === 'new' ? tasksApi.create(body) : tasksApi.update((editing as Task).id, body)
    },
    onSuccess: () => { message.success('Задача сохранена'); setEditing(null); reload() },
    onError: fail,
  })

  const open = (target: Task | 'new') => {
    setEditing(target)
    form.resetFields()
    if (target === 'new') {
      form.setFieldsValue({ taskDate: date, priority: 'NORMAL' })
    } else {
      form.setFieldsValue({
        title: target.title, description: target.description ?? undefined, taskDate: dayjs(target.taskDate),
        dueTime: toTime(target.dueTime), priority: target.priority,
      })
    }
  }

  const p = data?.progress

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Мои задачи</Typography.Title>
        <Space wrap>
          <Button icon={<LeftOutlined />} aria-label="Предыдущий день" onClick={() => setDate(date.subtract(1, 'day'))} />
          <DatePicker value={date} allowClear={false} format="DD.MM.YYYY" onChange={(v) => v && setDate(v)} />
          <Button onClick={() => setDate(dayjs(todayBishkek()))}>Сегодня</Button>
          <Button icon={<RightOutlined />} aria-label="Следующий день" onClick={() => setDate(date.add(1, 'day'))} />
        </Space>
      </Space>

      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}

      <Card>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text strong>
            {p && p.total > 0 ? `Выполнено ${p.done} из ${p.total}` : 'На этот день задач нет'}
          </Typography.Text>
          <Progress percent={p?.percent ?? 0} aria-label="Прогресс выполнения задач" />
        </Space>
      </Card>

      <Card title={formatDate(d)} extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>Добавить задачу</Button>}>
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : (data?.tasks.length ?? 0) === 0 ? (
          <Empty description="Задач на этот день пока нет" />
        ) : (
          <List<Task> dataSource={data?.tasks ?? []} renderItem={(t) => (
            <List.Item key={t.id} actions={[
              t.canEdit && <Button key="e" size="small" onClick={() => open(t)}>Изменить</Button>,
              t.canDelete && (
                <Popconfirm key="d" title="Удалить задачу?" okText="Удалить" cancelText="Назад"
                  okButtonProps={{ danger: true }} onConfirm={() => remove.mutate(t.id)}>
                  <Button size="small" danger>Удалить</Button>
                </Popconfirm>),
            ].filter(Boolean)}>
              <Space align="start" size="middle" style={{ width: '100%' }}>
                <Checkbox checked={t.status === 'DONE'} disabled={!t.canChangeStatus}
                  aria-label={`Выполнено: ${t.title}`}
                  onChange={(e) => setStatus.mutate({ id: t.id, status: e.target.checked ? 'DONE' : 'TODO' })} />
                <div>
                  <Space wrap>
                    <Typography.Text delete={t.status === 'DONE'} strong>{t.title}</Typography.Text>
                    <PriorityTag priority={t.priority} />
                    {t.dueTime && <Typography.Text type="secondary">до {shortTime(t.dueTime)}</Typography.Text>}
                  </Space>
                  {t.description && <div><Typography.Text type="secondary">{t.description}</Typography.Text></div>}
                  {t.createdById !== t.assigneeId && (
                    <div><Typography.Text type="secondary">Назначил: {t.createdByName}</Typography.Text></div>
                  )}
                  <Select size="small" value={t.status} options={STATUS_OPTIONS} disabled={!t.canChangeStatus}
                    style={{ width: 150, marginTop: 6 }} aria-label={`Статус: ${t.title}`}
                    onChange={(status) => setStatus.mutate({ id: t.id, status })} />
                </div>
              </Space>
            </List.Item>
          )} />
        )}
      </Card>

      <Modal open={editing !== null} destroyOnClose onCancel={() => setEditing(null)}
        title={editing === 'new' ? 'Новая задача' : 'Редактирование задачи'}
        okText="Сохранить" cancelText="Отмена" confirmLoading={save.isPending}
        onOk={() => form.validateFields().then((v) => save.mutate(v)).catch(() => undefined)}>
        <Form<FormValues> form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="title" label="Название"
            rules={[{ required: true, whitespace: true, message: 'Укажите название задачи' }, { max: 200, message: 'Не длиннее 200 символов' }]}>
            <Input autoFocus />
          </Form.Item>
          <Form.Item name="description" label="Описание" rules={[{ max: 2000, message: 'Не длиннее 2000 символов' }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Space wrap>
            <Form.Item name="taskDate" label="Дата" rules={[{ required: true, message: 'Укажите дату' }]}>
              <DatePicker format="DD.MM.YYYY" allowClear={false} />
            </Form.Item>
            <Form.Item name="dueTime" label="Время (необязательно)">
              <TimePicker format="HH:mm" minuteStep={5} />
            </Form.Item>
            <Form.Item name="priority" label="Приоритет">
              <Select options={PRIORITY_OPTIONS} style={{ width: 140 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Space>
  )
}
