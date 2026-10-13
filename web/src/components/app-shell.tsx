import { Inbox } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-svh bg-muted/40">
      <header className="border-b bg-background">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4">
          <Inbox className="size-5" aria-hidden />
          <Link to="/" className="font-semibold">
            Morning Queue
          </Link>
          <span className="text-sm text-muted-foreground">· Onboarding review</span>
        </div>
      </header>
      <main className="mx-auto max-w-6xl p-4 md:p-6">{children}</main>
    </div>
  )
}
