import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import type { Role } from '../../types/api'

const auth = vi.hoisted(() => ({
  value: { user: null as null | { role: string; mustChangePassword?: boolean }, loading: false },
}))
vi.mock('./AuthContext', () => ({ useAuth: () => auth.value }))

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>Страница входа</div>} />
        <Route path="/change-password" element={<div>Смена пароля</div>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/calendar" element={<div>Календарь</div>} />
          <Route element={<ProtectedRoute roles={['MANAGER']} />}>
            <Route path="/manager/dashboard" element={<div>Дашборд</div>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

const asUser = (role: Role) => { auth.value = { user: { role }, loading: false } }

describe('ProtectedRoute', () => {
  it('перенаправляет неавторизованного на /login', () => {
    auth.value = { user: null, loading: false }
    renderAt('/calendar')
    expect(screen.getByText('Страница входа')).toBeInTheDocument()
  })

  it('пускает сотрудника на календарь', () => {
    asUser('EMPLOYEE')
    renderAt('/calendar')
    expect(screen.getByText('Календарь')).toBeInTheDocument()
  })

  it('не пускает сотрудника на страницы руководителя', () => {
    asUser('EMPLOYEE')
    renderAt('/manager/dashboard')
    expect(screen.queryByText('Дашборд')).not.toBeInTheDocument()
    expect(screen.getByText('Нет доступа')).toBeInTheDocument()
  })

  it('отправляет на смену временного пароля до любой другой страницы', () => {
    auth.value = { user: { role: 'EMPLOYEE', mustChangePassword: true }, loading: false }
    renderAt('/calendar')
    expect(screen.getByText('Смена пароля')).toBeInTheDocument()
    expect(screen.queryByText('Календарь')).not.toBeInTheDocument()
  })

  it('пускает руководителя на дашборд', () => {
    asUser('MANAGER')
    renderAt('/manager/dashboard')
    expect(screen.getByText('Дашборд')).toBeInTheDocument()
  })
})
