import { useState } from 'react'
import { Badge, Button, Empty, List, Popover, Spin, Typography } from 'antd'
import { BellOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '../api/endpoints'
import { formatDateTime } from '../utils/format'
import type { AppNotification } from '../types/api'

/** Внутренние уведомления. Обновляются раз в минуту; прочитанные отмечаются по клику. */
export function NotificationBell() {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['notifications'], queryFn: notificationsApi.list, refetchInterval: 60_000,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })
  const read = useMutation({ mutationFn: (id: number) => notificationsApi.read(id), onSuccess: refresh })
  const readAll = useMutation({ mutationFn: notificationsApi.readAll, onSuccess: refresh })

  const content = (
    <div style={{ width: 340, maxWidth: '80vw' }}>
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 16 }}><Spin /></div>
      ) : isError ? (
        <Typography.Text type="danger">Не удалось загрузить уведомления</Typography.Text>
      ) : (data?.items.length ?? 0) === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Уведомлений нет" />
      ) : (
        <>
          <div style={{ textAlign: 'right' }}>
            <Button type="link" size="small" disabled={(data?.unread ?? 0) === 0} onClick={() => readAll.mutate()}>
              Прочитать все
            </Button>
          </div>
          <List<AppNotification> size="small" dataSource={data?.items ?? []} style={{ maxHeight: 360, overflow: 'auto' }}
            renderItem={(n) => (
              <List.Item key={n.id} style={{ cursor: n.read ? 'default' : 'pointer', opacity: n.read ? 0.65 : 1 }}
                onClick={() => !n.read && read.mutate(n.id)}>
                <List.Item.Meta
                  title={<span>{!n.read && <Badge status="processing" style={{ marginInlineEnd: 6 }} />}{n.title}</span>}
                  description={<><div>{n.message}</div><Typography.Text type="secondary">{formatDateTime(n.createdAt)}</Typography.Text></>} />
              </List.Item>
            )} />
        </>
      )}
    </div>
  )

  return (
    <Popover content={content} trigger="click" open={open} onOpenChange={setOpen} placement="bottomRight" title="Уведомления">
      <Badge count={data?.unread ?? 0} size="small" overflowCount={99}>
        <Button type="text" shape="circle" icon={<BellOutlined />}
          aria-label={`Уведомления${data?.unread ? `, непрочитанных: ${data.unread}` : ''}`} />
      </Badge>
    </Popover>
  )
}
