import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layouts/AppLayout'
import { LoginPage } from './features/auth/LoginPage'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { CalendarPage } from './features/calendar/CalendarPage'
import { MyStatisticsPage } from './features/statistics/MyStatisticsPage'
import { DashboardPage } from './features/manager-dashboard/DashboardPage'
import { ReportsPage } from './features/reports/ReportsPage'
import { SettingsPage } from './features/settings/SettingsPage'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/my-statistics" element={<MyStatisticsPage />} />
          <Route element={<ProtectedRoute roles={['MANAGER']} />}>
            <Route path="/manager/dashboard" element={<DashboardPage />} />
            <Route path="/manager/reports" element={<ReportsPage />} />
            <Route path="/manager/settings" element={<SettingsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/calendar" replace />} />
    </Routes>
  )
}
