import { SearchX } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

export function NotFoundPage() {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-2 py-12 text-center">
        <SearchX className="size-8 text-muted-foreground" aria-hidden />
        <p className="font-medium">Page not found</p>
        <Button nativeButton={false} render={<Link to="/" />}>
          Back to the queue
        </Button>
      </CardContent>
    </Card>
  )
}
