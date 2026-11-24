import { describeDay } from './format'
import type { CalendarDay } from '../types/api'

const base: CalendarDay = {
  date: '2026-11-24', workingDay: true, capacity: 10, occupied: 7, free: 3, myStatus: 'NONE', myRecordId: null,
  canBook: true, canRemote: true, canCancel: false, unavailableReason: null,
}

describe('describeDay', () => {
  it('показывает свободные места зелёным с текстом', () => {
    const v = describeDay(base)
    expect(v.tone).toBe('free')
    expect(v.officeText).toBe('Офис: 7 / 10, свободно: 3')
    expect(v.statusText).toBeNull()
  })

  it('показывает «мест нет» красным, когда всё занято', () => {
    const v = describeDay({ ...base, occupied: 10, free: 0, canBook: false, unavailableReason: 'Свободных мест нет' })
    expect(v.tone).toBe('full')
    expect(v.officeText).toBe('Офис: 10 / 10, мест нет')
  })

  it('показывает удалённый статус пользователя', () => {
    const v = describeDay({ ...base, myStatus: 'REMOTE', myRecordId: 5 })
    expect(v.tone).toBe('remote')
    expect(v.statusText).toBe('Мой статус: Удалённо')
  })

  it('показывает офисный статус пользователя', () => {
    const v = describeDay({ ...base, myStatus: 'OFFICE', myRecordId: 5 })
    expect(v.tone).toBe('office')
    expect(v.statusText).toBe('Мой статус: В офисе')
  })

  it('серый для недоступной даты', () => {
    const v = describeDay({ ...base, canBook: false, canRemote: false, unavailableReason: 'Эта дата — нерабочий день', workingDay: false })
    expect(v.tone).toBe('disabled')
  })
})
