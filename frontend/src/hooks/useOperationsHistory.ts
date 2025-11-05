import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'

interface HistoricalDataPoint {
  timestamp: string
  count: number
  errorCount: number
}

interface UseOperationsHistoryOptions {
  hours: 24 | 72
}

export function useOperationsHistory({ hours }: UseOperationsHistoryOptions) {
  return useQuery<HistoricalDataPoint[], Error>({
    queryKey: ['operations', 'history', hours],
    queryFn: async () => {
      const now = new Date()
      const startTime = new Date(now.getTime() - hours * 60 * 60 * 1000)

      // Fetch all operations in the time range
      const response = await apiClient.getOperations({
        startTime: startTime.toISOString(),
        endTime: now.toISOString(),
        pageSize: 10000, // Get all operations
      })

      // Group operations by hour
      const hourlyData = new Map<string, { count: number; errorCount: number }>()

      response.operations.forEach((op) => {
        const opDate = new Date(op.occurredAt)
        const hourKey = new Date(
          opDate.getFullYear(),
          opDate.getMonth(),
          opDate.getDate(),
          opDate.getHours()
        ).toISOString()

        const existing = hourlyData.get(hourKey) || { count: 0, errorCount: 0 }
        existing.count++
        if (op.result === 'ERROR') {
          existing.errorCount++
        }
        hourlyData.set(hourKey, existing)
      })

      // Convert to array and sort by timestamp
      const dataPoints: HistoricalDataPoint[] = Array.from(hourlyData.entries())
        .map(([timestamp, data]) => ({
          timestamp,
          count: data.count,
          errorCount: data.errorCount,
        }))
        .sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())

      return dataPoints
    },
    refetchInterval: 30000, // Refresh every 30 seconds
    staleTime: 15000,
  })
}
