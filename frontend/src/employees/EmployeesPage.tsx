import { useState } from 'react'
import { App, Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd'
import { CopyOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { employeeApi, type EmployeeForm } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { Employee, Role } from '../../types/api'
import { formatDateTime } from '../../utils/format'

const ROLE_LABEL: Record<Role, string> = { EMPLOYEE: 'Сотрудник', MANAGER: 'Руководитель' }

export function EmployeesPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [form] = Form.useForm<EmployeeForm>()
  const [q, setQ] = useState('')
  const [role, setRole] = useState<Role | undefined>()
  const [enabled, setEnabled] = useState<boolean | undefined>()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [editing, setEditing] = useState<Employee | 'new' | null>(null)
  const [credentials, setCredentials] = useState<{ name: string; password: string } | null>(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['employees', q, role, enabled, page, size],
    queryFn: () => employeeApi.list({ q: q || undefined, role, enabled, page, size }),
  })

  const reload = () => {
    queryClient.invalidateQueries({ queryKey: ['employees'] })
    // состав сотрудников и записи влияют на календарь, дашборд и командный календарь
    for (const k of ['calendar', 'dashboard', 'team', 'manager-users']) queryClient.invalidateQueries({ queryKey: [k] })
  }
  const fail = (e: unknown) => message.error(errorMessage(e))

  const save = useMutation({
    mutationFn: async (v: EmployeeForm) => {
      if (editing === 'new') return { created: await employeeApi.create(v as Required<EmployeeForm>) }
      await employeeApi.update((editing as Employee).id, { firstName: v.firstName, lastName: v.lastName, role: v.role })
      return { created: null }
    },
    onSuccess: ({ created }) => {
      setEditing(null)
      reload()
      if (created) {
        setCredentials({ name: `${created.employee.firstName} ${created.employee.lastName}`, password: created.temporaryPassword })
      } else {
        message.success('Данные сохранены')
      }
    },
    onError: fail,
  })

  const deactivate = useMutation({
    mutationFn: (id: number) => employeeApi.deactivate(id),
    onSuccess: () => { message.success('Сотрудник отключён, его будущие записи отменены'); reload() },
    onError: fail,
  })
  const activate = useMutation({
    mutationFn: (id: number) => employeeApi.activate(id),
    onSuccess: () => { message.success('Сотрудник включён'); reload() },
    onError: fail,
  })
  const reset = useMutation({
    mutationFn: (e: Employee) => employeeApi.resetPassword(e.id).then((r) => ({ e, r })),
    onSuccess: ({ e, r }) => {
      reload()
      setCredentials({ name: `${e.firstName} ${e.lastName}`, password: r.temporaryPassword })
    },
    onError: fail,
  })

  const openEditor = (target: Employee | 'new') => {
    setEditing(target)
    form.resetFields()
    if (target !== 'new') {
      form.setFieldsValue({ firstName: target.firstName, lastName: target.lastName, role: target.role })
    } else {
      form.setFieldsValue({ role: 'EMPLOYEE' })
    }
  }

  const copy = async () => {
    if (!credentials) return
    try {
      await navigator.clipboard.writeText(credentials.password)
      message.success('Пароль скопирован')
    } catch {
      message.warning('Не удалось скопировать — выделите пароль вручную')
    }
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0 }}>Сотрудники</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor('new')}>Добавить сотрудника</Button>
      </Space>

      {error && (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      )}

      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search allowClear placeholder="Имя или email" style={{ width: 260 }}
            onSearch={(v) => { setQ(v); setPage(0) }} />
          <Select allowClear placeholder="Роль" style={{ width: 160 }} value={role}
            onChange={(v) => { setRole(v); setPage(0) }}
            options={[{ value: 'EMPLOYEE', label: 'Сотрудник' }, { value: 'MANAGER', label: 'Руководитель' }]} />
          <Select allowClear placeholder="Статус" style={{ width: 160 }} value={enabled}
            onChange={(v) => { setEnabled(v); setPage(0) }}
            options={[{ value: true, label: 'Активные' }, { value: false, label: 'Отключённые' }]} />
        </Space>

        <Table<Employee> rowKey="id" loading={isLoading} dataSource={data?.content ?? []} scroll={{ x: true }}
          locale={{ emptyText: 'Сотрудники не найдены' }}
          onChange={(p) => { setPage((p.current ?? 1) - 1); setSize(p.pageSize ?? 20) }}
          pagination={{ current: page + 1, pageSize: size, total: data?.totalElements ?? 0,
            showSizeChanger: true, showTotal: (t) => `Всего: ${t}` }}
          columns={[
            { title: 'Сотрудник', render: (_, e) => `${e.lastName} ${e.firstName}` },
            { title: 'Email', dataIndex: 'email' },
            { title: 'Роль', dataIndex: 'role', render: (r: Role) => <Tag color={r === 'MANAGER' ? 'gold' : 'default'}>{ROLE_LABEL[r]}</Tag> },
            { title: 'Статус', render: (_, e) => (
              <Space size={4} wrap>
                {e.enabled ? <Tag color="green">Активен</Tag> : <Tag color="red">Отключён</Tag>}
                {e.mustChangePassword && <Tag color="orange">Временный пароль</Tag>}
              </Space>) },
            { title: 'Создан', dataIndex: 'createdAt', render: formatDateTime },
            { title: 'Действия', key: 'actions', render: (_, e) => (
              <Space wrap size={4}>
                <Button size="small" onClick={() => openEditor(e)}>Изменить</Button>
                <Popconfirm title="Сбросить пароль?" description="Будет создан новый временный пароль. Старый перестанет работать."
                  okText="Сбросить" cancelText="Назад" onConfirm={() => reset.mutate(e)}>
                  <Button size="small">Сбросить пароль</Button>
                </Popconfirm>
                {e.enabled ? (
                  <Popconfirm title="Отключить сотрудника?"
                    description="Он не сможет войти, а все его будущие записи будут отменены. История сохранится."
                    okText="Отключить" okButtonProps={{ danger: true }} cancelText="Назад"
                    onConfirm={() => deactivate.mutate(e.id)}>
                    <Button size="small" danger>Отключить</Button>
                  </Popconfirm>
                ) : (
                  <Button size="small" onClick={() => activate.mutate(e.id)}>Включить</Button>
                )}
              </Space>) },
          ]} />
      </Card>

      <Modal open={editing !== null} destroyOnClose onCancel={() => setEditing(null)}
        title={editing === 'new' ? 'Новый сотрудник' : 'Редактирование сотрудника'}
        okText="Сохранить" cancelText="Отмена" confirmLoading={save.isPending}
        onOk={() => form.validateFields().then((v) => save.mutate(v)).catch(() => undefined)}>
        <Form<EmployeeForm> form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="lastName" label="Фамилия" rules={[{ required: true, message: 'Укажите фамилию' }, { max: 100 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="firstName" label="Имя" rules={[{ required: true, message: 'Укажите имя' }, { max: 100 }]}>
            <Input />
          </Form.Item>
          {editing === 'new' && (
            <Form.Item name="email" label="Email"
              rules={[{ required: true, message: 'Укажите email' }, { type: 'email', message: 'Некорректный email' }]}
              extra="Временный пароль будет показан после создания. Сотрудник сменит его при первом входе.">
              <Input autoComplete="off" />
            </Form.Item>
          )}
          <Form.Item name="role" label="Роль" rules={[{ required: true, message: 'Выберите роль' }]}>
            <Select options={[{ value: 'EMPLOYEE', label: 'Сотрудник' }, { value: 'MANAGER', label: 'Руководитель' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal open={credentials !== null} onCancel={() => setCredentials(null)} maskClosable={false}
        title="Временный пароль" footer={<Button type="primary" onClick={() => setCredentials(null)}>Готово</Button>}>
        <Alert type="warning" showIcon style={{ marginBottom: 12 }}
          message="Пароль показывается один раз. Передайте его сотруднику безопасным способом." />
        <Typography.Paragraph>Сотрудник: <b>{credentials?.name}</b></Typography.Paragraph>
        <Space.Compact style={{ width: '100%' }}>
          <Input readOnly value={credentials?.password} aria-label="Временный пароль"
            style={{ fontFamily: 'monospace' }} />
          <Button icon={<CopyOutlined />} onClick={copy}>Копировать</Button>
        </Space.Compact>
      </Modal>
    </Space>
  )
}
