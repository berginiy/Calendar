export type Role = 'EMPLOYEE' | 'MANAGER'
export type PresenceType = 'OFFICE' | 'REMOTE'
export type PresenceStatus = 'ACTIVE' | 'CANCELLED'
export type MyStatus = 'OFFICE' | 'REMOTE' | 'NONE'
export type DayOfWeek =
  | 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface User {
  id: number
  firstName: string
  lastName: string
  email: string
  role: Role
  enabled: boolean
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: User
}

export interface CalendarDay {
  date: string
  workingDay: boolean
  capacity: number
  occupied: number
  free: number
  myStatus: MyStatus
  myRecordId: number | null
  canBook: boolean
  canRemote: boolean
  canCancel: boolean
  unavailableReason: string | null
}

export interface PresenceDto {
  id: number
  date: string
  type: PresenceType
  status: PresenceStatus
  createdAt: string
  cancelledAt: string | null
}

export interface LimitUsage {
  periodFrom: string
  periodTo: string
  used: number
  max: number | null
  unlimited: boolean
  remaining: number | null
}

export interface MyStats {
  from: string
  to: string
  week: LimitUsage
  month: LimitUsage
  remoteDays: PresenceDto[]
}

export interface DashboardEmployee {
  userId: number
  fullName: string
  email: string
  status: MyStatus
}

export interface Dashboard {
  date: string
  totalEmployees: number
  inOffice: number
  remote: number
  notSelected: number
  totalSeats: number
  occupiedSeats: number
  freeSeats: number
  employees: DashboardEmployee[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ReportRow {
  id: number
  userId: number
  userName: string
  email: string
  date: string
  type: PresenceType
  status: PresenceStatus
  createdAt: string
  cancelledAt: string | null
}

export interface SummaryRow {
  userId: number
  userName: string
  email: string
  officeDays: number
  remoteDays: number
  cancelled: number
}

export interface AuditRow {
  id: number
  actorUserId: number | null
  actorName: string | null
  action: string
  entityType: string
  entityId: string | null
  details: string | null
  createdAt: string
}

export interface Settings {
  officeCapacity: number
  allowSameDayBooking: boolean
  cancellationDeadlineHours: number
  workingDays: DayOfWeek[]
  timezone: string
}

export interface LimitRule {
  periodType: 'WEEKLY' | 'MONTHLY'
  maxDays: number
  enabled: boolean
}

export interface ApiError {
  code: string
  message: string
  details?: string[]
}
