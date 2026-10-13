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
  fetchReviewers,
  ApiError,
  postDecision,
  type CaseDetail,
  type ReasonCode,
  type Reviewer,
} from '@/lib/api'
import { flagInfo, scoreBandFor, type FlagSeverity, type ScoreBand } from '@/lib/flags'
import { loadReviewer, saveReviewer } from '@/lib/reviewer'
import {
  attemptFor,
  DECISIONS,
  decisionSchema,
  rememberedReviewer,
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
          {detail.data ? <Badge variant="secondary">{detail.data.status}</Badge> : 'Loading case…'}
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
                    <Badge variant={a.status === 'PENDING_REVIEW' ? 'secondary' : 'outline'}>{a.status}</Badge>
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

/** The form appears only when reviewers and reason codes are loaded, so every default matches a visible option. */
function DecisionSection({ caseId, onDone }: { caseId: string; onDone: () => void }) {
  const reviewers = useQuery({
    queryKey: ['reviewers'],
    queryFn: fetchReviewers,
    staleTime: Infinity,
  })
  const reasonCodes = useQuery({
    queryKey: ['reason-codes'],
    queryFn: fetchReasonCodes,
    staleTime: Infinity,
  })

  if (reviewers.isError || reasonCodes.isError) {
    return (
      <p className="flex items-center gap-2 border-t pt-4 text-sm text-destructive">
        <CircleAlert className="size-4" aria-hidden /> Could not load reviewers or reason codes. Close and open the case
        again.
      </p>
    )
  }
  if (!reviewers.data || !reasonCodes.data) return <Skeleton className="h-64 w-full" />
  return <DecisionForm caseId={caseId} onDone={onDone} reviewers={reviewers.data} reasonCodes={reasonCodes.data} />
}

function DecisionForm(props: { caseId: string; onDone: () => void; reviewers: Reviewer[]; reasonCodes: ReasonCode[] }) {
  const { caseId, onDone } = props
  const queryClient = useQueryClient()
  const form = useForm<DecisionInput>({
    resolver: zodResolver(decisionSchema),
    defaultValues: {
      reviewer: rememberedReviewer(loadReviewer(), props.reviewers),
      reasonCode: '',
      comment: '',
    },
  })
  const lastAttempt = useRef<Attempt | null>(null)
  const save = useMutation({
    mutationFn: ({ input, key }: Attempt) => postDecision(caseId, input, key),
    onSuccess: (result, { input }) => {
      saveReviewer(input.reviewer)
      toast.success(`${caseId} saved in the back office as ${result.status}.`)
      queryClient.invalidateQueries({ queryKey: ['queue'] })
      queryClient.invalidateQueries({ queryKey: ['case', caseId] })
      onDone()
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
      <Field label="Your name" id="reviewer" error={errors.reviewer?.message}>
        <NativeSelect id="reviewer" className="w-full" {...form.register('reviewer')}>
          <NativeSelectOption value="">Pick your name</NativeSelectOption>
          {props.reviewers.map((r) => (
            <NativeSelectOption key={r.id} value={r.id}>
              {r.id} (shift {r.shift})
            </NativeSelectOption>
          ))}
        </NativeSelect>
      </Field>
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
      <Button type="submit" className="w-full" disabled={save.isPending}>
        {save.isPending ? 'Saving…' : 'Save decision'}
      </Button>
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
