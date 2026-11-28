import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { AllocationPage } from './AllocationPage'
import { allocationApi } from '../../api/endpoints'
import type { AllocationRun, RunDetail, RunStatus } from '../../types/api'

vi.mock('../../api/endpoints', () => ({
  allocationApi: {
    settings: vi.fn(), saveSettings: vi.fn(), overview: vi.fn(), runs: vi.fn(), run: vi.fn(), get: vi.fn(),
    adjust: vi.fn(), confirm: vi.fn(), discard: vi.fn(), csv: vi.fn(),
  },
}))

const run = (status: RunStatus): AllocationRun => ({
  id: 5, rangeFrom: '2026-11-30', rangeTo: '2026-12-04', status, policy: 'ROTATION', capacity: 10, createdById: 1,
  createdByName: 'Менеджер', createdAt: '2026-11-24T05:00:00Z', confirmedByName: null, confirmedAt: null,
  total: 2, office: 1, remote: 0, none: 1, applied: 0, skipped: 1, failed: 0, pending: 1,
})

const detail = (status: RunStatus): RunDetail => ({
  run: run(status),
  days: [{ date: '2026-11-30', officeWanted: 2, officeAssigned: 1, remoteAssigned: 0, unassigned: 1 }],
  results: [
    { id: 1, userId: 1, userName: 'Айбек Асанов', date: '2026-11-30', preference: 'OFFICE', outcome: 'OFFICE', source: 'AUTO',
      explanation: 'Офисное предпочтение учтено: место выделено.', manualReason: null, applyStatus: 'PENDING', applyMessage: null },
    { id: 2, userId: 2, userName: 'Бермет Бекова', date: '2026-11-30', preference: 'OFFICE', outcome: 'NONE', source: 'AUTO',
      explanation: 'Офисное предпочтение не удовлетворено: свободные места распределены.', manualReason: null,
      applyStatus: 'SKIPPED', applyMessage: 'Назначение не требуется' },
  ],
})

function setup(enabled: boolean, status: RunStatus) {
  vi.mocked(allocationApi.settings).mockResolvedValue({ enabled, policy: 'ROTATION', preferenceDeadlineHours: 24 })
  vi.mocked(allocationApi.overview).mockResolvedValue({
    settings: { enabled, policy: 'ROTATION', preferenceDeadlineHours: 24 }, totalEmployees: 15, draftRunId: null, days: [],
  })
  vi.mocked(allocationApi.runs).mockResolvedValue([run(status)])
  vi.mocked(allocationApi.get).mockResolvedValue(detail(status))
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<AntApp><QueryClientProvider client={qc}><AllocationPage /></QueryClientProvider></AntApp>)
}

beforeEach(() => vi.clearAllMocks())

describe('AllocationPage', () => {
  it('не даёт запустить расчёт, пока функция выключена', async () => {
    setup(false, 'DRAFT')
    expect(await screen.findByText(/Автоматическое распределение выключено/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Рассчитать распределение' })).toBeDisabled()
  })

  it('черновик можно подтвердить только после явного подтверждения в диалоге', async () => {
    vi.mocked(allocationApi.confirm).mockResolvedValue(detail('CONFIRMED'))
    setup(true, 'DRAFT')
    await userEvent.click(await screen.findByRole('button', { name: 'Открыть' }))
    const confirmBtn = await screen.findByRole('button', { name: 'Подтвердить' })
    await userEvent.click(confirmBtn)
    expect(await screen.findByText(/Будет создано записей: 1/)).toBeInTheDocument()
    expect(allocationApi.confirm).not.toHaveBeenCalled() // пока диалог не подтверждён — ничего не происходит
    const buttons = await screen.findAllByRole('button', { name: 'Подтвердить' })
    await userEvent.click(buttons[buttons.length - 1])
    await waitFor(() => expect(allocationApi.confirm).toHaveBeenCalledWith(5))
  })

  it('у подтверждённого распределения нет кнопок подтверждения и изменения', async () => {
    setup(true, 'CONFIRMED')
    await userEvent.click(await screen.findByRole('button', { name: 'Открыть' }))
    expect(await screen.findByText('Распределение №5')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Подтвердить' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отклонить' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Изменить' })).not.toBeInTheDocument()
  })

  it('показывает объяснение результата для каждого сотрудника', async () => {
    setup(true, 'DRAFT')
    await userEvent.click(await screen.findByRole('button', { name: 'Открыть' }))
    expect(await screen.findByText(/свободные места распределены/)).toBeInTheDocument()
    expect(screen.getByText('Айбек Асанов')).toBeInTheDocument()
    expect(screen.getByText('Бермет Бекова')).toBeInTheDocument()
  })
})
