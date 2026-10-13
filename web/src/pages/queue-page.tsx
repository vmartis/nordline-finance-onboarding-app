import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CircleAlert, Inbox, Play } from 'lucide-react'
import { useEffect } from 'react'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { CaseSheet } from '@/components/case-sheet'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { fetchQueue, takeNext, type QueueItem } from '@/lib/api'
import { backlogSummary } from '@/lib/backlog'
import { rowTone, timeLeft, type RowTone } from '@/lib/time-left'

export function QueuePage() {
  const queue = useQuery({ queryKey: ['queue'], queryFn: fetchQueue })
  const { caseId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const next = useMutation({
    mutationFn: takeNext,
    onSuccess: (nextCaseId) => {
      queryClient.invalidateQueries({ queryKey: ['queue'] })
      if (nextCaseId) navigate(`/cases/${encodeURIComponent(nextCaseId)}`)
      else toast.info('No free cases left: every pending case is taken.')
    },
    onError: (error) => toast.error(error.message),
  })

  useEffect(() => {
    if (queue.isError) toast.error('Could not load the queue.')
  }, [queue.isError])

  return (
    <>
      <CaseSheet caseId={caseId} onClose={() => navigate('/')} />
      <Card>
        <CardHeader>
          <CardTitle>Pending cases</CardTitle>
          <CardDescription>
            {queue.data ? `${queue.data.length} pending cases, most urgent first` : 'Most urgent first'}
          </CardDescription>
          <CardAction className="flex items-center gap-2">
            <Button onClick={() => next.mutate()} disabled={next.isPending}>
              <Play aria-hidden /> {next.isPending ? 'Taking…' : 'Take next'}
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent>
          {queue.data && queue.data.length > 0 && <BacklogStrip items={queue.data} />}
          {queue.isPending && <LoadingRows />}
          {queue.isError && (
            <EmptyState
              icon={<CircleAlert className="size-8 text-destructive" aria-hidden />}
              title="Could not load the queue"
              text="The server did not answer. Your data is safe; try again."
              action={<Button onClick={() => queue.refetch()}>Try again</Button>}
            />
          )}
          {queue.data?.length === 0 && (
            <EmptyState
              icon={<Inbox className="size-8 text-muted-foreground" aria-hidden />}
              title="No pending cases"
              text="Nothing is waiting for review. New cases appear after the next import."
            />
          )}
          {queue.data && queue.data.length > 0 && (
            <QueueTable items={queue.data} onOpen={(id) => navigate(`/cases/${encodeURIComponent(id)}`)} />
          )}
        </CardContent>
      </Card>
    </>
  )
}

const ROW_TONE: Record<RowTone, string> = {
  danger: 'bg-red-50 hover:bg-red-100',
  warning: 'bg-amber-50 hover:bg-amber-100',
  none: '',
}

function QueueTable({ items, onOpen }: { items: QueueItem[]; onOpen: (caseId: string) => void }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Time left</TableHead>
          <TableHead>Case</TableHead>
          <TableHead>Document</TableHead>
          <TableHead>Flags</TableHead>
          <TableHead className="text-right">Attempt</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow
            key={item.caseId}
            className={`cursor-pointer ${ROW_TONE[rowTone(item.minutesLeft)]}`}
            onClick={() => onOpen(item.caseId)}
          >
            <TableCell>
              <TimeLeftBadge minutes={item.minutesLeft} />
            </TableCell>
            <TableCell className="font-mono text-xs">
              <button
                type="button"
                className="underline-offset-4 hover:underline"
                onClick={(e) => {
                  e.stopPropagation()
                  onOpen(item.caseId)
                }}
              >
                {item.caseId}
              </button>
              {item.claimedBy && (
                <Badge variant="secondary" className="ml-2">
                  taken by {item.claimedBy}
                </Badge>
              )}
              {item.dataProblems.length > 0 && (
                <Badge
                  variant="outline"
                  className="ml-2 border-amber-500 text-amber-700"
                  title={item.dataProblems.join('\n')}
                >
                  data problem
                </Badge>
              )}
            </TableCell>
            <TableCell>{item.documentType ?? '—'}</TableCell>
            <TableCell>
              <div className="flex flex-wrap gap-1">
                {item.flags.map((flag) => (
                  <Badge key={flag} variant="outline">
                    {flag}
                  </Badge>
                ))}
              </div>
            </TableCell>
            <TableCell className="text-right">{item.attempt}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

/** One line with the pressure at a glance; same rules as the time badges. */
function BacklogStrip({ items }: { items: QueueItem[] }) {
  const { overdue, soon, later, unknown } = backlogSummary(items)
  return (
    <div className="mb-4 flex flex-wrap items-center gap-2 text-sm" aria-label="Backlog by time left">
      {/* A zero count stays grey: no alarm when nothing is late. */}
      <Badge variant={overdue > 0 ? 'destructive' : 'secondary'}>{overdue} overdue</Badge>
      <Badge
        variant={soon > 0 ? 'outline' : 'secondary'}
        className={soon > 0 ? 'border-orange-500 bg-orange-50 text-orange-700' : undefined}
      >
        {soon} due within 1 h
      </Badge>
      <Badge variant="secondary">{later} later</Badge>
      {unknown > 0 && (
        <Badge variant="outline" className="border-amber-500 text-amber-700">
          {unknown} deadline unknown
        </Badge>
      )}
    </div>
  )
}

function TimeLeftBadge({ minutes }: { minutes: number | null }) {
  const { label, status } = timeLeft(minutes)
  const variant = status === 'overdue' ? 'destructive' : status === 'ok' ? 'secondary' : 'outline'
  const tone = {
    unknown: 'border-amber-500 text-amber-700',
    soon: 'border-orange-500 bg-orange-50 text-orange-700',
    overdue: undefined,
    ok: undefined,
  }[status]
  return (
    <Badge variant={variant} className={tone}>
      {status === 'soon' ? `due soon · ${label}` : label}
    </Badge>
  )
}

function LoadingRows() {
  return (
    <div className="space-y-2" aria-label="Loading">
      {Array.from({ length: 8 }, (_, i) => (
        <Skeleton key={i} className="h-9 w-full" />
      ))}
    </div>
  )
}

function EmptyState(props: { icon: React.ReactNode; title: string; text: string; action?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 py-12 text-center">
      {props.icon}
      <p className="font-medium">{props.title}</p>
      <p className="max-w-sm text-sm text-muted-foreground">{props.text}</p>
      {props.action && <div className="mt-2">{props.action}</div>}
    </div>
  )
}
