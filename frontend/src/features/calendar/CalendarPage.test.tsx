import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { AxiosError, type AxiosResponse } from 'axios'
import { CalendarPage } from './CalendarPage'
import { calendarApi, presenceApi } from '../../api/endpoints'
import type { CalendarDay } from '../../types/api'

vi.mock('../../api/endpoints', () => ({
  calendarApi: { get: vi.fn() },
  presenceApi: { book: vi.fn(), remote: vi.fn(), cancelBooking: vi.fn(), cancelRemote: vi.fn() },
}))

const day = (n: number, over: Partial<CalendarDay> = {}): CalendarDay => ({
  date: `2026-11-${String(n).padStart(2, '0')}`, workingDay: true, capacity: 10, occupied: 7, free: 3,
  myStatus: 'NONE', myRecordId: null, canBook: true, canRemote: true, canCancel: false, unavailableReason: null, ...over,
})

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <AntApp>
      <QueryClientProvider client={qc}><CalendarPage /></QueryClientProvider>
    </AntApp>,
  )
}

beforeEach(() => vi.clearAllMocks())

describe('CalendarPage', () => {
  it('показывает занятость, «мест нет» и мой статус', async () => {
    vi.mocked(calendarApi.get).mockResolvedValue([
      day(2),
      day(3, { occupied: 10, free: 0, canBook: false, unavailableReason: 'Свободных мест нет' }),
      day(4, { myStatus: 'REMOTE', myRecordId: 1, canCancel: true }),
    ])
    renderPage()
    expect((await screen.findAllByText('Офис: 7 / 10, свободно: 3')).length).toBeGreaterThan(0)
    expect(screen.getByText('Офис: 10 / 10, мест нет')).toBeInTheDocument()
    expect(screen.getByText('Мой статус: Удалённо')).toBeInTheDocument()
  })

  it('создаёт бронирование и показывает подтверждение', async () => {
    vi.mocked(calendarApi.get).mockResolvedValue([day(2)])
    vi.mocked(presenceApi.book).mockResolvedValue({
      id: 1, date: '2026-11-02', type: 'OFFICE', status: 'ACTIVE', createdAt: '', cancelledAt: null,
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: /Офис: 7 \/ 10/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Забронировать место' }))
    await waitFor(() => expect(presenceApi.book).toHaveBeenCalledWith('2026-11-02'))
    expect(await screen.findByText('Место забронировано')).toBeInTheDocument()
  })

  it('показывает ошибку API понятным текстом', async () => {
    vi.mocked(calendarApi.get).mockResolvedValue([day(2)])
    const err = new AxiosError('x')
    err.response = { status: 409, data: { code: 'NO_FREE_SEATS', message: 'На выбранную дату свободных мест нет' } } as AxiosResponse
    vi.mocked(presenceApi.book).mockRejectedValue(err)
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: /Офис: 7 \/ 10/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Забронировать место' }))
    expect(await screen.findByText('На выбранную дату свободных мест нет')).toBeInTheDocument()
  })

  it('отменяет запись после подтверждения', async () => {
    vi.mocked(calendarApi.get).mockResolvedValue([day(2, { myStatus: 'OFFICE', myRecordId: 42, canCancel: true })])
    vi.mocked(presenceApi.cancelBooking).mockResolvedValue({
      id: 42, date: '2026-11-02', type: 'OFFICE', status: 'CANCELLED', createdAt: '', cancelledAt: '',
    })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: /Мой статус: В офисе/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Отменить запись' }))
    // во всплывающем подтверждении появляется вторая кнопка с тем же текстом
    const confirm = (await screen.findAllByRole('button', { name: 'Отменить запись' })).at(-1)!
    await userEvent.click(confirm)
    await waitFor(() => expect(presenceApi.cancelBooking).toHaveBeenCalledWith(42))
  })

  it('показывает ошибку загрузки с кнопкой повтора', async () => {
    vi.mocked(calendarApi.get).mockRejectedValue(new AxiosError('Network Error'))
    renderPage()
    expect(await screen.findByText(/Нет связи с сервером/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Повторить' })).toBeInTheDocument()
  })
})
