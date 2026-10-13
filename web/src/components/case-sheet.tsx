import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CircleAlert, TriangleAlert } from 'lucide-react'
import { useRef } from 'react'
import { useNavigate } from 'react-router'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import {
  fetchCase,
  fetchReasonCodes,
  ApiError,
  postDecision,
  takeNext,
  type CaseDetail,
  type ReasonCode,
} from '@/lib/api'
import { flagInfo, scoreBandFor, type FlagSeverity, type ScoreBand } from '@/lib/flags'
import { useSession } from '@/lib/auth'
import { statusLabel } from '@/lib/status'
import {
  afterSaveLocation,
  attemptFor,
  DECISIONS,
  decisionSchema,
  type Attempt,
  type DecisionInput,
} from '@/lib/decision'

export function CaseSheet({ caseId, onClose }: { caseId: string | undefined; onClose: () => void }) {
  return (
    <Sheet open={caseId !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full overflow-y-auto sm:max-w-lg">
        {caseId && <CaseSheetBody key={caseId} caseId={caseId} onClose={onClose} />}
      </SheetContent>
    </Sheet>
  )
}

function CaseSheetBody({ caseId, onClose }: { caseId: string; onClose: () => void }) {
  const detail = useQuery({
    queryKey: ['case', caseId],
    queryFn: () => fetchCase(caseId),
  })

  return (
    <>
      <SheetHeader>
        <SheetTitle className="font-mono">{caseId}</SheetTitle>
        <SheetDescription>
          {detail.data ? <Badge variant="secondary">{statusLabel(detail.data.status)}</Badge> : 'Loading case…'}
        </SheetDescription>
      </SheetHeader>
      <div className="space-y-6 px-4 pb-6">
        {detail.isPending && <Skeleton className="h-48 w-full" />}
        {detail.isError && (
          <p className="flex items-center gap-2 text-sm text-destructive">
            <CircleAlert className="size-4" aria-hidden /> {detail.error.message}
          </p>
        )}
        {detail.data && <CaseFacts detail={detail.data} />}
        {detail.data?.status === 'PENDING_REVIEW' && <DecisionSection caseId={caseId} onDone={onClose} />}
      </div>
    </>
  )
}

const BAND_STYLE: Record<ScoreBand, string> = {
  good: 'border-emerald-300 bg-emerald-50 text-emerald-800',
  borderline: 'border-amber-300 bg-amber-50 text-amber-800',
  bad: 'border-red-300 bg-red-50 text-red-800',
  missing: 'border-dashed text-muted-foreground',
  flagged: 'border-amber-300 bg-amber-50 text-amber-800',
}

const SEVERITY_TEXT: Record<FlagSeverity, string> = { serious: 'serious', check: 'needs a check', minor: 'minor' }

const SEVERITY_STYLE: Record<FlagSeverity, string> = {
  serious: 'border-red-300 text-red-700',
  check: 'border-amber-300 text-amber-700',
  minor: 'text-muted-foreground',
}

function CaseFacts({ detail }: { detail: CaseDetail }) {
  const navigate = useNavigate()
  return (
    <div className="space-y-4 text-sm">
      {detail.dataProblems.length > 0 && (
        <section className="rounded-md border border-amber-500 bg-amber-50 p-3 text-amber-900">
          <h3 className="mb-1 flex items-center gap-2 font-medium">
            <TriangleAlert className="size-4" aria-hidden /> Data problems in the export
          </h3>
          <ul className="list-disc space-y-0.5 pl-5">
            {detail.dataProblems.map((problem) => (
              <li key={problem}>{problem}</li>
            ))}
          </ul>
        </section>
      )}
      <section>
        <h3 className="mb-2 font-medium">Checks</h3>
        <dl className="grid grid-cols-3 gap-2">
          <Score
            label="Document"
            band={scoreBandFor('documentAuthenticity', detail.scores.documentAuthenticity, detail.flags)}
            value={detail.scores.documentAuthenticity}
          />
          <Score
            label="Face match"
            band={scoreBandFor('faceMatch', detail.scores.faceMatch, detail.flags)}
            value={detail.scores.faceMatch}
          />
          <Score
            label="Liveness"
            band={scoreBandFor('liveness', detail.scores.liveness, detail.flags)}
            value={detail.scores.liveness}
          />
        </dl>
        <p className="mt-1 text-xs text-muted-foreground">
          Bands: good ≥ 0.80, borderline ≥ 0.60; a flagged check is never shown as good. Guidance, not policy.
        </p>
      </section>
      {detail.flags.length > 0 && (
        <section>
          <h3 className="mb-2 font-medium">Flags</h3>
          <ul className="space-y-2">
            {detail.flags.map((flag) => {
              const info = flagInfo(flag)
              return (
                <li key={flag} className="rounded-md border p-2">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{info.label}</span>
                    <Badge variant="outline" className={SEVERITY_STYLE[info.severity]}>
                      usually: {SEVERITY_TEXT[info.severity]}
                    </Badge>
                  </div>
                  <p className="mt-1 text-muted-foreground">{info.text}</p>
                </li>
              )
            })}
          </ul>
          <p className="mt-1 text-xs text-muted-foreground">
            Flag help is guidance for reviewers, not compliance policy.
          </p>
        </section>
      )}
      {detail.otherAttempts.length > 0 && (
        <section>
          <h3 className="mb-2 font-medium">Other attempts with the same document</h3>
          <ul className="divide-y rounded-md border">
            {detail.otherAttempts.map((a) => (
              <li key={a.caseId}>
                <button
                  type="button"
                  className="w-full p-2 text-left hover:bg-muted/60"
                  onClick={() => navigate(`/cases/${encodeURIComponent(a.caseId)}`)}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span>
                      Attempt {a.attempt} ({a.attempt < detail.attempt ? 'earlier' : 'later'}) ·{' '}
                      <span className="font-mono text-xs">{a.caseId}</span>
                    </span>
                    <Badge variant={a.status === 'PENDING_REVIEW' ? 'secondary' : 'outline'}>
                      {statusLabel(a.status)}
                    </Badge>
                  </span>
                  {a.flags.length > 0 && (
                    <span className="mt-1 block text-xs text-muted-foreground">
                      {a.flags.map((f) => flagInfo(f).label).join(' · ')}
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
      <section>
        <h3 className="mb-2 font-medium">Applicant</h3>
        <dl className="grid grid-cols-2 gap-2">
          <Fact label="Name" value={detail.fullName ?? '—'} />
          <Fact label="Date of birth" value={detail.dateOfBirth ?? 'unknown'} />
          <Fact label="Document" value={detail.documentType ?? '—'} />
          <Fact label="Document number" value={detail.documentNumber ?? '—'} />
          <Fact label="Attempt" value={String(detail.attempt)} />
        </dl>
        {detail.notes && <p className="mt-2 text-muted-foreground">{detail.notes}</p>}
      </section>
    </div>
  )
}

function Score({ label, value, band }: { label: string; value: number | null; band: ScoreBand }) {
  const text = band === 'flagged' ? 'flagged by the check' : band
  return (
    <div className={`rounded-md border p-2 ${BAND_STYLE[band]}`}>
      <dt className="text-xs">{label}</dt>
      <dd className="font-medium">{value === null ? 'missing' : `${value.toFixed(3)} · ${text}`}</dd>
    </div>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border bg-muted/40 p-2">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  )
}

/** The form appears only when the reason codes are loaded, so every choice is a visible option. */
function DecisionSection({ caseId, onDone }: { caseId: string; onDone: () => void }) {
  const session = useSession()
  const reasonCodes = useQuery({ queryKey: ['reason-codes'], queryFn: fetchReasonCodes, staleTime: Infinity })

  if (reasonCodes.isError) {
    return (
      <p className="flex items-center gap-2 border-t pt-4 text-sm text-destructive">
        <CircleAlert className="size-4" aria-hidden /> Could not load the reason codes. Close and open the case again.
      </p>
    )
  }
  if (!reasonCodes.data || !session) return <Skeleton className="h-64 w-full" />
  return <DecisionForm caseId={caseId} onDone={onDone} reviewer={session.username} reasonCodes={reasonCodes.data} />
}

/** The reviewer is the logged-in user (the server uses the login, not this field). */
function DecisionForm(props: { caseId: string; onDone: () => void; reviewer: string; reasonCodes: ReasonCode[] }) {
  const { caseId, onDone } = props
  const queryClient = useQueryClient()
  const form = useForm<DecisionInput>({
    resolver: zodResolver(decisionSchema),
    defaultValues: { reviewer: props.reviewer, reasonCode: '', comment: '' },
  })
  const lastAttempt = useRef<Attempt | null>(null)
  const afterSave = useRef<'next' | 'close'>('next')
  const navigate = useNavigate()
  const save = useMutation({
    mutationFn: ({ input, key }: Attempt) => postDecision(caseId, input, key),
    onSuccess: async (result) => {
      toast.success(`${caseId} saved in the back office as "${statusLabel(result.status)}".`)
      queryClient.invalidateQueries({ queryKey: ['queue'] })
      queryClient.invalidateQueries({ queryKey: ['case', caseId] })
      if (afterSave.current === 'close') return onDone()
      // "Save & next": take the next most urgent free case right away.
      try {
        const next = await takeNext()
        if (!next) toast.info('No free cases left: every pending case is taken.')
        navigate(afterSaveLocation(next))
      } catch (error) {
        toast.error(error instanceof Error ? error.message : 'Could not take the next case.')
        onDone()
      }
    },
    onError: (error) => {
      toast.error(error.message)
      if (error instanceof ApiError && error.status === 409) {
        // Decided elsewhere: show the fresh status; the form disappears because the case is no longer pending.
        queryClient.invalidateQueries({ queryKey: ['queue'] })
        queryClient.invalidateQueries({ queryKey: ['case', caseId] })
      }
    },
  })
  const errors = form.formState.errors

  return (
    <form
      onSubmit={form.handleSubmit((input) => {
        lastAttempt.current = attemptFor(input, lastAttempt.current, () => crypto.randomUUID())
        save.mutate(lastAttempt.current)
      })}
      className="space-y-4 border-t pt-4"
      noValidate
    >
      <h3 className="font-medium">Decision</h3>
      <p className="text-sm text-muted-foreground">
        Deciding as <span className="font-medium text-foreground">{props.reviewer}</span>
      </p>
      <Field label="Decision" id="decision" error={errors.decision?.message}>
        <NativeSelect id="decision" className="w-full" defaultValue="" {...form.register('decision')}>
          <NativeSelectOption value="">Pick a decision</NativeSelectOption>
          {DECISIONS.map((d) => (
            <NativeSelectOption key={d.value} value={d.value}>
              {d.label}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </Field>
      <Field label="Reason code" id="reasonCode" error={errors.reasonCode?.message}>
        <NativeSelect id="reasonCode" className="w-full" {...form.register('reasonCode')}>
          <NativeSelectOption value="">Pick a reason code</NativeSelectOption>
          {props.reasonCodes.map((r) => (
            <NativeSelectOption key={r.code} value={r.code}>
              {r.label}
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </Field>
      <Field label="Comment" id="comment" error={errors.comment?.message}>
        <Textarea id="comment" rows={3} placeholder="Why, in one or two sentences" {...form.register('comment')} />
      </Field>
      <div className="flex gap-2">
        <Button type="submit" className="flex-1" disabled={save.isPending} onClick={() => (afterSave.current = 'next')}>
          {save.isPending ? 'Saving…' : 'Save & next'}
        </Button>
        <Button type="submit" variant="outline" disabled={save.isPending} onClick={() => (afterSave.current = 'close')}>
          Save & close
        </Button>
      </div>
    </form>
  )
}

function Field(props: { label: string; id: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={props.id}>{props.label}</Label>
      {props.children}
      {props.error && <p className="text-sm text-destructive">{props.error}</p>}
    </div>
  )
}
