import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { TeamPage } from './TeamPage'
import { teamApi } from '../../api/endpoints'
import type { TeamResponse } from '../../types/api'

vi.mock('../../api/endpoints', () => ({ teamApi: { get: vi.fn() } }))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <AntApp>
      <QueryClientProvider client={qc}><TeamPage /></QueryClientProvider>
    </AntApp>,
  )
}

const day = (date: string, members: TeamResponse['days'][number]['members']) => ({
  date, workingDay: true, office: 1, remote: 1, notSelected: 0, members,
})

beforeEach(() => vi.clearAllMocks())

describe('TeamPage', () => {
  it('в режиме «только счётчики» не показывает имена и объясняет почему', async () => {
    vi.mocked(teamApi.get).mockResolvedValue({
      visibility: 'COUNTS_ONLY', totalEmployees: 2, days: [day('2026-11-23', [])],
    })
    renderPage()
    expect(await screen.findByText(/Вам доступны только количественные данные/)).toBeInTheDocument()
    expect(screen.queryByText('Кто где работает')).not.toBeInTheDocument()
  })

  it('руководителю показывает имена и статусы', async () => {
    vi.mocked(teamApi.get).mockResolvedValue({
      visibility: 'FULL', totalEmployees: 2,
      days: [day('2026-11-23', [
        { userId: 1, fullName: 'Айбек Асанов', status: 'OFFICE' },
        { userId: 2, fullName: 'Бермет Бекова', status: 'REMOTE' },
      ])],
    })
    renderPage()
    expect(await screen.findByText('Айбек Асанов')).toBeInTheDocument()
    expect(screen.getByText('Бермет Бекова')).toBeInTheDocument()
    expect(screen.getByText('Кто где работает')).toBeInTheDocument()
  })

  it('показывает ошибку загрузки с повтором', async () => {
    vi.mocked(teamApi.get).mockRejectedValue(new Error('x'))
    renderPage()
    expect(await screen.findByRole('button', { name: 'Повторить' })).toBeInTheDocument()
  })
})
