import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { ReconcileStatusResponse, ReconcileTriggerResponse } from '../types/api'

export function useReconcileStatus() {
  return useQuery<ReconcileStatusResponse, Error>({
    queryKey: ['reconcile', 'status'],
    queryFn: () => apiClient.getReconcileStatus(),
    refetchInterval: 5000, // Poll every 5 seconds
    staleTime: 2000,
  })
}

export function useReconcileTrigger() {
  const queryClient = useQueryClient()

  return useMutation<ReconcileTriggerResponse, Error>({
    mutationFn: () => apiClient.triggerReconcile(),
    onSuccess: () => {
      // Invalidate queries to refresh data after reconciliation
      queryClient.invalidateQueries({ queryKey: ['reconcile', 'status'] })
      queryClient.invalidateQueries({ queryKey: ['summary'] })
      queryClient.invalidateQueries({ queryKey: ['operations'] })
      queryClient.invalidateQueries({ queryKey: ['batches'] })
    },
  })
}
