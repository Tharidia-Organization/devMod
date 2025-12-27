import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { Message, ListResponse, Config, Stats, BroadcastJob } from '../types';
import { useToast } from '../context/ToastContext';
import { getApiErrorMessage } from '../utils/apiErrors';
import { format } from 'date-fns';
import {
  Mail,
  Send,
  Trash2,
  AlertCircle,
  X,
  Megaphone,
  ChevronDown,
  RefreshCw,
} from 'lucide-react';
import clsx from 'clsx';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

interface MessageFormData {
  recipientUuid: string;
  senderName: string;
  subject: string;
  body?: string;
  messageType: string;
  attachmentData?: string;
  expiresAtMillis?: number;
}

interface BroadcastFormData {
  senderName: string;
  subject: string;
  body?: string;
  messageType: string;
  attachmentData?: string;
  expiresAtMillis?: number;
}

export default function MessagesPage() {
  const [showNewModal, setShowNewModal] = useState(false);
  const [showBroadcastModal, setShowBroadcastModal] = useState(false);
  const [selectedMessage, setSelectedMessage] = useState<Message | null>(null);
  const [createErrors, setCreateErrors] = useState<Record<string, string>>({});
  const [broadcastErrors, setBroadcastErrors] = useState<Record<string, string>>({});
  const queryClient = useQueryClient();
  const { pushToast } = useToast();

  const { data: config } = useQuery<Config>({
    queryKey: ['config'],
    queryFn: () => api.getConfig(),
    staleTime: 60_000,
  });

  const broadcastQueueEnabled = config?.broadcastQueueEnabled ?? false;

  const { data: stats } = useQuery<Stats>({
    queryKey: ['stats'],
    queryFn: () => api.getStats(),
    staleTime: 60_000,
  });

  const { data, isLoading, error, refetch } = useQuery<ListResponse<Message>>({
    queryKey: ['messages'],
    queryFn: () => api.getMessages(100, 0),
  });

  const {
    data: activeJobsData,
    isLoading: activeJobsLoading,
    error: activeJobsError,
    refetch: refetchActiveJobs,
  } = useQuery<ListResponse<BroadcastJob>>({
    queryKey: ['broadcastJobs', 'active'],
    queryFn: () => api.getBroadcastJobsActive(),
    enabled: broadcastQueueEnabled,
    refetchInterval: broadcastQueueEnabled ? 5000 : false,
  });

  const {
    data: recentJobsData,
    isLoading: recentJobsLoading,
    error: recentJobsError,
    refetch: refetchRecentJobs,
  } = useQuery<ListResponse<BroadcastJob>>({
    queryKey: ['broadcastJobs', 'recent'],
    queryFn: () => api.getBroadcastJobsRecent(10),
    enabled: broadcastQueueEnabled,
  });

  const maxSubjectLength = config?.maxSubjectLength ?? 128;
  const maxBodyLength = config?.maxBodyLength ?? 2000;
  const broadcastBatchSize = config?.broadcastBatchSize ?? 500;
  const broadcastBatchDelayMs = config?.broadcastBatchDelayMs ?? 0;

  const closeNewModal = () => {
    setShowNewModal(false);
    setCreateErrors({});
    createMutation.reset();
  };

  const closeBroadcastModal = () => {
    setShowBroadcastModal(false);
    setBroadcastErrors({});
    broadcastMutation.reset();
  };

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteMessage(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['messages'] });
      setSelectedMessage(null);
      pushToast({ type: 'success', title: 'Message deleted' });
    },
    onError: (err, id) => {
      pushToast({
        type: 'error',
        title: 'Delete failed',
        message: getApiErrorMessage(err, 'Failed to delete message.'),
        durationMs: 0,
        action: {
          label: 'Retry',
          onClick: () => deleteMutation.mutate(id),
        },
      });
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: MessageFormData) => api.createMessage(data),
    onSuccess: (response: { message?: string } | undefined) => {
      queryClient.invalidateQueries({ queryKey: ['messages'] });
      closeNewModal();
      pushToast({
        type: 'success',
        title: 'Message sent',
        message: response?.message,
      });
    },
    onError: (err, variables) => {
      pushToast({
        type: 'error',
        title: 'Send failed',
        message: getApiErrorMessage(err, 'Failed to send message.'),
        durationMs: 0,
        action: variables
          ? {
              label: 'Retry',
              onClick: () => createMutation.mutate(variables),
            }
          : undefined,
      });
    },
  });

  const broadcastMutation = useMutation({
    mutationFn: (data: BroadcastFormData) => api.broadcastMessage(data),
    onSuccess: (response: { message?: string; queued?: boolean } | undefined) => {
      queryClient.invalidateQueries({ queryKey: ['messages'] });
      if (response?.queued) {
        queryClient.invalidateQueries({ queryKey: ['broadcastJobs'] });
      }
      closeBroadcastModal();
      pushToast({
        type: 'success',
        title: response?.queued ? 'Broadcast queued' : 'Broadcast sent',
        message: response?.message,
      });
    },
    onError: (err, variables) => {
      pushToast({
        type: 'error',
        title: 'Broadcast failed',
        message: getApiErrorMessage(err, 'Failed to send broadcast.'),
        durationMs: 0,
        action: variables
          ? {
              label: 'Retry',
              onClick: () => broadcastMutation.mutate(variables),
            }
          : undefined,
      });
    },
  });

  const handleCreateSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const recipientUuid = (formData.get('recipientUuid') as string || '').trim();
    const senderName = (formData.get('senderName') as string || '').trim() || 'Admin';
    const subject = (formData.get('subject') as string || '').trim();
    const body = (formData.get('body') as string || '') as string;
    const messageType = (formData.get('messageType') as string || 'ADMIN');
    const attachmentData = (formData.get('attachmentData') as string || '').trim();
    const expiresAtRaw = (formData.get('expiresAt') as string || '').trim();
    const expiresAtMillis = expiresAtRaw ? new Date(expiresAtRaw).getTime() : undefined;

    const nextErrors: Record<string, string> = {};
    if (!recipientUuid) {
      nextErrors.recipientUuid = 'Recipient UUID is required';
    } else if (!UUID_REGEX.test(recipientUuid)) {
      nextErrors.recipientUuid = 'Enter a valid UUID';
    }
    if (!subject) {
      nextErrors.subject = 'Subject is required';
    } else if (subject.length > maxSubjectLength) {
      nextErrors.subject = `Max ${maxSubjectLength} characters`;
    }
    if (body.length > maxBodyLength) {
      nextErrors.body = `Max ${maxBodyLength} characters`;
    }
    if (attachmentData) {
      try {
        JSON.parse(attachmentData);
      } catch {
        nextErrors.attachmentData = 'Attachment must be valid JSON';
      }
    }
    if (expiresAtRaw && Number.isNaN(expiresAtMillis)) {
      nextErrors.expiresAt = 'Invalid expiry date';
    }

    if (Object.keys(nextErrors).length > 0) {
      setCreateErrors(nextErrors);
      return;
    }

    setCreateErrors({});
    const payload = {
      recipientUuid,
      senderName,
      subject,
      body: body || undefined,
      messageType,
      attachmentData: attachmentData || undefined,
      expiresAtMillis,
    };
    createMutation.mutate(payload);
  };

  const handleBroadcastSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const senderName = (formData.get('senderName') as string || '').trim() || 'Admin';
    const subject = (formData.get('subject') as string || '').trim();
    const body = (formData.get('body') as string || '') as string;
    const messageType = (formData.get('messageType') as string || 'ADMIN');
    const attachmentData = (formData.get('attachmentData') as string || '').trim();
    const expiresAtRaw = (formData.get('expiresAt') as string || '').trim();
    const expiresAtMillis = expiresAtRaw ? new Date(expiresAtRaw).getTime() : undefined;

    const nextErrors: Record<string, string> = {};
    if (!subject) {
      nextErrors.subject = 'Subject is required';
    } else if (subject.length > maxSubjectLength) {
      nextErrors.subject = `Max ${maxSubjectLength} characters`;
    }
    if (body.length > maxBodyLength) {
      nextErrors.body = `Max ${maxBodyLength} characters`;
    }
    if (attachmentData) {
      try {
        JSON.parse(attachmentData);
      } catch {
        nextErrors.attachmentData = 'Attachment must be valid JSON';
      }
    }
    if (expiresAtRaw && Number.isNaN(expiresAtMillis)) {
      nextErrors.expiresAt = 'Invalid expiry date';
    }

    if (Object.keys(nextErrors).length > 0) {
      setBroadcastErrors(nextErrors);
      return;
    }

    setBroadcastErrors({});
    const payload = {
      senderName,
      subject,
      body: body || undefined,
      messageType,
      attachmentData: attachmentData || undefined,
      expiresAtMillis,
    };
    broadcastMutation.mutate(payload);
  };

  const handleCreateFieldChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const field = event.currentTarget.name;
    if (createMutation.isError) {
      createMutation.reset();
    }
    if (createErrors[field]) {
      setCreateErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  };

  const handleBroadcastFieldChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const field = event.currentTarget.name;
    if (broadcastMutation.isError) {
      broadcastMutation.reset();
    }
    if (broadcastErrors[field]) {
      setBroadcastErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  };

  const handleRefreshJobs = async () => {
    if (!broadcastQueueEnabled) return;
    try {
      await Promise.all([refetchActiveJobs(), refetchRecentJobs()]);
      pushToast({ type: 'success', title: 'Broadcast queue refreshed' });
    } catch (err) {
      pushToast({
        type: 'error',
        title: 'Refresh failed',
        message: getApiErrorMessage(err, 'Failed to refresh broadcast queue.'),
      });
    }
  };

  const inputClass = (errors: Record<string, string>, field: string) =>
    `w-full px-4 py-2 border rounded-lg focus:ring-2 ${
      errors[field]
        ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
        : 'border-gray-300 focus:ring-primary-500 focus:border-primary-500'
    }`;

  const helperText = (errors: Record<string, string>, field: string, text: string) =>
    errors[field] ? (
      <p className="text-xs text-red-600 mt-1">{errors[field]}</p>
    ) : (
      <p className="text-sm text-gray-500 mt-1">{text}</p>
    );

  const hasCreateErrors = Object.keys(createErrors).length > 0;
  const hasBroadcastErrors = Object.keys(broadcastErrors).length > 0;
  const activeJobs = activeJobsData?.items ?? [];
  const recentJobs = recentJobsData?.items ?? [];
  const jobsLoading = activeJobsLoading || recentJobsLoading;
  const jobsError = activeJobsError || recentJobsError;

  const getMessageTypeColor = (type: string) => {
    switch (type) {
      case 'SYSTEM': return 'bg-blue-100 text-blue-700';
      case 'ADMIN': return 'bg-purple-100 text-purple-700';
      case 'REWARD': return 'bg-green-100 text-green-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  const getJobStateColor = (state: string) => {
    switch (state) {
      case 'PROCESSING': return 'bg-blue-100 text-blue-700';
      case 'RETRYING': return 'bg-yellow-100 text-yellow-700';
      case 'COMPLETED': return 'bg-green-100 text-green-700';
      case 'FAILED': return 'bg-red-100 text-red-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  const formatJobTime = (millis: number | null) =>
    millis ? format(new Date(millis), 'MMM d, HH:mm') : '—';

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-6 flex items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <AlertCircle className="h-6 w-6 text-red-500" />
          <div>
            <h3 className="text-lg font-medium text-red-900">Failed to load messages</h3>
            <p className="text-red-700 mt-1">
              {getApiErrorMessage(error, 'Please check your connection and try again.')}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void refetch()}
          className="flex items-center gap-2 px-3 py-2 text-sm text-red-700 border border-red-200 rounded-lg hover:bg-red-100"
        >
          <RefreshCw className="h-4 w-4" />
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Messages</h1>
          <p className="text-gray-500 mt-1">{data?.count ?? 0} messages total</p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={() => setShowBroadcastModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-orange-600 hover:bg-orange-700 text-white rounded-lg transition-colors"
          >
            <Megaphone className="h-5 w-5" />
            Broadcast
          </button>
          <button
            onClick={() => setShowNewModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors"
          >
            <Send className="h-5 w-5" />
            New Message
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <table className="w-full">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Subject</th>
              <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Recipient</th>
              <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Type</th>
              <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Sent</th>
              <th className="text-left px-6 py-3 text-sm font-medium text-gray-500">Status</th>
              <th className="w-20"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {data?.items.map((message) => (
              <tr
                key={message.id}
                className="hover:bg-gray-50 cursor-pointer"
                onClick={() => setSelectedMessage(message)}
              >
                <td className="px-6 py-4">
                  <div className="flex items-center gap-3">
                    <Mail className="h-5 w-5 text-gray-400" />
                    <span className="font-medium text-gray-900">{message.subject}</span>
                  </div>
                </td>
                <td className="px-6 py-4 text-sm text-gray-600">
                  {message.recipientUuid.substring(0, 8)}...
                </td>
                <td className="px-6 py-4">
                  <span className={clsx('px-2 py-1 rounded-full text-xs font-medium', getMessageTypeColor(message.messageType))}>
                    {message.messageType}
                  </span>
                </td>
                <td className="px-6 py-4 text-sm text-gray-600">
                  {format(new Date(message.createdAtMillis), 'MMM d, HH:mm')}
                </td>
                <td className="px-6 py-4">
                  <span className={clsx(
                    'px-2 py-1 rounded-full text-xs font-medium',
                    message.readAtMillis ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'
                  )}>
                    {message.readAtMillis ? 'Read' : 'Unread'}
                  </span>
                </td>
                <td className="px-6 py-4">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      if (confirm('Delete this message?')) {
                        deleteMutation.mutate(message.id);
                      }
                    }}
                    className="p-2 text-gray-400 hover:text-red-600 rounded-lg hover:bg-red-50 transition-colors"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Broadcast Queue</h2>
            <p className="text-sm text-gray-500">Track queued broadcast jobs</p>
          </div>
          <button
            onClick={handleRefreshJobs}
            disabled={!broadcastQueueEnabled}
            className={clsx(
              'flex items-center gap-2 px-3 py-2 text-sm rounded-lg border',
              broadcastQueueEnabled
                ? 'border-gray-200 hover:bg-gray-50'
                : 'border-gray-100 text-gray-400 cursor-not-allowed'
            )}
          >
            <RefreshCw className="h-4 w-4" />
            Refresh
          </button>
        </div>

        {!broadcastQueueEnabled ? (
          <div className="text-sm text-gray-500">
            Broadcast queueing is disabled. Enable it in Configuration to see job status.
          </div>
        ) : jobsError ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <div className="flex items-center gap-2 text-red-700">
              <AlertCircle className="h-5 w-5" />
              <span>{getApiErrorMessage(jobsError, 'Failed to load broadcast queue.')}</span>
            </div>
          </div>
        ) : jobsLoading ? (
          <div className="text-sm text-gray-500">Loading broadcast queue...</div>
        ) : (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div>
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Active Jobs</h3>
              <div className="border border-gray-200 rounded-lg overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="text-left px-3 py-2 text-xs font-medium text-gray-500">Subject</th>
                      <th className="text-left px-3 py-2 text-xs font-medium text-gray-500">Progress</th>
                      <th className="text-left px-3 py-2 text-xs font-medium text-gray-500">State</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {activeJobs.length === 0 ? (
                      <tr>
                        <td colSpan={3} className="px-3 py-4 text-center text-gray-500">
                          No active broadcast jobs.
                        </td>
                      </tr>
                    ) : (
                      activeJobs.map((job) => (
                        <tr key={job.jobId} className="hover:bg-gray-50">
                          <td className="px-3 py-2 text-gray-700">{job.subject}</td>
                          <td className="px-3 py-2 text-gray-600">
                            {job.sentCount}/{job.totalRecipients}
                          </td>
                          <td className="px-3 py-2">
                            <span className={clsx(
                              'px-2 py-1 rounded-full text-xs font-medium',
                              getJobStateColor(job.state)
                            )}>
                              {job.state}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            <div>
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Recent Jobs</h3>
              <div className="border border-gray-200 rounded-lg overflow-hidden">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="text-left px-3 py-2 text-xs font-medium text-gray-500">Subject</th>
                      <th className="text-left px-3 py-2 text-xs font-medium text-gray-500">Completed</th>
                      <th className="text-left px-3 py-2 text-xs font-medium text-gray-500">State</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {recentJobs.length === 0 ? (
                      <tr>
                        <td colSpan={3} className="px-3 py-4 text-center text-gray-500">
                          No recent broadcast jobs.
                        </td>
                      </tr>
                    ) : (
                      recentJobs.map((job) => (
                        <tr key={job.jobId} className="hover:bg-gray-50">
                          <td className="px-3 py-2 text-gray-700">
                            <div className="flex flex-col">
                              <span>{job.subject}</span>
                              {job.errorMessage && (
                                <span className="text-xs text-red-600 mt-1">{job.errorMessage}</span>
                              )}
                            </div>
                          </td>
                          <td className="px-3 py-2 text-gray-600">{formatJobTime(job.completedAtMillis)}</td>
                          <td className="px-3 py-2">
                            <span className={clsx(
                              'px-2 py-1 rounded-full text-xs font-medium',
                              getJobStateColor(job.state)
                            )}>
                              {job.state}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Message Detail Modal */}
      {selectedMessage && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-2xl w-full max-h-[80vh] overflow-auto">
            <div className="flex items-center justify-between p-6 border-b">
              <h2 className="text-xl font-semibold text-gray-900">{selectedMessage.subject}</h2>
              <button onClick={() => setSelectedMessage(null)} className="p-2 hover:bg-gray-100 rounded-lg">
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-500">From:</span>
                  <span className="ml-2 text-gray-900">{selectedMessage.senderName || 'System'}</span>
                </div>
                <div>
                  <span className="text-gray-500">To:</span>
                  <span className="ml-2 text-gray-900 font-mono">{selectedMessage.recipientUuid}</span>
                </div>
                <div>
                  <span className="text-gray-500">Sent:</span>
                  <span className="ml-2 text-gray-900">{format(new Date(selectedMessage.createdAtMillis), 'PPpp')}</span>
                </div>
                <div>
                  <span className="text-gray-500">Type:</span>
                  <span className={clsx('ml-2 px-2 py-1 rounded-full text-xs font-medium', getMessageTypeColor(selectedMessage.messageType))}>
                    {selectedMessage.messageType}
                  </span>
                </div>
              </div>
              <div className="pt-4 border-t">
                <p className="text-gray-700 whitespace-pre-wrap">{selectedMessage.body || '(No content)'}</p>
              </div>
              {selectedMessage.hasAttachment && (
                <div className="pt-4 border-t">
                  <span className="text-sm text-gray-500">Attachment: </span>
                  <span className={clsx(
                    'text-sm font-medium',
                    selectedMessage.attachmentClaimed ? 'text-green-600' : 'text-orange-600'
                  )}>
                    {selectedMessage.attachmentClaimed ? 'Claimed' : 'Unclaimed'}
                  </span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* New Message Modal */}
      {showNewModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full">
            <div className="flex items-center justify-between p-6 border-b">
              <h2 className="text-xl font-semibold text-gray-900">New Message</h2>
              <button onClick={closeNewModal} className="p-2 hover:bg-gray-100 rounded-lg">
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleCreateSubmit} className="p-6 space-y-4">
              {hasCreateErrors && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
                  Please fix the highlighted fields before sending.
                </div>
              )}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Recipient UUID</label>
                <input
                  name="recipientUuid"
                  required
                  onChange={handleCreateFieldChange}
                  className={inputClass(createErrors, 'recipientUuid')}
                  placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
                />
                {helperText(createErrors, 'recipientUuid', 'Must be a valid UUID')}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Sender Name</label>
                <input
                  name="senderName"
                  defaultValue="Admin"
                  onChange={handleCreateFieldChange}
                  className={inputClass(createErrors, 'senderName')}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Subject</label>
                <input
                  name="subject"
                  required
                  maxLength={maxSubjectLength}
                  onChange={handleCreateFieldChange}
                  className={inputClass(createErrors, 'subject')}
                />
                {helperText(createErrors, 'subject', `Max ${maxSubjectLength} characters`)}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Body</label>
                <textarea
                  name="body"
                  rows={4}
                  maxLength={maxBodyLength}
                  onChange={handleCreateFieldChange}
                  className={inputClass(createErrors, 'body')}
                />
                {helperText(createErrors, 'body', `Max ${maxBodyLength} characters`)}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Attachment JSON (optional)</label>
                <textarea
                  name="attachmentData"
                  rows={3}
                  onChange={handleCreateFieldChange}
                  className={inputClass(createErrors, 'attachmentData')}
                  placeholder='e.g. [{"type":"currency","currency":"tokens","amount":100}]'
                />
                {helperText(createErrors, 'attachmentData')}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Expires At (optional)</label>
                <input
                  type="datetime-local"
                  name="expiresAt"
                  onChange={handleCreateFieldChange}
                  className={inputClass(createErrors, 'expiresAt')}
                />
                {helperText(createErrors, 'expiresAt')}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <div className="relative">
                  <select
                    name="messageType"
                    defaultValue="ADMIN"
                    onChange={handleCreateFieldChange}
                    className={inputClass(createErrors, 'messageType') + ' appearance-none'}
                  >
                    <option value="ADMIN">Admin</option>
                    <option value="SYSTEM">System</option>
                    <option value="REWARD">Reward</option>
                  </select>
                  <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400 pointer-events-none" />
                </div>
              </div>
              <div className="flex justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={closeNewModal}
                  className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors disabled:opacity-50"
                >
                  {createMutation.isPending ? 'Sending...' : 'Send Message'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Broadcast Modal */}
      {showBroadcastModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full">
            <div className="flex items-center justify-between p-6 border-b">
              <h2 className="text-xl font-semibold text-gray-900">Broadcast Message</h2>
              <button onClick={closeBroadcastModal} className="p-2 hover:bg-gray-100 rounded-lg">
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleBroadcastSubmit} className="p-6 space-y-4">
              {hasBroadcastErrors && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
                  Please fix the highlighted fields before broadcasting.
                </div>
              )}
              <div className="bg-orange-50 border border-orange-200 rounded-lg p-4 text-sm text-orange-700">
                This will send a message to all known players (online and offline).
              </div>
              {stats?.totalUsers != null && (
                <div className="rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-800">
                  <span className="font-medium">Estimated delay overhead:</span>{' '}
                  {stats.totalUsers === 0
                    ? 'No known recipients.'
                    : (() => {
                        const recipients = stats.totalUsers;
                        const batchSize = Math.max(1, broadcastBatchSize);
                        const delayMs = Math.max(0, broadcastBatchDelayMs);
                        const batches = Math.ceil(recipients / batchSize);
                        const delayTotalMs = Math.max(0, batches - 1) * delayMs;
                        const seconds = Math.round(delayTotalMs / 1000);
                        const hours = Math.floor(seconds / 3600);
                        const minutes = Math.floor((seconds % 3600) / 60);
                        const secs = seconds % 60;
                        const parts = [
                          hours > 0 ? `${hours}h` : null,
                          minutes > 0 || hours > 0 ? `${minutes}m` : null,
                          `${secs}s`,
                        ].filter(Boolean);
                        return `${parts.join(' ')} for ${recipients.toLocaleString()} users (${batches} batches).`;
                      })()}
                  <p className="text-xs text-blue-700 mt-1">
                    This only accounts for the configured delay between batches.
                  </p>
                </div>
              )}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Sender Name</label>
                <input
                  name="senderName"
                  defaultValue="Admin"
                  onChange={handleBroadcastFieldChange}
                  className={inputClass(broadcastErrors, 'senderName')}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Subject</label>
                <input
                  name="subject"
                  required
                  maxLength={maxSubjectLength}
                  onChange={handleBroadcastFieldChange}
                  className={inputClass(broadcastErrors, 'subject')}
                />
                {helperText(broadcastErrors, 'subject', `Max ${maxSubjectLength} characters`)}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Body</label>
                <textarea
                  name="body"
                  rows={4}
                  maxLength={maxBodyLength}
                  onChange={handleBroadcastFieldChange}
                  className={inputClass(broadcastErrors, 'body')}
                />
                {helperText(broadcastErrors, 'body', `Max ${maxBodyLength} characters`)}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Attachment JSON (optional)</label>
                <textarea
                  name="attachmentData"
                  rows={3}
                  onChange={handleBroadcastFieldChange}
                  className={inputClass(broadcastErrors, 'attachmentData')}
                  placeholder='e.g. [{"type":"currency","currency":"tokens","amount":100}]'
                />
                {helperText(broadcastErrors, 'attachmentData')}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Expires At (optional)</label>
                <input
                  type="datetime-local"
                  name="expiresAt"
                  onChange={handleBroadcastFieldChange}
                  className={inputClass(broadcastErrors, 'expiresAt')}
                />
                {helperText(broadcastErrors, 'expiresAt')}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <div className="relative">
                  <select
                    name="messageType"
                    defaultValue="ADMIN"
                    onChange={handleBroadcastFieldChange}
                    className={inputClass(broadcastErrors, 'messageType') + ' appearance-none'}
                  >
                    <option value="ADMIN">Admin</option>
                    <option value="SYSTEM">System</option>
                  </select>
                  <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400 pointer-events-none" />
                </div>
              </div>
              <div className="flex justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={closeBroadcastModal}
                  className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={broadcastMutation.isPending}
                  className="px-4 py-2 bg-orange-600 hover:bg-orange-700 text-white rounded-lg transition-colors disabled:opacity-50"
                >
                  {broadcastMutation.isPending ? 'Broadcasting...' : 'Send Broadcast'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
