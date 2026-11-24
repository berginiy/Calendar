import { AxiosError, type AxiosResponse } from 'axios'
import { errorMessage } from './client'

function apiError(status: number, data?: unknown): AxiosError {
  const err = new AxiosError('fail')
  err.response = { status, data } as AxiosResponse
  return err
}

describe('errorMessage', () => {
  it('возвращает сообщение backend', () => {
    expect(errorMessage(apiError(409, { code: 'NO_FREE_SEATS', message: 'Свободных мест нет' }))).toBe('Свободных мест нет')
  })

  it('понятный текст при отсутствии связи', () => {
    expect(errorMessage(new AxiosError('Network Error'))).toContain('Нет связи с сервером')
  })

  it('не раскрывает технические детали при 500', () => {
    expect(errorMessage(apiError(500, '<html>stack trace</html>'))).toBe('Внутренняя ошибка сервера. Повторите попытку позже')
  })

  it('сообщение по умолчанию для неизвестной ошибки', () => {
    expect(errorMessage(new Error('boom'))).toBe('Не удалось выполнить действие')
  })
})
