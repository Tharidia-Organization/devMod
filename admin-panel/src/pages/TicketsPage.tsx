import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import {
  Ticket,
  AlertCircle,
  CheckCircle,
  Clock,
  User,
  MessageSquare,
  Filter,
  RefreshCw,
  ChevronDown,
  Send,
  X,
} from 'lucide-react';
import clsx from 'clsx';
import { api } from '../api/client';

// Types
interface TicketDto {
  id: string;
  reporterUuid: string;
  reporterName: string | null;
  reportedUuid: string | null;
  reportedName: string | null;
  category: string;
  categoryDisplay: string;
  priority: string;
  priorityDisplay: string;
  priorityColor: string;
  status: string;
  statusDisplay: string;
  subject: string;
  description: string | null;
  assignedTo: string | null;
  assignedToName: string | null;
  createdAtMillis: number;
  updatedAtMillis: number | null;
  resolvedAtMillis: number | null;
  resolutionNotes: string | null;
  ageMillis: number;
}

interface CommentDto {
  id: string;
  ticketId: string;
  authorUuid: string | null;
  authorName: string | null;
  content: string;
  isInternal: boolean;
  createdAtMillis: number;
}

interface TicketListResponse {
  tickets: TicketDto[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

interface TicketDetailResponse {
  ticket: TicketDto;
  comments: CommentDto[];
}

interface TicketStatsResponse {
  total: number;
  open: number;
  assigned: number;
  inProgress: number;
  resolved: number;
  closed: number;
  avgResolutionTimeMs: number;
}

// Status colors
const statusColors: Record<string, string> = {
  OPEN: 'bg-blue-100 text-blue-800',
  ASSIGNED: 'bg-cyan-100 text-cyan-800',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-800',
  RESOLVED: 'bg-green-100 text-green-800',
  CLOSED: 'bg-gray-100 text-gray-800',
};

const priorityColors: Record<string, string> = {
  LOW: 'text-gray-500',
  NORMAL: 'text-blue-600',
  HIGH: 'text-orange-500',
  URGENT: 'text-red-600',
};

export default function TicketsPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [page, setPage] = useState(0);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [newComment, setNewComment] = useState('');
  const [isInternalComment, setIsInternalComment] = useState(false);

  // Fetch tickets list
  const { data: ticketsData, isLoading, refetch } = useQuery({
    queryKey: ['tickets', statusFilter, categoryFilter, page],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (statusFilter) params.append('status', statusFilter);
      if (categoryFilter) params.append('category', categoryFilter);
      params.append('page', page.toString());
      params.append('pageSize', '20');
      const res = await api.get<TicketListResponse>(`/api/tickets?${params}`);
      return res.data;
    },
  });

  // Fetch ticket stats
  const { data: stats } = useQuery({
    queryKey: ['ticketStats'],
    queryFn: async () => {
      const res = await api.get<TicketStatsResponse>('/api/tickets/stats');
      return res.data;
    },
  });

  // Fetch selected ticket details
  const { data: ticketDetail } = useQuery({
    queryKey: ['ticket', selectedTicketId],
    queryFn: async () => {
      if (!selectedTicketId) return null;
      const res = await api.get<TicketDetailResponse>(`/api/tickets/${selectedTicketId}`);
      return res.data;
    },
    enabled: !!selectedTicketId,
  });

  // Update ticket mutation
  const updateTicketMutation = useMutation({
    mutationFn: async ({ id, data }: { id: string; data: { status?: string; priority?: string; resolutionNotes?: string } }) => {
      const res = await api.put(`/api/tickets/${id}`, data);
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tickets'] });
      queryClient.invalidateQueries({ queryKey: ['ticket', selectedTicketId] });
      queryClient.invalidateQueries({ queryKey: ['ticketStats'] });
    },
  });

