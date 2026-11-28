import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import { TasksPage } from './TasksPage'
import { tasksApi } from '../../api/endpoints'
import type { DayTasks, Task } from '../../types/api'

vi.mock('../../api/endpoints', () => ({
  tasksApi: { day: vi.fn(), create: vi.fn(), update: vi.fn(), setStatus: vi.fn(), remove: vi.fn() },
}))

const task = (id: number, over: Partial<Task> = {}): Task => ({
  id, title: `Задача ${id}`, description: null, assigneeId: 1, assigneeName: 'Айбек Асанов', createdById: 1,
  createdByName: 'Айбек Асанов', taskDate: '2026-11-23', dueTime: null, priority: 'NORMAL', status: 'TODO',
  createdAt: '', updatedAt: '', canEdit: true, canDelete: true, canChangeStatus: true, ...over,
})

const day = (tasks: Task[]): DayTasks => {
  const done = tasks.filter((t) => t.status === 'DONE').length
  return {
    date: '2026-11-23', tasks,
    progress: { total: tasks.length, done, inProgress: 0, todo: tasks.length - done, percent: tasks.length ? Math.round(done * 100 / tasks.length) : 0 },
  }
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<AntApp><QueryClientProvider client={qc}><TasksPage /></QueryClientProvider></AntApp>)
}

beforeEach(() => vi.clearAllMocks())

describe('TasksPage', () => {
  it('показывает прогресс и задачи', async () => {
    vi.mocked(tasksApi.day).mockResolvedValue(day([task(1, { status: 'DONE' }), task(2)]))
    renderPage()
    expect(await screen.findByText('Выполнено 1 из 2')).toBeInTheDocument()
    expect(screen.getByText('Задача 1')).toBeInTheDocument()
    expect(screen.getByText('Задача 2')).toBeInTheDocument()
  })

  it('пустой день показывает понятное сообщение', async () => {
    vi.mocked(tasksApi.day).mockResolvedValue(day([]))
    renderPage()
    expect(await screen.findByText('Задач на этот день пока нет')).toBeInTheDocument()
    expect(screen.getByText('На этот день задач нет')).toBeInTheDocument()
  })

  it('отмечает задачу выполненной', async () => {
    vi.mocked(tasksApi.day).mockResolvedValue(day([task(7)]))
    vi.mocked(tasksApi.setStatus).mockResolvedValue(task(7, { status: 'DONE' }))
    renderPage()
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Выполнено: Задача 7' }))
    await waitFor(() => expect(tasksApi.setStatus).toHaveBeenCalledWith(7, 'DONE'))
  })

  it('задачу, назначенную руководителем, нельзя править и удалять, но можно отмечать', async () => {
    vi.mocked(tasksApi.day).mockResolvedValue(day([
      task(3, { createdById: 99, createdByName: 'Руководитель', canEdit: false, canDelete: false }),
    ]))
    renderPage()
    expect(await screen.findByText('Назначил: Руководитель')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Изменить' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Удалить' })).not.toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Выполнено: Задача 3' })).toBeEnabled()
  })

  it('показывает ошибку загрузки с повтором', async () => {
    vi.mocked(tasksApi.day).mockRejectedValue(new Error('x'))
    renderPage()
    expect(await screen.findByRole('button', { name: 'Повторить' })).toBeInTheDocument()
  })
})
