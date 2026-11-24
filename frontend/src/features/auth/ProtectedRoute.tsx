import { Button, Result, Spin } from 'antd'
import { Link, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import type { Role } from '../../types/api'

/** Защита маршрутов на клиенте. Настоящая проверка прав выполняется на backend. */
export function ProtectedRoute({ roles }: { roles?: Role[] }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <Spin fullscreen tip="Загрузка…" />
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  // временный пароль нужно сменить до любой другой работы (backend тоже блокирует остальные запросы)
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  if (roles && !roles.includes(user.role)) {
    return (
      <Result
        status="403"
        title="Нет доступа"
        subTitle="У вас недостаточно прав для просмотра этой страницы"
        extra={<Link to="/calendar"><Button type="primary">К календарю</Button></Link>}
      />
    )
  }
  return <Outlet />
}
