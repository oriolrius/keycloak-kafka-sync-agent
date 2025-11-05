import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { HealthResponse } from '../types/api'

export function useHealth() {
  return useQuery<HealthResponse, Error>({
    queryKey: ['health'],
    queryFn: () => apiClient.getHealth(),
    // Poll every 10 seconds for health status
    refetchInterval: 10000,
    // Always keep health data fresh
    staleTime: 5000,
  })
}
