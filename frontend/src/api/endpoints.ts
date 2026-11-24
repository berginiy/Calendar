import { api } from './client'
import type {
  AuditRow, CalendarDay, Dashboard, LimitRule, LoginResponse, MyStats, PageResponse, PresenceDto,
  PresenceStatus, ReportRow, Settings, SummaryRow, User,
} from '../types/api'

export const authApi = {
  login: (email: string, password: string) =>
    api.post<LoginResponse>('/auth/login', { email, password }).then((r) => r.data),
  me: () => api.get<User>('/auth/me').then((r) => r.data),
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
