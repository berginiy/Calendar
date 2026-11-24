import { useState } from 'react'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { LockOutlined, MailOutlined } from '@ant-design/icons'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { errorMessage } from '../../api/client'

interface LoginForm {
  email: string
  password: string
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const target = (location.state as { from?: string } | null)?.from ?? '/calendar'

  if (user) return <Navigate to={target} replace />

  const onFinish = async (values: LoginForm) => {
    setSubmitting(true)
    setError(null)
    try {
      await login(values.email.trim(), values.password)
      navigate(target, { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 16, background: '#f5f5f5' }}>
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginTop: 0 }}>
          Бронирование мест
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
          Войдите, чтобы выбрать офис или удалённую работу
        </Typography.Paragraph>
        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
        <Form<LoginForm> layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            name="email"
            label="Email"
            rules={[{ required: true, message: 'Введите email' }, { type: 'email', message: 'Некорректный email' }]}
          >
            <Input prefix={<MailOutlined />} autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="Пароль" rules={[{ required: true, message: 'Введите пароль' }]}>
            <Input.Password prefix={<LockOutlined />} autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            Войти
          </Button>
        </Form>
      </Card>
    </div>
  )
}
