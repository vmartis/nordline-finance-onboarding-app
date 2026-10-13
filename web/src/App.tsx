import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Route, Routes } from 'react-router'
import { AppShell } from '@/components/app-shell'
import { useSession } from '@/lib/auth'
import { LoginPage } from '@/pages/login-page'
import { NotFoundPage } from '@/pages/not-found-page'
import { QueuePage } from '@/pages/queue-page'

export default function App() {
  const session = useSession()
  const queryClient = useQueryClient()
  // Whenever the session ends (logout or expired token), forget cached cases, which hold personal data.
  useEffect(() => {
    if (!session) queryClient.clear()
  }, [session, queryClient])
  if (!session) {
    return (
      <AppShell>
        <LoginPage />
      </AppShell>
    )
  }
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<QueuePage />} />
        <Route path="/cases/:caseId" element={<QueuePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  )
}