  // Add comment mutation
  const addCommentMutation = useMutation({
    mutationFn: async ({ ticketId, content, isInternal }: { ticketId: string; content: string; isInternal: boolean }) => {
      const res = await api.post(`/api/tickets/${ticketId}/comments`, {
        authorUuid: '00000000-0000-0000-0000-000000000000', // Admin placeholder
        authorName: 'Admin',
        content,
        isInternal,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticket', selectedTicketId] });
      setNewComment('');
    },
  });

  const handleStatusChange = (ticketId: string, newStatus: string) => {
    if (newStatus === 'RESOLVED') {
      const notes = prompt('Resolution notes:');
      if (notes) {
        updateTicketMutation.mutate({ id: ticketId, data: { status: newStatus, resolutionNotes: notes } });
      }
    } else {
      updateTicketMutation.mutate({ id: ticketId, data: { status: newStatus } });
    }
  };

  const handleAddComment = () => {
    if (!selectedTicketId || !newComment.trim()) return;
    addCommentMutation.mutate({
      ticketId: selectedTicketId,
      content: newComment,
      isInternal: isInternalComment,
    });
  };

  const formatAge = (ms: number) => {
    const hours = Math.floor(ms / (1000 * 60 * 60));
    if (hours < 24) return `${hours}h`;
    const days = Math.floor(hours / 24);
    return `${days}d`;
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Support Tickets</h1>
          <p className="text-sm text-gray-500">Manage player support requests</p>
        </div>
        <button
          onClick={() => refetch()}
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh
        </button>
      </div>

      {/* Stats Cards */}
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-6 gap-4">
          <StatCard label="Total" value={stats.total} icon={Ticket} color="gray" />
          <StatCard label="Open" value={stats.open} icon={AlertCircle} color="blue" />
          <StatCard label="Assigned" value={stats.assigned} icon={User} color="cyan" />
          <StatCard label="In Progress" value={stats.inProgress} icon={Clock} color="yellow" />
          <StatCard label="Resolved" value={stats.resolved} icon={CheckCircle} color="green" />
          <StatCard label="Closed" value={stats.closed} icon={X} color="gray" />
        </div>
      )}

      {/* Filters */}
      <div className="flex gap-4 items-center bg-white p-4 rounded-lg shadow-sm">
        <Filter className="w-5 h-5 text-gray-400" />
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          className="border rounded-lg px-3 py-2 text-sm"
          aria-label="Filter by status"
        >
          <option value="">All Status</option>
          <option value="OPEN">Open</option>
          <option value="ASSIGNED">Assigned</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="RESOLVED">Resolved</option>
          <option value="CLOSED">Closed</option>
        </select>
        <select
          value={categoryFilter}
          onChange={(e) => { setCategoryFilter(e.target.value); setPage(0); }}
          className="border rounded-lg px-3 py-2 text-sm"
          aria-label="Filter by category"
        >
          <option value="">All Categories</option>
          <option value="BUG">Bug</option>
          <option value="ABUSE">Abuse Report</option>
          <option value="SUGGESTION">Suggestion</option>
          <option value="QUESTION">Question</option>
          <option value="OTHER">Other</option>
        </select>
      </div>

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Ticket List */}
        <div className="lg:col-span-2 bg-white rounded-lg shadow-sm overflow-hidden">
          {isLoading ? (
            <div className="p-8 text-center text-gray-500">Loading tickets...</div>
          ) : ticketsData?.tickets.length === 0 ? (
            <div className="p-8 text-center text-gray-500">No tickets found</div>
          ) : (
            <div className="divide-y">
              {ticketsData?.tickets.map((ticket) => (
                <div
                  key={ticket.id}
                  onClick={() => setSelectedTicketId(ticket.id)}
                  className={clsx(
                    'p-4 cursor-pointer hover:bg-gray-50 transition-colors',
                    selectedTicketId === ticket.id && 'bg-primary-50 border-l-4 border-primary-600'
                  )}
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className={clsx('px-2 py-0.5 text-xs font-medium rounded-full', statusColors[ticket.status])}>
                          {ticket.statusDisplay}
                        </span>
                        <span className={clsx('text-xs font-medium', priorityColors[ticket.priority])}>
                          {ticket.priorityDisplay}
                        </span>
                        <span className="text-xs text-gray-400">
                          {ticket.categoryDisplay}
                        </span>
                      </div>
                      <h3 className="font-medium text-gray-900 truncate">{ticket.subject}</h3>
                      <p className="text-sm text-gray-500 mt-1">
                        {ticket.reporterName || 'Unknown'} • {format(ticket.createdAtMillis, 'MMM d, HH:mm')}
                        <span className="text-gray-400 ml-2">({formatAge(ticket.ageMillis)} ago)</span>
                      </p>
                    </div>
                    {ticket.assignedToName && (
                      <div className="flex items-center gap-1 text-xs text-gray-500">
                        <User className="w-3 h-3" />
                        {ticket.assignedToName}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Pagination */}
          {ticketsData && ticketsData.totalPages > 1 && (
            <div className="flex items-center justify-between p-4 border-t">
              <span className="text-sm text-gray-500">
                Page {page + 1} of {ticketsData.totalPages} ({ticketsData.totalItems} total)
              </span>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-3 py-1 border rounded disabled:opacity-50"
                >
                  Previous
                </button>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page >= ticketsData.totalPages - 1}
                  className="px-3 py-1 border rounded disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Ticket Detail Panel */}
        <div className="bg-white rounded-lg shadow-sm overflow-hidden">
          {!selectedTicketId ? (
            <div className="p-8 text-center text-gray-500">
              <Ticket className="w-12 h-12 mx-auto mb-3 text-gray-300" />
              <p>Select a ticket to view details</p>
            </div>
          ) : ticketDetail ? (
            <div className="flex flex-col h-full">
              {/* Ticket Header */}
              <div className="p-4 border-b">
                <div className="flex items-center gap-2 mb-2">
                  <span className={clsx('px-2 py-0.5 text-xs font-medium rounded-full', statusColors[ticketDetail.ticket.status])}>
                    {ticketDetail.ticket.statusDisplay}
                  </span>
                  <span className={clsx('text-xs font-medium', priorityColors[ticketDetail.ticket.priority])}>
                    {ticketDetail.ticket.priorityDisplay}
                  </span>
                </div>
                <h2 className="font-semibold text-gray-900">{ticketDetail.ticket.subject}</h2>
                <p className="text-sm text-gray-500 mt-1">
                  From: {ticketDetail.ticket.reporterName || 'Unknown'}
                </p>
                <p className="text-xs text-gray-400">
                  {format(ticketDetail.ticket.createdAtMillis, 'PPpp')}
                </p>
              </div>

              {/* Description */}
              {ticketDetail.ticket.description && (
                <div className="p-4 border-b bg-gray-50">
                  <p className="text-sm text-gray-700 whitespace-pre-wrap">
                    {ticketDetail.ticket.description}
                  </p>
                </div>
              )}

              {/* Actions */}
              <div className="p-4 border-b">
                <label htmlFor="ticket-status-select" className="block text-xs font-medium text-gray-500 mb-1">Change Status</label>
                <select
                  id="ticket-status-select"
                  value={ticketDetail.ticket.status}
                  onChange={(e) => handleStatusChange(ticketDetail.ticket.id, e.target.value)}
                  className="w-full border rounded-lg px-3 py-2 text-sm"
                >
                  <option value="OPEN">Open</option>
                  <option value="ASSIGNED">Assigned</option>
                  <option value="IN_PROGRESS">In Progress</option>
                  <option value="RESOLVED">Resolved</option>
                  <option value="CLOSED">Closed</option>
                </select>
              </div>

              {/* Comments */}
              <div className="flex-1 overflow-y-auto p-4">
                <h3 className="text-sm font-medium text-gray-700 mb-3 flex items-center gap-2">
                  <MessageSquare className="w-4 h-4" />
                  Comments ({ticketDetail.comments.length})
                </h3>
                <div className="space-y-3">
                  {ticketDetail.comments.map((comment) => (
                    <div
                      key={comment.id}
                      className={clsx(
                        'p-3 rounded-lg text-sm',
                        comment.isInternal ? 'bg-yellow-50 border border-yellow-200' : 'bg-gray-50'
                      )}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-medium text-gray-900">
                          {comment.authorName || 'System'}
                          {comment.isInternal && (
                            <span className="ml-2 text-xs text-yellow-600">(Internal)</span>
                          )}
                        </span>
                        <span className="text-xs text-gray-400">
                          {format(comment.createdAtMillis, 'MMM d, HH:mm')}
                        </span>
                      </div>
                      <p className="text-gray-700 whitespace-pre-wrap">{comment.content}</p>
                    </div>
                  ))}
                </div>
              </div>

              {/* Add Comment */}
              <div className="p-4 border-t bg-gray-50">
                <textarea
                  value={newComment}
                  onChange={(e) => setNewComment(e.target.value)}
                  placeholder="Add a comment..."
                  className="w-full border rounded-lg px-3 py-2 text-sm resize-none"
                  rows={2}
                />
                <div className="flex items-center justify-between mt-2">
                  <label className="flex items-center gap-2 text-sm text-gray-600">
                    <input
                      type="checkbox"
                      checked={isInternalComment}
                      onChange={(e) => setIsInternalComment(e.target.checked)}
                      className="rounded"
                    />
                    Internal note
                  </label>
                  <button
                    onClick={handleAddComment}
                    disabled={!newComment.trim() || addCommentMutation.isPending}
                    className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
                  >
                    <Send className="w-4 h-4" />
                    Send
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <div className="p-8 text-center text-gray-500">Loading...</div>
          )}
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }: { label: string; value: number; icon: any; color: string }) {
  const colorClasses: Record<string, string> = {
    gray: 'bg-gray-100 text-gray-600',
    blue: 'bg-blue-100 text-blue-600',
    cyan: 'bg-cyan-100 text-cyan-600',
    yellow: 'bg-yellow-100 text-yellow-600',
    green: 'bg-green-100 text-green-600',
  };

  return (
    <div className="bg-white rounded-lg p-4 shadow-sm">
      <div className="flex items-center gap-3">
        <div className={clsx('p-2 rounded-lg', colorClasses[color])}>
          <Icon className="w-5 h-5" />
        </div>
        <div>
          <p className="text-2xl font-bold text-gray-900">{value}</p>
          <p className="text-xs text-gray-500">{label}</p>
        </div>
      </div>
    </div>
  );
}
