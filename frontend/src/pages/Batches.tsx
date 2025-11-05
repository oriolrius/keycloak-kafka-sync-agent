import { useState } from 'react'
import { useBatches } from '../hooks/useBatches'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Badge } from '../components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '../components/ui/collapsible'
import { Download, ChevronDown, ChevronUp, AlertCircle, CheckCircle, XCircle, Clock } from 'lucide-react'

type SortField = 'startedAt' | 'durationMs' | 'itemsTotal' | 'successRate'
type SortDirection = 'asc' | 'desc'

export default function Batches() {
  // Pagination state
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(25)

  // Filter state
  const [startTime, setStartTime] = useState<string>('')
  const [endTime, setEndTime] = useState<string>('')
  const [source, setSource] = useState<string | undefined>(undefined)

  // Sort state
  const [sortField, setSortField] = useState<SortField>('startedAt')
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc')

  // Expanded rows state
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set())

  // Build query params
  const queryParams = {
    page,
    pageSize,
    ...(startTime && { startTime: new Date(startTime).toISOString() }),
    ...(endTime && { endTime: new Date(endTime).toISOString() }),
    ...(source && { source }),
  }

  // Fetch batches with polling
  const { data, isLoading, error } = useBatches(queryParams)

  // Calculate success rate for a batch
  const calculateSuccessRate = (itemsSuccess: number, itemsTotal: number) => {
    if (itemsTotal === 0) return 0
    return Math.round((itemsSuccess / itemsTotal) * 100)
  }

  // Sort batches client-side
  const sortedBatches = data?.batches
    ? [...data.batches].sort((a, b) => {
        let aVal, bVal
        switch (sortField) {
          case 'startedAt':
            aVal = new Date(a.startedAt).getTime()
            bVal = new Date(b.startedAt).getTime()
            break
          case 'durationMs':
            aVal = a.durationMs || 0
            bVal = b.durationMs || 0
            break
          case 'itemsTotal':
            aVal = a.itemsTotal
            bVal = b.itemsTotal
            break
          case 'successRate':
            aVal = calculateSuccessRate(a.itemsSuccess, a.itemsTotal)
            bVal = calculateSuccessRate(b.itemsSuccess, b.itemsTotal)
            break
          default:
            return 0
        }
        if (sortDirection === 'asc') {
          return aVal > bVal ? 1 : -1
        }
        return aVal < bVal ? 1 : -1
      })
    : []

  // Toggle row expansion
  const toggleRow = (id: number) => {
    const newExpanded = new Set(expandedRows)
    if (newExpanded.has(id)) {
      newExpanded.delete(id)
    } else {
      newExpanded.add(id)
    }
    setExpandedRows(newExpanded)
  }

  // Toggle sort
  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc')
    } else {
      setSortField(field)
      setSortDirection('desc')
    }
  }

  // CSV Export
  const handleExport = () => {
    if (!sortedBatches.length) return

    const headers = [
      'Batch ID',
      'Correlation ID',
      'Start Time',
      'End Time',
      'Source',
      'Items Total',
      'Items Success',
      'Items Error',
      'Duration (ms)',
      'Success Rate (%)',
      'Status',
    ]

    const rows = sortedBatches.map((batch) => [
      batch.id.toString(),
      batch.correlationId,
      new Date(batch.startedAt).toISOString(),
      batch.finishedAt ? new Date(batch.finishedAt).toISOString() : 'In Progress',
      batch.source,
      batch.itemsTotal.toString(),
      batch.itemsSuccess.toString(),
      batch.itemsError.toString(),
      batch.durationMs?.toString() || '',
      calculateSuccessRate(batch.itemsSuccess, batch.itemsTotal).toString(),
      batch.complete ? 'Completed' : 'Running',
    ])

    const csv = [
      headers.join(','),
      ...rows.map((row) =>
        row.map((cell) => `"${cell.replace(/"/g, '""')}"`).join(',')
      ),
    ].join('\n')

    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `batches-${new Date().toISOString()}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  // Reset filters
  const handleResetFilters = () => {
    setStartTime('')
    setEndTime('')
    setSource(undefined)
    setPage(0)
  }

  // Format timestamp
  const formatTimestamp = (isoString: string) => {
    const date = new Date(isoString)
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  }

  // Format duration
  const formatDuration = (ms: number | undefined) => {
    if (!ms) return 'N/A'
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    return `${(ms / 60000).toFixed(1)}m`
  }

  // Status badge
  const StatusBadge = ({ complete }: { complete: boolean }) => {
    if (complete) {
      return (
        <Badge variant="default" className="flex items-center gap-1 w-fit">
          <CheckCircle className="h-3 w-3 text-green-600" />
          Completed
        </Badge>
      )
    }
    return (
      <Badge variant="secondary" className="flex items-center gap-1 w-fit">
        <Clock className="h-3 w-3 text-blue-600" />
        Running
      </Badge>
    )
  }

  // Source badge
  const SourceBadge = ({ source }: { source: string }) => {
    const variants: Record<string, { variant: 'default' | 'secondary' | 'outline'; color: string }> = {
      SCHEDULED: { variant: 'default', color: 'text-blue-600' },
      MANUAL: { variant: 'secondary', color: 'text-purple-600' },
      WEBHOOK: { variant: 'outline', color: 'text-orange-600' },
    }
    const config = variants[source] || { variant: 'outline', color: 'text-gray-600' }

    return (
      <Badge variant={config.variant} className="flex items-center gap-1 w-fit">
        <span className={config.color}>{source}</span>
      </Badge>
    )
  }

  // Success rate badge
  const SuccessRateBadge = ({ rate }: { rate: number }) => {
    const getVariant = () => {
      if (rate === 100) return 'default'
      if (rate >= 80) return 'secondary'
      return 'destructive'
    }

    const getIcon = () => {
      if (rate === 100) return CheckCircle
      if (rate >= 80) return AlertCircle
      return XCircle
    }

    const Icon = getIcon()

    return (
      <Badge variant={getVariant()} className="flex items-center gap-1 w-fit">
        <Icon className="h-3 w-3" />
        {rate}%
      </Badge>
    )
  }

  // Sort indicator
  const SortIndicator = ({ field }: { field: SortField }) => {
    if (sortField !== field) return null
    return sortDirection === 'asc' ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />
  }

  if (error) {
    return (
      <div className="p-8">
        <Card className="border-destructive">
          <CardHeader>
            <CardTitle className="text-destructive flex items-center gap-2">
              <AlertCircle className="h-5 w-5" />
              Error Loading Batches
            </CardTitle>
            <CardDescription>{error.message}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">Batch Summary</h1>
          <p className="text-muted-foreground">
            Reconciliation cycle history with success rates and timing information
          </p>
        </div>
        <Button
          onClick={handleExport}
          disabled={!sortedBatches.length}
          className="flex items-center gap-2"
        >
          <Download className="h-4 w-4" />
          Export CSV
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Filter batches by time range and source type</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div>
              <label className="text-sm font-medium mb-1 block">Start Time</label>
              <Input
                type="datetime-local"
                value={startTime}
                onChange={(e) => {
                  setStartTime(e.target.value)
                  setPage(0)
                }}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1 block">End Time</label>
              <Input
                type="datetime-local"
                value={endTime}
                onChange={(e) => {
                  setEndTime(e.target.value)
                  setPage(0)
                }}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1 block">Source Type</label>
              <Select
                value={source || 'all'}
                onValueChange={(value) => {
                  setSource(value === 'all' ? undefined : value)
                  setPage(0)
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All sources" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All sources</SelectItem>
                  <SelectItem value="SCHEDULED">Scheduled</SelectItem>
                  <SelectItem value="MANUAL">Manual</SelectItem>
                  <SelectItem value="WEBHOOK">Webhook</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-end">
              <Button variant="outline" onClick={handleResetFilters} className="w-full">
                Reset
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Batches Table */}
      <Card>
        <CardHeader>
          <div className="flex justify-between items-center">
            <div>
              <CardTitle>Batches</CardTitle>
              <CardDescription>
                Showing {sortedBatches.length} of {data?.totalElements || 0} batches
              </CardDescription>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Page size:</span>
              <Select
                value={pageSize.toString()}
                onValueChange={(value) => {
                  setPageSize(parseInt(value))
                  setPage(0)
                }}
              >
                <SelectTrigger className="w-20">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                  <SelectItem value="100">100</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex items-center justify-center h-64">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            </div>
          ) : sortedBatches.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-64 text-muted-foreground">
              <AlertCircle className="h-12 w-12 mb-4" />
              <p>No batches found</p>
            </div>
          ) : (
            <div className="border rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead></TableHead>
                    <TableHead>Correlation ID</TableHead>
                    <TableHead
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleSort('startedAt')}
                    >
                      <div className="flex items-center gap-1">
                        Start Time
                        <SortIndicator field="startedAt" />
                      </div>
                    </TableHead>
                    <TableHead>End Time</TableHead>
                    <TableHead>Source</TableHead>
                    <TableHead
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleSort('itemsTotal')}
                    >
                      <div className="flex items-center gap-1">
                        Items
                        <SortIndicator field="itemsTotal" />
                      </div>
                    </TableHead>
                    <TableHead>Success</TableHead>
                    <TableHead>Errors</TableHead>
                    <TableHead
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleSort('durationMs')}
                    >
                      <div className="flex items-center gap-1">
                        Duration
                        <SortIndicator field="durationMs" />
                      </div>
                    </TableHead>
                    <TableHead
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleSort('successRate')}
                    >
                      <div className="flex items-center gap-1">
                        Success Rate
                        <SortIndicator field="successRate" />
                      </div>
                    </TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sortedBatches.map((batch) => (
                    <Collapsible
                      key={batch.id}
                      open={expandedRows.has(batch.id)}
                      onOpenChange={() => toggleRow(batch.id)}
                      asChild
                    >
                      <>
                        <TableRow className="cursor-pointer hover:bg-muted/30">
                          <TableCell className="w-8">
                            <CollapsibleTrigger asChild>
                              <Button variant="ghost" size="sm" className="p-0 h-6 w-6">
                                {expandedRows.has(batch.id) ? (
                                  <ChevronDown className="h-4 w-4" />
                                ) : (
                                  <ChevronUp className="h-4 w-4 rotate-180" />
                                )}
                              </Button>
                            </CollapsibleTrigger>
                          </TableCell>
                          <TableCell className="font-mono text-sm">{batch.correlationId}</TableCell>
                          <TableCell className="font-mono text-sm">
                            {formatTimestamp(batch.startedAt)}
                          </TableCell>
                          <TableCell className="font-mono text-sm">
                            {batch.finishedAt ? formatTimestamp(batch.finishedAt) : '—'}
                          </TableCell>
                          <TableCell>
                            <SourceBadge source={batch.source} />
                          </TableCell>
                          <TableCell>{batch.itemsTotal}</TableCell>
                          <TableCell className="text-green-600 font-semibold">
                            {batch.itemsSuccess}
                          </TableCell>
                          <TableCell className="text-red-600 font-semibold">
                            {batch.itemsError}
                          </TableCell>
                          <TableCell>{formatDuration(batch.durationMs)}</TableCell>
                          <TableCell>
                            <SuccessRateBadge
                              rate={calculateSuccessRate(batch.itemsSuccess, batch.itemsTotal)}
                            />
                          </TableCell>
                          <TableCell>
                            <StatusBadge complete={batch.complete} />
                          </TableCell>
                        </TableRow>
                        {expandedRows.has(batch.id) && (
                          <TableRow>
                            <TableCell colSpan={11}>
                              <CollapsibleContent className="p-4 bg-muted/30 rounded-lg space-y-2">
                                <div className="grid grid-cols-3 gap-4 text-sm">
                                  <div>
                                    <span className="font-semibold">Batch ID:</span>
                                    <p className="font-mono text-xs mt-1">{batch.id}</p>
                                  </div>
                                  <div>
                                    <span className="font-semibold">Correlation ID:</span>
                                    <p className="font-mono text-xs mt-1">{batch.correlationId}</p>
                                  </div>
                                  <div>
                                    <span className="font-semibold">Duration:</span>
                                    <p className="mt-1">
                                      {batch.durationMs ? `${batch.durationMs}ms` : 'Still running'}
                                    </p>
                                  </div>
                                  <div className="col-span-3">
                                    <span className="font-semibold">Related Operations:</span>
                                    <p className="mt-1 text-muted-foreground">
                                      Click on the correlation ID to view related operations in the Operations Timeline
                                    </p>
                                    <a
                                      href={`/operations?correlationId=${encodeURIComponent(batch.correlationId)}`}
                                      className="text-primary hover:underline mt-2 inline-block"
                                    >
                                      View {batch.itemsTotal} operations →
                                    </a>
                                  </div>
                                </div>
                              </CollapsibleContent>
                            </TableCell>
                          </TableRow>
                        )}
                      </>
                    </Collapsible>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Pagination Controls */}
          {data && data.totalPages > 1 && (
            <div className="flex justify-between items-center mt-4">
              <div className="text-sm text-muted-foreground">
                Page {page + 1} of {data.totalPages}
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))}
                  disabled={page >= data.totalPages - 1}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
