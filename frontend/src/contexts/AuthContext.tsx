import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'

type AuthMode = 'basic' | 'oidc'

interface AuthContextType {
  isAuthenticated: boolean
  authMode: AuthMode
  login: (username: string, password: string) => void
  loginWithOidc: (keycloakUrl: string, realm: string, clientId: string) => void
  logout: () => void
  getAuthHeader: () => string | null
  setOidcTokens: (accessToken: string, refreshToken?: string) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

const AUTH_STORAGE_KEY = 'dashboard_auth'
const AUTH_MODE_KEY = 'dashboard_auth_mode'
const OIDC_ACCESS_TOKEN_KEY = 'dashboard_oidc_access_token'
const OIDC_REFRESH_TOKEN_KEY = 'dashboard_oidc_refresh_token'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false)
  const [authMode, setAuthMode] = useState<AuthMode>('basic')

  // Check for existing auth credentials on mount
  useEffect(() => {
    const storedMode = sessionStorage.getItem(AUTH_MODE_KEY) as AuthMode | null
    const storedBasicAuth = sessionStorage.getItem(AUTH_STORAGE_KEY)
    const storedOidcToken = sessionStorage.getItem(OIDC_ACCESS_TOKEN_KEY)

    if (storedMode === 'oidc' && storedOidcToken) {
      setAuthMode('oidc')
      setIsAuthenticated(true)
    } else if (storedBasicAuth) {
      setAuthMode('basic')
      setIsAuthenticated(true)
    }
  }, [])

  const login = (username: string, password: string) => {
    // Encode credentials as Basic Auth
    const credentials = btoa(`${username}:${password}`)
    sessionStorage.setItem(AUTH_STORAGE_KEY, credentials)
    sessionStorage.setItem(AUTH_MODE_KEY, 'basic')
    setAuthMode('basic')
    setIsAuthenticated(true)
  }

  const loginWithOidc = (keycloakUrl: string, realm: string, clientId: string) => {
    // Store OIDC config for callback
    sessionStorage.setItem('oidc_keycloak_url', keycloakUrl)
    sessionStorage.setItem('oidc_realm', realm)
    sessionStorage.setItem('oidc_client_id', clientId)

    // Build authorization URL
    const redirectUri = `${window.location.origin}/oidc-callback`
    const authUrl = `${keycloakUrl}/realms/${realm}/protocol/openid-connect/auth`
    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: 'code',
      scope: 'openid profile email',
    })

    // Redirect to Keycloak for authentication
    window.location.href = `${authUrl}?${params.toString()}`
  }

  const setOidcTokens = (accessToken: string, refreshToken?: string) => {
    sessionStorage.setItem(OIDC_ACCESS_TOKEN_KEY, accessToken)
    if (refreshToken) {
      sessionStorage.setItem(OIDC_REFRESH_TOKEN_KEY, refreshToken)
    }
    sessionStorage.setItem(AUTH_MODE_KEY, 'oidc')
    setAuthMode('oidc')
    setIsAuthenticated(true)
  }

  const logout = () => {
    sessionStorage.removeItem(AUTH_STORAGE_KEY)
    sessionStorage.removeItem(OIDC_ACCESS_TOKEN_KEY)
    sessionStorage.removeItem(OIDC_REFRESH_TOKEN_KEY)
    sessionStorage.removeItem(AUTH_MODE_KEY)
    setIsAuthenticated(false)
  }

  const getAuthHeader = (): string | null => {
    const mode = sessionStorage.getItem(AUTH_MODE_KEY) as AuthMode | null

    if (mode === 'oidc') {
      const token = sessionStorage.getItem(OIDC_ACCESS_TOKEN_KEY)
      if (!token) {
        return null
      }
      return `Bearer ${token}`
    }

    // Default to Basic Auth
    const credentials = sessionStorage.getItem(AUTH_STORAGE_KEY)
    if (!credentials) {
      return null
    }
    return `Basic ${credentials}`
  }

  return (
    <AuthContext.Provider value={{
      isAuthenticated,
      authMode,
      login,
      loginWithOidc,
      logout,
      getAuthHeader,
      setOidcTokens
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
