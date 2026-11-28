import { api } from './client'
import type {
  AllocationOverview, AllocationRun, AllocationSettings, Analytics, AuditRow, DayTasks, ManagerTasks,
  NotificationsResponse, Outcome, PreferenceDay, PreferencesResponse, PreferenceType, RunDetail, Task, TaskPriority,
  TaskStatus, CalendarDay, CredentialsResponse, Dashboard, Employee, LimitRule, LoginResponse, MyStats, PageResponse, PresenceDto,
  PresenceStatus, ReportRow, Role, Settings, SummaryRow, TeamResponse, User,
} from '../types/api'

export const authApi = {
  login: (email: string, password: string) =>
    api.post<LoginResponse>('/auth/login', { email, password }).then((r) => r.data),
  me: () => api.get<User>('/auth/me').then((r) => r.data),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post('/auth/change-password', { currentPassword, newPassword }).then(() => undefined),
}

export const calendarApi = {
  get: (from: string, to: string) => api.get<CalendarDay[]>('/calendar', { params: { from, to } }).then((r) => r.data),
}

export const presenceApi = {
  book: (date: string) => api.post<PresenceDto>('/bookings', { date }).then((r) => r.data),
  cancelBooking: (id: number) => api.delete<PresenceDto>(`/bookings/${id}`).then((r) => r.data),
  remote: (date: string) => api.post<PresenceDto>('/remote-days', { date }).then((r) => r.data),
  cancelRemote: (id: number) => api.delete<PresenceDto>(`/remote-days/${id}`).then((r) => r.data),
}

export const statsApi = {
  my: (from: string, to: string) => api.get<MyStats>('/my-stats', { params: { from, to } }).then((r) => r.data),
}

export interface RecordsQuery {
  from: string
  to: string
  userId?: number
  status?: PresenceStatus
  page: number
  size: number
  sortBy?: string
  direction?: 'asc' | 'desc'
}

export const managerApi = {
  users: () => api.get<User[]>('/manager/users').then((r) => r.data),
  dashboard: (date: string, status?: string, q?: string) =>
    api.get<Dashboard>('/manager/dashboard', { params: { date, status, q } }).then((r) => r.data),
  remoteReport: (p: RecordsQuery) =>
    api.get<PageResponse<ReportRow>>('/manager/reports/remote-days', { params: p }).then((r) => r.data),
  bookingReport: (p: RecordsQuery) =>
    api.get<PageResponse<ReportRow>>('/manager/reports/bookings', { params: p }).then((r) => r.data),
  summary: (p: RecordsQuery) =>
    api.get<PageResponse<SummaryRow>>('/manager/reports/summary', { params: p }).then((r) => r.data),
  audit: (p: { from: string; to: string; actorUserId?: number; page: number; size: number }) =>
    api.get<PageResponse<AuditRow>>('/manager/audit', { params: p }).then((r) => r.data),
  settings: () => api.get<Settings>('/manager/settings').then((r) => r.data),
  saveSettings: (s: Omit<Settings, 'timezone'>) => api.put<Settings>('/manager/settings', s).then((r) => r.data),
  limitRules: () => api.get<LimitRule[]>('/manager/limit-rules').then((r) => r.data),
  saveLimitRules: (rules: LimitRule[]) => api.put<LimitRule[]>('/manager/limit-rules', rules).then((r) => r.data),
}

export const teamApi = {
  get: (from: string, to: string) => api.get<TeamResponse>('/team', { params: { from, to } }).then((r) => r.data),
}

export const analyticsApi = {
  get: (from: string, to: string) =>
    api.get<Analytics>('/manager/analytics', { params: { from, to } }).then((r) => r.data),
  csv: (from: string, to: string) =>
    api.get<Blob>('/manager/analytics/export.csv', { params: { from, to }, responseType: 'blob' }).then((r) => r.data),
}

export interface EmployeeFilter {
  q?: string
  role?: Role
  enabled?: boolean
  page: number
  size: number
}

export interface EmployeeForm {
  firstName: string
  lastName: string
  email?: string
  role: Role
}

