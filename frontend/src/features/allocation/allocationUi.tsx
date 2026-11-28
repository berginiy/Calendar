import { Tag } from 'antd'
import type { AllocationPolicy, ApplyStatus, Outcome, PreferenceType, ResultSource, RunStatus } from '../../types/api'

export const POLICY_LABEL: Record<AllocationPolicy, string> = {
  ROTATION: 'Ротация (кому реже доставалось место — тот приоритетнее)',
  SUBMISSION_ORDER: 'По порядку подачи предпочтения',
}

export const RUN_STATUS: Record<RunStatus, { color: string; label: string }> = {
  DRAFT: { color: 'orange', label: 'Черновик' },
  APPLYING: { color: 'processing', label: 'Применяется' },
  CONFIRMED: { color: 'green', label: 'Подтверждено' },
  DISCARDED: { color: 'default', label: 'Отклонено' },
}

export const PREF_LABEL: Record<PreferenceType, string> = { OFFICE: 'Офис', REMOTE: 'Удалённо', FLEXIBLE: 'Гибко' }
export const SOURCE_LABEL: Record<ResultSource, string> = { AUTO: 'Алгоритм', EXISTING: 'Уже была запись', MANUAL: 'Вручную' }

export function OutcomeTag({ outcome }: { outcome: Outcome }) {
  if (outcome === 'OFFICE') return <Tag color="purple">Офис</Tag>
  if (outcome === 'REMOTE') return <Tag color="blue">Удалённо</Tag>
  return <Tag>Без назначения</Tag>
}

export function ApplyTag({ status }: { status: ApplyStatus }) {
  const map: Record<ApplyStatus, { color: string; label: string }> = {
    PENDING: { color: 'default', label: 'Ожидает' },
    APPLIED: { color: 'green', label: 'Применено' },
    SKIPPED: { color: 'gold', label: 'Пропущено' },
    FAILED: { color: 'red', label: 'Ошибка' },
  }
  return <Tag color={map[status].color}>{map[status].label}</Tag>
}
