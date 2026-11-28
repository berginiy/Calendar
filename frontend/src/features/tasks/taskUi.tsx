import { Tag } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import type { TaskPriority, TaskStatus } from '../../types/api'

export const STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: 'К выполнению',
  IN_PROGRESS: 'В работе',
  DONE: 'Выполнено',
}

export const PRIORITY_LABEL: Record<TaskPriority, string> = { LOW: 'Низкий', NORMAL: 'Обычный', HIGH: 'Высокий' }
const PRIORITY_COLOR: Record<TaskPriority, string> = { LOW: 'default', NORMAL: 'blue', HIGH: 'red' }

export const STATUS_OPTIONS = (Object.keys(STATUS_LABEL) as TaskStatus[]).map((v) => ({ value: v, label: STATUS_LABEL[v] }))
export const PRIORITY_OPTIONS = (Object.keys(PRIORITY_LABEL) as TaskPriority[]).map((v) => ({ value: v, label: PRIORITY_LABEL[v] }))

export function PriorityTag({ priority }: { priority: TaskPriority }) {
  return <Tag color={PRIORITY_COLOR[priority]}>{PRIORITY_LABEL[priority]}</Tag>
}

/** «09:30:00» от backend → dayjs для TimePicker. */
export function toTime(value: string | null | undefined): Dayjs | undefined {
  return value ? dayjs(`2000-01-01T${value}`) : undefined
}

/** Короткий вид времени для отображения. */
export function shortTime(value: string | null): string {
  return value ? value.slice(0, 5) : ''
}