export const employeeApi = {
  list: (p: EmployeeFilter) =>
    api.get<PageResponse<Employee>>('/manager/employees', { params: p }).then((r) => r.data),
  create: (f: Required<EmployeeForm>) =>
    api.post<CredentialsResponse>('/manager/employees', f).then((r) => r.data),
  update: (id: number, f: Omit<EmployeeForm, 'email'>) =>
    api.put<Employee>(`/manager/employees/${id}`, f).then((r) => r.data),
  deactivate: (id: number) =>
    api.post<Employee>(`/manager/employees/${id}/deactivate`, { cancelFutureRecords: true }).then((r) => r.data),
  activate: (id: number) => api.post<Employee>(`/manager/employees/${id}/activate`).then((r) => r.data),
  resetPassword: (id: number) =>
    api.post<{ temporaryPassword: string }>(`/manager/employees/${id}/reset-password`).then((r) => r.data),
}

// ---------- Задачи ----------
export interface TaskForm {
  title: string
  description?: string
  taskDate: string
  dueTime?: string
  priority?: TaskPriority
}

export interface ManagerTaskForm extends TaskForm {
  assigneeId: number
  status?: TaskStatus
}

export const tasksApi = {
  day: (date: string) => api.get<DayTasks>('/tasks', { params: { date } }).then((r) => r.data),
  create: (f: TaskForm) => api.post<Task>('/tasks', f).then((r) => r.data),
  update: (id: number, f: TaskForm) => api.put<Task>(`/tasks/${id}`, f).then((r) => r.data),
  setStatus: (id: number, status: TaskStatus) =>
    api.patch<Task>(`/tasks/${id}/status`, { status }).then((r) => r.data),
  remove: (id: number) => api.delete(`/tasks/${id}`).then(() => undefined),
}

export interface ManagerTaskQuery {
  from: string
  to: string
  assigneeId?: number
  status?: TaskStatus
  priority?: TaskPriority
  page: number
  size: number
}

export const managerTasksApi = {
  list: (q: ManagerTaskQuery) => api.get<ManagerTasks>('/manager/tasks', { params: q }).then((r) => r.data),
  create: (f: ManagerTaskForm) => api.post<Task>('/manager/tasks', f).then((r) => r.data),
  update: (id: number, f: ManagerTaskForm) => api.put<Task>(`/manager/tasks/${id}`, f).then((r) => r.data),
  remove: (id: number) => api.delete(`/manager/tasks/${id}`).then(() => undefined),
}

// ---------- Предпочтения и распределение ----------
export const preferencesApi = {
  list: (from: string, to: string) =>
    api.get<PreferencesResponse>('/work-preferences', { params: { from, to } }).then((r) => r.data),
  bulk: (items: { date: string; preference: PreferenceType | null }[]) =>
    api.post<PreferenceDay[]>('/work-preferences/bulk', { items }).then((r) => r.data),
}

export const allocationApi = {
  settings: () => api.get<AllocationSettings>('/manager/allocation/settings').then((r) => r.data),
  saveSettings: (s: AllocationSettings) =>
    api.put<AllocationSettings>('/manager/allocation/settings', s).then((r) => r.data),
  overview: (from: string, to: string) =>
    api.get<AllocationOverview>('/manager/allocation', { params: { from, to } }).then((r) => r.data),
  runs: () => api.get<AllocationRun[]>('/manager/allocation/runs').then((r) => r.data),
  run: (from: string, to: string) => api.post<RunDetail>('/manager/allocation/run', { from, to }).then((r) => r.data),
  get: (id: number) => api.get<RunDetail>(`/manager/allocation/${id}`).then((r) => r.data),
  adjust: (runId: number, resultId: number, outcome: Outcome, reason: string) =>
    api.put(`/manager/allocation/${runId}/results/${resultId}`, { outcome, reason }).then((r) => r.data),
  confirm: (id: number) => api.post<RunDetail>(`/manager/allocation/${id}/confirm`).then((r) => r.data),
  discard: (id: number) => api.post<AllocationRun>(`/manager/allocation/${id}/discard`).then((r) => r.data),
  csv: (id: number) => api.get<Blob>(`/manager/allocation/${id}/export.csv`, { responseType: 'blob' }).then((r) => r.data),
}

// ---------- Уведомления ----------
export const notificationsApi = {
  list: () => api.get<NotificationsResponse>('/notifications').then((r) => r.data),
  read: (id: number) => api.post(`/notifications/${id}/read`).then(() => undefined),
  readAll: () => api.post('/notifications/read-all').then(() => undefined),
}
