// API Client for backend endpoints

import type {
  SummaryResponse,
  OperationsPageResponse,
  OperationsQueryParams,
  BatchesPageResponse,
  BatchesQueryParams,
  RetentionConfig,
  HealthResponse,
  ReconcileStatusResponse,
  ReconcileTriggerResponse,
} from '../types/api'

// Base API URL - will be proxied by Vite dev server
const API_BASE_URL = '/api'

// Authentication helper
function getAuthHeader(): string | null {
  const credentials = sessionStorage.getItem('dashboard_auth')
  if (!credentials) {
    return null
  }
  return `Basic ${credentials}`
}

// 401 handler - exported for use by components
export function handleUnauthorized() {
  sessionStorage.removeItem('dashboard_auth')
  window.location.href = '/login'
}

// Helper function to build query string
function buildQueryString(params: Record<string, any>): string {
  const searchParams = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      searchParams.append(key, String(value))
    }
  })

  const queryString = searchParams.toString()
  return queryString ? `?${queryString}` : ''
}

// Generic fetch wrapper with error handling and authentication
async function fetchJSON<T>(url: string, options: RequestInit = {}): Promise<T> {
  // Add authentication header if available
  const authHeader = getAuthHeader()
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string> || {}),
  }

  if (authHeader) {
    headers['Authorization'] = authHeader
  }

  const response = await fetch(url, {
    ...options,
    headers,
  })

  // Handle unauthorized responses
  if (response.status === 401) {
    handleUnauthorized()
    throw new Error('Unauthorized')
  }

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  return response.json()
}

// API Client functions
export const apiClient = {
  // GET /api/summary
  async getSummary(): Promise<SummaryResponse> {
    return fetchJSON<SummaryResponse>(`${API_BASE_URL}/summary`)
  },

  // GET /api/operations
  async getOperations(params: OperationsQueryParams = {}): Promise<OperationsPageResponse> {
    const queryString = buildQueryString(params)
    return fetchJSON<OperationsPageResponse>(`${API_BASE_URL}/operations${queryString}`)
  },

  // GET /api/batches
  async getBatches(params: BatchesQueryParams = {}): Promise<BatchesPageResponse> {
    const queryString = buildQueryString(params)
    return fetchJSON<BatchesPageResponse>(`${API_BASE_URL}/batches${queryString}`)
  },

  // GET /api/config/retention
  async getRetentionConfig(): Promise<RetentionConfig> {
    return fetchJSON<RetentionConfig>(`${API_BASE_URL}/config/retention`)
  },

  // PUT /api/config/retention
  async updateRetentionConfig(config: RetentionConfig): Promise<RetentionConfig> {
    const authHeader = getAuthHeader()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }

    if (authHeader) {
      headers['Authorization'] = authHeader
    }

    const response = await fetch(`${API_BASE_URL}/config/retention`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(config),
    })

    if (response.status === 401) {
      handleUnauthorized()
      throw new Error('Unauthorized')
    }

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    return response.json()
  },

  // GET /health (MicroProfile Health endpoint)
  async getHealth(): Promise<HealthResponse> {
    return fetchJSON<HealthResponse>('/health')
  },

  // GET /api/reconcile/status
  async getReconcileStatus(): Promise<ReconcileStatusResponse> {
    return fetchJSON<ReconcileStatusResponse>(`${API_BASE_URL}/reconcile/status`)
  },

  // POST /api/reconcile/trigger
  async triggerReconcile(): Promise<ReconcileTriggerResponse> {
    const authHeader = getAuthHeader()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }

    if (authHeader) {
      headers['Authorization'] = authHeader
    }

    const response = await fetch(`${API_BASE_URL}/reconcile/trigger`, {
      method: 'POST',
      headers,
    })

    if (response.status === 401) {
      handleUnauthorized()
      throw new Error('Unauthorized')
    }

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: 'Unknown error' }))
      throw new Error(errorData.error || `HTTP error! status: ${response.status}`)
    }

    return response.json()
  },
}
