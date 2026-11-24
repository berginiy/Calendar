import { useState } from 'react'
import { Alert, App, Button, Card, Form, Input, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import { useAuth } from './AuthContext'

interface PasswordForm {
  currentPassword: string
  newPassword: string
  confirm: string
}

export function ChangePasswordPage() {
  const { user, logout, passwordChanged } = useAuth()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const forced = user?.mustChangePassword ?? false

  const onFinish = async (v: PasswordForm) => {
    setSubmitting(true)
    setError(null)
    try {
      await authApi.changePassword(v.currentPassword, v.newPassword)
      passwordChanged()
      message.success('Пароль изменён')
      navigate('/calendar', { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 16, background: '#f5f5f5' }}>
      <Card style={{ width: '100%', maxWidth: 420 }}>
        <Typography.Title level={3} style={{ marginTop: 0 }}>Смена пароля</Typography.Title>
        {forced && (
          <Alert type="info" showIcon style={{ marginBottom: 16 }}
            message="Вы вошли с временным паролем. Придумайте свой пароль, чтобы продолжить работу." />
        )}
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
        <Form<PasswordForm> layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="currentPassword" label={forced ? 'Временный пароль' : 'Текущий пароль'}
            rules={[{ required: true, message: 'Введите текущий пароль' }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item name="newPassword" label="Новый пароль" hasFeedback
            rules={[{ required: true, message: 'Введите новый пароль' },
              { min: 8, message: 'Не менее 8 символов' }, { max: 72, message: 'Не более 72 символов' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="confirm" label="Повторите новый пароль" dependencies={['newPassword']} hasFeedback
            rules={[{ required: true, message: 'Повторите пароль' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || getFieldValue('newPassword') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('Пароли не совпадают')),
              })]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>Сохранить пароль</Button>
          {forced ? (
            <Button type="link" block onClick={() => { logout(); navigate('/login') }}>Выйти</Button>
          ) : (
            <Button type="link" block onClick={() => navigate(-1)}>Отмена</Button>
          )}
        </Form>
      </Card>
    </div>
  )
}
