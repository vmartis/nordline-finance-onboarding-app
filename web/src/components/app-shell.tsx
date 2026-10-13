import { useQueryClient } from '@tanstack/react-query'
import { Inbox, LogOut } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { clearSession, useSession } from '@/lib/auth'

export function AppShell({ children }: { children: ReactNode }) {
  const session = useSession()
  const queryClient = useQueryClient()
  const logout = () => {
    clearSession()
    queryClient.clear() // forget cached cases, including personal data
  }
  return (
    <div className="min-h-svh bg-muted/40">
      <header className="border-b bg-background">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4">
          <Inbox className="size-5" aria-hidden />
          <Link to="/" className="font-semibold">
            Morning Queue
          </Link>
          <span className="text-sm text-muted-foreground">· Onboarding review</span>
          {session && (
            <div className="ml-auto flex items-center gap-2 text-sm">
              <span className="text-muted-foreground">Logged in as</span>
              <span className="font-medium">{session.username}</span>
              <Button variant="ghost" size="sm" onClick={logout}>
                <LogOut aria-hidden /> Log out
              </Button>
            </div>
          )}
        </div>
      </header>
      <main className="mx-auto max-w-6xl p-4 md:p-6">{children}</main>
    </div>
  )
}
