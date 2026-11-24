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
  mustChangePassword: boolean
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
  occupancyLevel?: 'FREE' | 'ALMOST_FULL' | 'FULL'
  myStatus: MyStatus
  myRecordId: number | null
  canBook: boolean
  canRemote: boolean
  canCancel: boolean
  /** Общая причина уровня даты (прошлое, выходной, мест нет) */
  unavailableReason: string | null
  bookReason?: string | null
  remoteReason?: string | null
  cancelReason?: string | null
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
  teamVisibleToEmployees: boolean
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

// ---------- Командный календарь ----------
export interface TeamMember {
  userId: number
  fullName: string
  status: MyStatus
}

export interface TeamDay {
  date: string
  workingDay: boolean
  office: number
  remote: number
  notSelected: number
  members: TeamMember[]
}

export interface TeamResponse {
  visibility: 'FULL' | 'COUNTS_ONLY'
  totalEmployees: number
  days: TeamDay[]
}

// ---------- Аналитика ----------
export interface DayStat {
  date: string
  workingDay: boolean
  office: number
  remote: number
  free: number
  utilization: number
}

export interface WeekdayStat {
  day: DayOfWeek
  days: number
  avgOccupied: number
  maxOccupied: number
  avgUtilization: number
}

export interface PeriodStat {
  period: string
  from: string
  to: string
  workingDays: number
  office: number
  remote: number
  utilization: number
}

export interface Analytics {
  basis: 'PLANNED'
  from: string
  to: string
  capacity: number
  workingDays: number
  officeBookings: number
  remoteDays: number
  cancelled: number
  overallUtilization: number
  byWeekday: WeekdayStat[]
  topDemand: DayStat[]
  lowDemand: DayStat[]
  weekly: PeriodStat[]
  monthly: PeriodStat[]
}

// ---------- Сотрудники ----------
export interface Employee {
  id: number
  firstName: string
  lastName: string
  email: string
  role: Role
  enabled: boolean
  mustChangePassword: boolean
  createdAt: string
}

export interface CredentialsResponse {
  employee: Employee
  temporaryPassword: string
}
