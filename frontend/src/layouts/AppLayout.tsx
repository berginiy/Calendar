import { Avatar, Dropdown, Layout, Menu, Space, Tag, Typography } from 'antd'
import {
  BarChartOutlined, CalendarOutlined, DashboardOutlined, FileTextOutlined, LogoutOutlined, SettingOutlined,
  TeamOutlined, UserOutlined, LineChartOutlined, KeyOutlined, IdcardOutlined, CheckSquareOutlined, SwapOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { NotificationBell } from './NotificationBell'

const { Header, Content, Sider } = Layout

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const isManager = user?.role === 'MANAGER'

  const items = [
    { key: '/calendar', icon: <CalendarOutlined />, label: 'Календарь' },
    { key: '/team', icon: <TeamOutlined />, label: 'Команда' },
    { key: '/preferences', icon: <SwapOutlined />, label: 'Предпочтения' },
    { key: '/tasks', icon: <CheckSquareOutlined />, label: 'Мои задачи' },
    { key: '/my-statistics', icon: <LineChartOutlined />, label: 'Моя статистика' },
    ...(isManager
      ? [{
          type: 'group' as const, key: 'manager', label: 'Руководитель',
          children: [
            { key: '/manager/dashboard', icon: <DashboardOutlined />, label: 'Дашборд' },
            { key: '/manager/allocation', icon: <SwapOutlined />, label: 'Распределение' },
            { key: '/manager/tasks', icon: <CheckSquareOutlined />, label: 'Задачи команды' },
            { key: '/manager/analytics', icon: <BarChartOutlined />, label: 'Аналитика' },
            { key: '/manager/reports', icon: <FileTextOutlined />, label: 'Отчёты' },
            { key: '/manager/employees', icon: <IdcardOutlined />, label: 'Сотрудники' },
            { key: '/manager/settings', icon: <SettingOutlined />, label: 'Настройки' },
          ],
        }]
      : []),
  ]

  const profileMenu = {
    items: [
      { key: 'password', icon: <KeyOutlined />, label: 'Сменить пароль' },
      { key: 'logout', icon: <LogoutOutlined />, label: 'Выйти' },
    ],
    onClick: ({ key }: { key: string }) => {
      if (key === 'password') navigate('/change-password')
      if (key === 'logout') { logout(); navigate('/login') }
    },
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* на узких экранах панель сворачивается и открывается кнопкой-«язычком» */}
      <Sider className="app-sider" breakpoint="lg" collapsedWidth={0} width={230} theme="light"
        style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: '16px 20px', fontWeight: 700, fontSize: 18 }}>OfficeFlow</div>
        <Menu mode="inline" selectedKeys={[pathname]} items={items} onClick={(e) => navigate(e.key)}
          style={{ borderInlineEnd: 0 }} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 12,
          padding: '0 16px', borderBottom: '1px solid #f0f0f0' }}>
          <NotificationBell />
          <Dropdown menu={profileMenu} trigger={['click']}>
            <Space style={{ cursor: 'pointer' }} role="button" tabIndex={0} aria-label="Меню профиля">
              <Avatar icon={<UserOutlined />} />
              <Typography.Text className="user-name">{user?.firstName} {user?.lastName}</Typography.Text>
              {isManager && <Tag color="gold" style={{ marginInlineEnd: 0 }}>Руководитель</Tag>}
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ padding: 16, maxWidth: 1200, width: '100%', margin: '0 auto' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
