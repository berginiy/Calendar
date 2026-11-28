import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { NotificationBell } from './NotificationBell'
import { notificationsApi } from '../api/endpoints'

vi.mock('../api/endpoints', () => ({ notificationsApi: { list: vi.fn(), read: vi.fn(), readAll: vi.fn() } }))

function renderBell() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><NotificationBell /></QueryClientProvider>)
}

beforeEach(() => vi.clearAllMocks())

describe('NotificationBell', () => {
  it('показывает число непрочитанных и отмечает уведомление прочитанным по клику', async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue({
      unread: 1,
      items: [{ id: 9, type: 'ALLOCATION_CONFIRMED', title: 'Распределение подтверждено', message: '30.11: офис', createdAt: '2026-11-24T05:00:00Z', read: false }],
    })
    vi.mocked(notificationsApi.read).mockResolvedValue(undefined)
    renderBell()
    const bell = await screen.findByRole('button', { name: /Уведомления, непрочитанных: 1/ })
    await userEvent.click(bell)
    await userEvent.click(await screen.findByText('Распределение подтверждено'))
    await waitFor(() => expect(notificationsApi.read).toHaveBeenCalledWith(9))
  })

  it('пустой список показывает понятное сообщение', async () => {
    vi.mocked(notificationsApi.list).mockResolvedValue({ unread: 0, items: [] })
    renderBell()
    await userEvent.click(await screen.findByRole('button', { name: 'Уведомления' }))
    expect(await screen.findByText('Уведомлений нет')).toBeInTheDocument()
  })
})
