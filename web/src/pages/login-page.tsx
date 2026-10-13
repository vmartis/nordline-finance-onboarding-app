import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { login } from '@/lib/api'
import { endedBecause, setSession } from '@/lib/auth'

const loginSchema = z.object({
  username: z.string().trim().min(1, 'Enter your name.'),
  password: z.string().min(1, 'Enter your password.'),
})

type LoginInput = z.infer<typeof loginSchema>

export function LoginPage() {
  const form = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: '', password: '' },
  })
  const submit = useMutation({
    mutationFn: (input: LoginInput) => login(input.username.trim(), input.password),
    onSuccess: setSession,
  })
  const errors = form.formState.errors

  return (
    <Card className="mx-auto mt-12 max-w-sm">
      <CardHeader>
        <CardTitle>Log in</CardTitle>
        <CardDescription>Use your reviewer name, for example j.sebo.</CardDescription>
      </CardHeader>
      <CardContent>
        {endedBecause() === 'expired' && (
          <p className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-2 text-sm text-amber-900">
            Your session ended. Log in again to continue.
          </p>
        )}
        <form onSubmit={form.handleSubmit((input) => submit.mutate(input))} className="space-y-4" noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="username">Name</Label>
            <Input id="username" autoComplete="username" autoFocus {...form.register('username')} />
            {errors.username && <p className="text-sm text-destructive">{errors.username.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <Input id="password" type="password" autoComplete="current-password" {...form.register('password')} />
            {errors.password && <p className="text-sm text-destructive">{errors.password.message}</p>}
          </div>
          {submit.isError && <p className="text-sm text-destructive">{submit.error.message}</p>}
          <Button type="submit" className="w-full" disabled={submit.isPending}>
            {submit.isPending ? 'Logging in…' : 'Log in'}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
