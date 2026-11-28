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

// ---------- Задачи ----------
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH'

export interface Task {
  id: number
  title: string
  description: string | null
  assigneeId: number
  assigneeName: string
  createdById: number
  createdByName: string
  taskDate: string
  dueTime: string | null
  priority: TaskPriority
  status: TaskStatus
  createdAt: string
  updatedAt: string
  canEdit: boolean
  canDelete: boolean
  canChangeStatus: boolean
}

export interface TaskProgress {
  total: number
  done: number
  inProgress: number
  todo: number
  percent: number
}

export interface DayTasks {
  date: string
  progress: TaskProgress
  tasks: Task[]
}

export interface ManagerTasks {
  progress: TaskProgress
  tasks: PageResponse<Task>
}

// ---------- Предпочтения и распределение ----------
export type PreferenceType = 'OFFICE' | 'REMOTE' | 'FLEXIBLE'
export type PrefStatus = 'PENDING' | 'PRELIMINARY' | 'SATISFIED' | 'ASSIGNED' | 'NOT_SATISFIED'
export type Outcome = 'OFFICE' | 'REMOTE' | 'NONE'

export interface PreferenceDay {
  date: string
  workingDay: boolean
  preferenceId: number | null
  preference: PreferenceType | null
  status: PrefStatus | null
  outcome: Outcome | null
  confirmed: boolean
  explanation: string | null
  editable: boolean
  editableReason: string | null
}

export interface PreferencesResponse {
  enabled: boolean
  deadlineHours: number
  days: PreferenceDay[]
}

export type AllocationPolicy = 'ROTATION' | 'SUBMISSION_ORDER'

export interface AllocationSettings {
  enabled: boolean
  policy: AllocationPolicy
  preferenceDeadlineHours: number
}

export interface OverviewDay {
  date: string
  workingDay: boolean
  allocatable: boolean
  capacity: number
  occupiedOffice: number
  freeSeats: number
  officePreferences: number
  remotePreferences: number
  flexiblePreferences: number
  noPreference: number
}

export interface AllocationOverview {
  settings: AllocationSettings
  totalEmployees: number
  draftRunId: number | null
  days: OverviewDay[]
}

export type RunStatus = 'DRAFT' | 'APPLYING' | 'CONFIRMED' | 'DISCARDED'
export type ApplyStatus = 'PENDING' | 'APPLIED' | 'SKIPPED' | 'FAILED'
export type ResultSource = 'AUTO' | 'EXISTING' | 'MANUAL'

export interface AllocationRun {
  id: number
  rangeFrom: string
  rangeTo: string
  status: RunStatus
  policy: AllocationPolicy
  capacity: number
  createdById: number
  createdByName: string | null
  createdAt: string
  confirmedByName: string | null
  confirmedAt: string | null
  total: number
  office: number
  remote: number
  none: number
  applied: number
  skipped: number
  failed: number
  pending: number
}

export interface AllocationResult {
  id: number
  userId: number
  userName: string
  date: string
  preference: PreferenceType
  outcome: Outcome
  source: ResultSource
  explanation: string
  manualReason: string | null
  applyStatus: ApplyStatus
  applyMessage: string | null
}

export interface AllocationDaySummary {
  date: string
  officeWanted: number
  officeAssigned: number
  remoteAssigned: number
  unassigned: number
}

export interface RunDetail {
  run: AllocationRun
  days: AllocationDaySummary[]
  results: AllocationResult[]
}

// ---------- Уведомления ----------
export interface AppNotification {
  id: number
  type: string
  title: string
  message: string
  createdAt: string
  read: boolean
}

export interface NotificationsResponse {
  unread: number
  items: AppNotification[]
}
