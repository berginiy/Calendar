import { useMemo, useState } from 'react'
import { App, Alert, Button, Card, Descriptions, Empty, List, Modal, Popconfirm, Space, Spin, Tag, Typography } from 'antd'
import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { calendarApi, presenceApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import type { CalendarDay } from '../../types/api'
import { DATE_FMT, describeDay, formatDate, todayBishkek, type DayTone } from '../../utils/format'

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']
const TONE_LABEL: Record<DayTone, string> = {
  free: 'Есть свободные места',
  full: 'Мест нет',
  remote: 'Удалённо',
  office: 'В офисе',
  disabled: 'Недоступно / не выбрано',
}

export function CalendarPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [month, setMonth] = useState(() => dayjs(todayBishkek()).startOf('month'))
  const [selected, setSelected] = useState<CalendarDay | null>(null)

  const from = month.format(DATE_FMT)
  const to = month.endOf('month').format(DATE_FMT)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['calendar', from, to],
    queryFn: () => calendarApi.get(from, to),
  })

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
    queryClient.invalidateQueries({ queryKey: ['my-stats'] })
  }

  const run = useMutation({
    mutationFn: (a: { kind: 'book' | 'remote' | 'cancel'; day: CalendarDay }) => {
      const { kind, day } = a
      if (kind === 'book') return presenceApi.book(day.date)
      if (kind === 'remote') return presenceApi.remote(day.date)
      return day.myStatus === 'OFFICE'
        ? presenceApi.cancelBooking(day.myRecordId!)
        : presenceApi.cancelRemote(day.myRecordId!)
    },
    onSuccess: (_r, { kind, day }) => {
      message.success(
        kind === 'book' ? 'Место забронировано' : kind === 'remote' ? 'Удалённая работа отмечена' : 'Запись отменена',
      )
      setSelected(null)
      refresh()
      void day
    },
    onError: (e) => {
      message.error(errorMessage(e))
      refresh() // данные могли устареть (например, место заняли)
      setSelected(null)
    },
  })

  const cells = useMemo(() => {
    if (!data) return []
    const offset = (month.day() + 6) % 7 // неделя начинается с понедельника
    return [...Array<null>(offset).fill(null), ...data]
  }, [data, month])

  // выбранный день берём из свежих данных, чтобы модальное окно не показывало устаревшее
  const current = selected ? data?.find((d) => d.date === selected.date) ?? selected : null
  const myRecords = (data ?? []).filter((d) => d.myStatus !== 'NONE')

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
        <Typography.Title level={3} style={{ margin: 0, textTransform: 'capitalize' }}>
          {month.format('MMMM YYYY')}
        </Typography.Title>
        <Space>
          <Button icon={<LeftOutlined />} onClick={() => setMonth(month.subtract(1, 'month'))} aria-label="Предыдущий месяц" />
          <Button onClick={() => setMonth(dayjs(todayBishkek()).startOf('month'))}>Сегодня</Button>
          <Button icon={<RightOutlined />} onClick={() => setMonth(month.add(1, 'month'))} aria-label="Следующий месяц" />
        </Space>
      </Space>

      <div className="cal-legend" aria-label="Обозначения">
        {(Object.keys(TONE_LABEL) as DayTone[]).map((t) => (
          <span key={t} className={`tone-${t}`}>{TONE_LABEL[t]}</span>
        ))}
      </div>

      {error ? (
        <Alert type="error" showIcon message={errorMessage(error)}
          action={<Button size="small" onClick={() => refetch()}>Повторить</Button>} />
      ) : isLoading ? (
        <div style={{ textAlign: 'center', padding: 48 }}><Spin size="large" /></div>
      ) : (
        <div className="cal-grid" role="grid" aria-label="Календарь">
          {WEEKDAYS.map((w) => <div key={w} className="cal-head">{w}</div>)}
          {cells.map((d, i) => {
            if (!d) return <div key={`e${i}`} className="cal-empty" />
            const v = describeDay(d)
            return (
              <button key={d.date} type="button" className={`cal-cell tone-${v.tone}`}
                onClick={() => setSelected(d)}
                aria-label={`${formatDate(d.date)}. ${v.officeText}. ${v.statusText ?? TONE_LABEL[v.tone]}`}>
                <span className="num">{dayjs(d.date).date()}</span>
                <span className="line">{v.officeText}</span>
                {v.statusText && <span className="line"><b>{v.statusText}</b></span>}
              </button>
            )
          })}
        </div>
      )}

      <Card title="Мои записи в этом месяце" size="small">
        {myRecords.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Записей пока нет" />
        ) : (
          <List size="small" dataSource={myRecords} renderItem={(d) => (
            <List.Item>
              <Space>
                <span>{formatDate(d.date)}</span>
                <Tag color={d.myStatus === 'OFFICE' ? 'purple' : 'blue'}>
                  {d.myStatus === 'OFFICE' ? 'В офисе' : 'Удалённо'}
                </Tag>
              </Space>
            </List.Item>
          )} />
        )}
      </Card>

      <Modal open={!!current} onCancel={() => setSelected(null)} footer={null}
        title={current ? formatDate(current.date) : ''} destroyOnClose>
        {current && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions size="small" column={1} bordered>
              <Descriptions.Item label="Офис">{describeDay(current).officeText.replace('Офис: ', '')}</Descriptions.Item>
              <Descriptions.Item label="Мой статус">
                {current.myStatus === 'OFFICE' ? 'В офисе' : current.myStatus === 'REMOTE' ? 'Удалённо' : 'Не выбран'}
              </Descriptions.Item>
            </Descriptions>
            {current.unavailableReason && current.myStatus === 'NONE' && (
              <Alert type="warning" showIcon message={current.unavailableReason} />
            )}
            <Space wrap>
              {current.myStatus === 'NONE' && (
                <>
                  <Button type="primary" disabled={!current.canBook} loading={run.isPending}
                    onClick={() => run.mutate({ kind: 'book', day: current })}>
                    Забронировать место
                  </Button>
                  <Button disabled={!current.canRemote} loading={run.isPending}
                    onClick={() => run.mutate({ kind: 'remote', day: current })}>
                    Работаю удалённо
                  </Button>
                </>
              )}
              {current.myStatus !== 'NONE' && (
                <Popconfirm title="Отменить запись?" okText="Отменить запись" cancelText="Назад"
                  disabled={!current.canCancel}
                  onConfirm={() => run.mutate({ kind: 'cancel', day: current })}>
                  <Button danger disabled={!current.canCancel} loading={run.isPending}>Отменить запись</Button>
                </Popconfirm>
              )}
            </Space>
            {current.myStatus !== 'NONE' && !current.canCancel && (
              <Typography.Text type="secondary">Срок отмены этой записи истёк.</Typography.Text>
            )}
          </Space>
        )}
      </Modal>
    </Space>
  )
}
