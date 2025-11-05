import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card'
import { AlertCircle } from 'lucide-react'

export default function OidcCallback() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { setOidcTokens } = useAuth()
  const [error, setError] = useState('')

  useEffect(() => {
    const handleCallback = async () => {
      const code = searchParams.get('code')
      const errorParam = searchParams.get('error')
      const errorDescription = searchParams.get('error_description')

      if (errorParam) {
        setError(errorDescription || errorParam)
        return
      }

      if (!code) {
        setError('No authorization code received')
        return
      }

      try {
        // Get OIDC config from session storage
        const keycloakUrl = sessionStorage.getItem('oidc_keycloak_url')
        const realm = sessionStorage.getItem('oidc_realm')
        const clientId = sessionStorage.getItem('oidc_client_id')

        if (!keycloakUrl || !realm || !clientId) {
          setError('Missing OIDC configuration')
          return
        }

        // Exchange code for tokens
        const redirectUri = `${window.location.origin}/oidc-callback`
        const tokenUrl = `${keycloakUrl}/realms/${realm}/protocol/openid-connect/token`

        const response = await fetch(tokenUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
          body: new URLSearchParams({
            grant_type: 'authorization_code',
            code,
            redirect_uri: redirectUri,
            client_id: clientId,
          }),
        })

        if (!response.ok) {
          const errorData = await response.json().catch(() => ({ error: 'Unknown error' }))
          throw new Error(errorData.error_description || errorData.error || 'Token exchange failed')
        }

        const tokens = await response.json()

        // Store tokens and redirect to dashboard
        setOidcTokens(tokens.access_token, tokens.refresh_token)
        navigate('/')
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to complete authentication')
      }
    }

    handleCallback()
  }, [searchParams, navigate, setOidcTokens])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800 p-4">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle className="text-destructive flex items-center gap-2">
              <AlertCircle className="h-5 w-5" />
              Authentication Failed
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-4">{error}</p>
            <button
              onClick={() => navigate('/login')}
              className="text-primary hover:underline text-sm"
            >
              Return to login
            </button>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-900 dark:to-slate-800">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>Completing Authentication...</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex justify-center">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
