import { Button, Layout, Menu, Space, Tag, Typography } from 'antd'
import { LogoutOutlined } from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

const { Header, Content } = Layout

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  const items = [
    { key: '/calendar', label: 'Календарь' },
    { key: '/my-statistics', label: 'Моя статистика' },
    ...(user?.role === 'MANAGER'
      ? [
          { key: '/manager/dashboard', label: 'Дашборд' },
          { key: '/manager/reports', label: 'Отчёты' },
          { key: '/manager/settings', label: 'Настройки' },
        ]
      : []),
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '0 16px' }}>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[pathname]}
          items={items}
          onClick={(e) => navigate(e.key)}
          style={{ flex: 1, minWidth: 0 }}
        />
        <Space>
          <Typography.Text style={{ color: '#fff' }} className="user-name">
            {user?.firstName} {user?.lastName}
          </Typography.Text>
          {user?.role === 'MANAGER' && <Tag color="gold">Руководитель</Tag>}
          <Button icon={<LogoutOutlined />} onClick={() => { logout(); navigate('/login') }} aria-label="Выйти">
            <span className="logout-text">Выйти</span>
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: 16, maxWidth: 1200, width: '100%', margin: '0 auto' }}>
        <Outlet />
      </Content>
    </Layout>
  )
}
