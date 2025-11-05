import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { AlertCircle } from 'lucide-react'

export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [showOidcConfig, setShowOidcConfig] = useState(false)
  const [oidcKeycloakUrl, setOidcKeycloakUrl] = useState('https://localhost:57003')
  const [oidcRealm, setOidcRealm] = useState('master')
  const [oidcClientId, setOidcClientId] = useState('admin-cli')
  const { login, loginWithOidc } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setIsLoading(true)

    try {
      // Attempt to login
      login(username, password)

      // Test the credentials by making a request to the API
      const authHeader = `Basic ${btoa(`${username}:${password}`)}`
      const response = await fetch('/api/summary', {
        headers: {
          Authorization: authHeader,
        },
      })

      if (response.ok) {
        // Credentials are valid, navigate to dashboard
        navigate('/')
      } else if (response.status === 401) {
        setError('Invalid username or password')
        // Clear the stored credentials
        sessionStorage.removeItem('dashboard_auth')
      } else {
        setError('An error occurred. Please try again.')
      }
    } catch (err) {
      setError('Failed to connect to the server')
    } finally {
      setIsLoading(false)
    }
  }

  const handleOidcLogin = () => {
    loginWithOidc(oidcKeycloakUrl, oidcRealm, oidcClientId)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-bold text-center">Dashboard Login</CardTitle>
          <CardDescription className="text-center">
            Enter your credentials to access the Keycloak Kafka Sync Agent dashboard
          </CardDescription>
        </CardHeader>
        <CardContent>
          {!showOidcConfig ? (
            <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="flex items-center gap-2 p-3 rounded-md bg-red-50 dark:bg-red-950 text-red-700 dark:text-red-300 text-sm">
                <AlertCircle className="h-4 w-4" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-2">
              <label htmlFor="username" className="text-sm font-medium">
                Username
              </label>
              <Input
                id="username"
                type="text"
                placeholder="Enter username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="username"
                disabled={isLoading}
              />
            </div>

            <div className="space-y-2">
              <label htmlFor="password" className="text-sm font-medium">
                Password
              </label>
              <Input
                id="password"
                type="password"
                placeholder="Enter password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
                disabled={isLoading}
              />
            </div>

            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading ? 'Signing in...' : 'Sign In'}
            </Button>

            <div className="relative my-4">
              <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t" />
              </div>
              <div className="relative flex justify-center text-xs uppercase">
                <span className="bg-background px-2 text-muted-foreground">Or</span>
              </div>
            </div>

            <Button
              type="button"
              variant="outline"
              className="w-full"
              onClick={() => setShowOidcConfig(true)}
            >
              Sign in with Keycloak OIDC
            </Button>
          </form>
          ) : (
            <div className="space-y-4">
              <div className="space-y-2">
                <label htmlFor="keycloakUrl" className="text-sm font-medium">
                  Keycloak URL
                </label>
                <Input
                  id="keycloakUrl"
                  type="text"
                  placeholder="https://localhost:57003"
                  value={oidcKeycloakUrl}
                  onChange={(e) => setOidcKeycloakUrl(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="realm" className="text-sm font-medium">
                  Realm
                </label>
                <Input
                  id="realm"
                  type="text"
                  placeholder="master"
                  value={oidcRealm}
                  onChange={(e) => setOidcRealm(e.target.value)}
                />
              </div>

              <div className="space-y-2">
                <label htmlFor="clientId" className="text-sm font-medium">
                  Client ID
                </label>
                <Input
                  id="clientId"
                  type="text"
                  placeholder="admin-cli"
                  value={oidcClientId}
                  onChange={(e) => setOidcClientId(e.target.value)}
                />
              </div>

              <Button type="button" className="w-full" onClick={handleOidcLogin}>
                Continue with Keycloak
              </Button>

              <Button
                type="button"
                variant="ghost"
                className="w-full"
                onClick={() => setShowOidcConfig(false)}
              >
                Back to Basic Auth
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
