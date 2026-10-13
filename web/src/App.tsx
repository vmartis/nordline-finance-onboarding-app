import { Route, Routes } from 'react-router'
import { AppShell } from '@/components/app-shell'
import { NotFoundPage } from '@/pages/not-found-page'
import { QueuePage } from '@/pages/queue-page'

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<QueuePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  )
}
