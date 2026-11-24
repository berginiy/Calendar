import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'
import 'dayjs/locale/ru'
import type { CalendarDay, MyStatus } from '../types/api'

dayjs.extend(utc)
dayjs.extend(timezone)
dayjs.locale('ru')

export const APP_TZ = 'Asia/Bishkek'
export const DATE_FMT = 'YYYY-MM-DD'

/** Сегодняшняя календарная дата по Бишкеку, независимо от часового пояса браузера. */
export function todayBishkek(): string {
  return dayjs().tz(APP_TZ).format(DATE_FMT)
}

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dayjs(iso).tz(APP_TZ).format('DD.MM.YYYY HH:mm') : '—'
}

export function formatDate(date: string): string {
  return dayjs(date).format('DD.MM.YYYY, dd')
}

export const STATUS_LABEL: Record<MyStatus, string> = {
  OFFICE: 'В офисе',
  REMOTE: 'Удалённо',
  NONE: 'Не выбран',
}

export type DayTone = 'free' | 'almost' | 'full' | 'remote' | 'office' | 'disabled'

export const DAY_NAME: Record<string, string> = {
  MONDAY: 'Понедельник', TUESDAY: 'Вторник', WEDNESDAY: 'Среда', THURSDAY: 'Четверг',
  FRIDAY: 'Пятница', SATURDAY: 'Суббота', SUNDAY: 'Воскресенье',
}

export interface DayView {
  tone: DayTone
  /** Строка о занятости офиса */
  officeText: string
  /** Строка о статусе пользователя (если выбран) */
  statusText: string | null
}

/** Подписи и цветовой тон ячейки календаря. Цвет всегда дублируется текстом. */
export function describeDay(d: CalendarDay): DayView {
  const officeText =
    d.free > 0
      ? `Офис: ${d.occupied} / ${d.capacity}, свободно: ${d.free}`
      : `Офис: ${d.occupied} / ${d.capacity}, мест нет`

  if (d.myStatus === 'OFFICE') return { tone: 'office', officeText, statusText: 'Мой статус: В офисе' }
  if (d.myStatus === 'REMOTE') return { tone: 'remote', officeText, statusText: 'Мой статус: Удалённо' }

  const dateUnavailable = !d.canRemote && d.unavailableReason !== null && d.free > 0
  if (dateUnavailable || !d.workingDay) return { tone: 'disabled', officeText, statusText: null }
  if (d.free === 0 || d.occupancyLevel === 'FULL') return { tone: 'full', officeText, statusText: null }
  if (d.occupancyLevel === 'ALMOST_FULL') return { tone: 'almost', officeText, statusText: null }
  return { tone: 'free', officeText, statusText: null }
}
