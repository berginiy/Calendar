import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layouts/AppLayout'
import { LoginPage } from './features/auth/LoginPage'
import { ChangePasswordPage } from './features/auth/ChangePasswordPage'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { CalendarPage } from './features/calendar/CalendarPage'
import { TeamPage } from './features/team/TeamPage'
import { MyStatisticsPage } from './features/statistics/MyStatisticsPage'
import { DashboardPage } from './features/manager-dashboard/DashboardPage'
import { AnalyticsPage } from './features/analytics/AnalyticsPage'
import { EmployeesPage } from './features/employees/EmployeesPage'
import { ReportsPage } from './features/reports/ReportsPage'
import { SettingsPage } from './features/settings/SettingsPage'
import { TasksPage } from './features/tasks/TasksPage'
import { ManagerTasksPage } from './features/tasks/ManagerTasksPage'
import { PreferencesPage } from './features/preferences/PreferencesPage'
import { AllocationPage } from './features/allocation/AllocationPage'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route element={<AppLayout />}>
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/team" element={<TeamPage />} />
          <Route path="/my-statistics" element={<MyStatisticsPage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="/preferences" element={<PreferencesPage />} />
          <Route element={<ProtectedRoute roles={['MANAGER']} />}>
            <Route path="/manager/dashboard" element={<DashboardPage />} />
            <Route path="/manager/analytics" element={<AnalyticsPage />} />
            <Route path="/manager/tasks" element={<ManagerTasksPage />} />
            <Route path="/manager/allocation" element={<AllocationPage />} />
            <Route path="/manager/reports" element={<ReportsPage />} />
            <Route path="/manager/employees" element={<EmployeesPage />} />
            <Route path="/manager/settings" element={<SettingsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/calendar" replace />} />
    </Routes>
  )
}
