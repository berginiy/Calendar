import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { PreferencesPage } from './PreferencesPage'
import { preferencesApi } from '../../api/endpoints'
import type { PreferenceDay, PreferencesResponse } from '../../types/api'

vi.mock('../../api/endpoints', () => ({ preferencesApi: { list: vi.fn(), bulk: vi.fn() } }))

const day = (date: string, over: Partial<PreferenceDay> = {}): PreferenceDay => ({
  date, workingDay: true, preferenceId: null, preference: null, status: null, outcome: null, confirmed: false,
  explanation: null, editable: true, editableReason: null, ...over,
})

const resp = (days: PreferenceDay[], enabled = true): PreferencesResponse => ({ enabled, deadlineHours: 24, days })

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<AntApp><QueryClientProvider client={qc}><PreferencesPage /></QueryClientProvider></AntApp>)
}

beforeEach(() => vi.clearAllMocks())

describe('PreferencesPage', () => {
  it('предупреждает, что функция выключена', async () => {
    vi.mocked(preferencesApi.list).mockResolvedValue(resp([day('2026-11-30', { editable: false, editableReason: 'Автоматическое распределение выключено' })], false))
    renderPage()
    expect(await screen.findByText(/Автоматическое распределение сейчас выключено/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Сохранить' })).toBeDisabled()
  })

  it('сохраняет только изменённые дни через массовый запрос', async () => {
    vi.mocked(preferencesApi.list).mockResolvedValue(resp([day('2026-11-30'), day('2026-12-01', { preferenceId: 5, preference: 'REMOTE' })]))
    vi.mocked(preferencesApi.bulk).mockResolvedValue([])
    renderPage()
    const group = await screen.findByRole('radiogroup', { name: /Предпочтение на 30\.11\.2026/ })
    await userEvent.click(within(group).getByText('Офис'))
    const save = screen.getByRole('button', { name: 'Сохранить' })
    expect(save).toBeEnabled()
    await userEvent.click(save)
    await waitFor(() => expect(preferencesApi.bulk).toHaveBeenCalledWith([{ date: '2026-11-30', preference: 'OFFICE' }]))
  })

  it('показывает статусы и объяснение; предварительный результат помечен как неподтверждённый', async () => {
    vi.mocked(preferencesApi.list).mockResolvedValue(resp([
      day('2026-11-30', { preferenceId: 1, preference: 'OFFICE', status: 'PRELIMINARY', outcome: 'OFFICE', explanation: 'Офисное предпочтение учтено: место выделено.' }),
      day('2026-12-01', { preferenceId: 2, preference: 'OFFICE', status: 'NOT_SATISFIED', outcome: 'NONE', confirmed: true, explanation: 'Офисное предпочтение не удовлетворено: свободных мест нет.' }),
    ]))
    renderPage()
    expect(await screen.findByText('Предварительно: офис')).toBeInTheDocument()
    expect(screen.getByText('Ещё не подтверждено руководителем')).toBeInTheDocument()
    expect(screen.getByText('Не удовлетворено')).toBeInTheDocument()
    expect(screen.getByText(/свободных мест нет/)).toBeInTheDocument()
  })

  it('блокирует выбор там, где срок истёк, и объясняет причину', async () => {
    vi.mocked(preferencesApi.list).mockResolvedValue(resp([
      day('2026-11-30', { editable: false, editableReason: 'Срок изменения предпочтений истёк' }),
    ]))
    renderPage()
    expect(await screen.findByText('Срок изменения предпочтений истёк')).toBeInTheDocument()
  })
})
