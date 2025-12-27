import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { AdminAuditEntry, ListResponse } from '../types';
import { useToast } from '../context/ToastContext';
import { getApiErrorMessage } from '../utils/apiErrors';
import { format } from 'date-fns';
import { AlertCircle, RefreshCw, ScrollText } from 'lucide-react';

const ACTION_OPTIONS = [
  'ALL',
  'BROADCAST',
  'MESSAGE_SEND',
  'MESSAGE_FLAG',
  'MESSAGE_DELETE',
  'MESSAGE_FORCE_DELETE',
  'NEWS_CREATE',
  'NEWS_UPDATE',
  'NEWS_DELETE',
  'TASK_CREATE',
  'TASK_ASSIGN',
  'TASK_UPDATE',
  'TASK_DELETE',
  'CONFIG_CHANGE',
  'USER_BLOCK',
  'USER_UNBLOCK',
  'USER_ACCESS_UPDATE',
  'LOGIN',
  'LOGOUT',
];

const LIMIT_OPTIONS = [50, 100, 200];

export default function AuditPage() {
  const { pushToast } = useToast();
  const [actionFilter, setActionFilter] = useState('ALL');
  const [actorFilter, setActorFilter] = useState('');
  const [limit, setLimit] = useState(100);

  const queryParams = useMemo(() => ({
    limit,
    action: actionFilter !== 'ALL' ? actionFilter : undefined,
    actor: actorFilter.trim() ? actorFilter.trim() : undefined,
  }), [limit, actionFilter, actorFilter]);

  const {
    data,
    isLoading,
    error,
    refetch,
  } = useQuery<ListResponse<AdminAuditEntry>>({
    queryKey: ['audit', queryParams],
    queryFn: () => api.getAuditEntries(queryParams),
  });

  const entries = data?.items ?? [];

  const handleRefresh = async () => {
    try {
      await refetch();
      pushToast({ type: 'success', title: 'Audit log refreshed' });
    } catch (err) {
      pushToast({
        type: 'error',
        title: 'Refresh failed',
        message: getApiErrorMessage(err, 'Failed to refresh audit log.'),
      });
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-3">
            <ScrollText className="h-6 w-6 text-primary-600" />
            <h1 className="text-2xl font-bold text-gray-900">Admin Audit Log</h1>
          </div>
          <p className="text-gray-500 mt-1">Track admin actions and system changes</p>
        </div>
        <button
          onClick={handleRefresh}
          className="flex items-center gap-2 px-4 py-2 bg-white border border-gray-200 rounded-lg hover:bg-gray-50"
        >
          <RefreshCw className="h-4 w-4" />
          Refresh
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Action</label>
            <select
              value={actionFilter}
              onChange={(e) => setActionFilter(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
            >
              {ACTION_OPTIONS.map((action) => (
                <option key={action} value={action}>{action}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Actor (name or UUID)</label>
            <input
              value={actorFilter}
              onChange={(e) => setActorFilter(e.target.value)}
              placeholder="admin"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Limit</label>
            <select
              value={limit}
              onChange={(e) => setLimit(Number(e.target.value))}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
            >
              {LIMIT_OPTIONS.map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <div className="flex items-center gap-2 text-red-700">
            <AlertCircle className="h-5 w-5" />
            <span>{getApiErrorMessage(error, 'Failed to load audit log.')}</span>
          </div>
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Time</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Action</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Actor</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Target</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {isLoading ? (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-gray-500">
                    Loading audit entries...
                  </td>
                </tr>
              ) : entries.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-gray-500">
                    No audit entries found.
                  </td>
                </tr>
              ) : (
                entries.map((entry) => (
                  <tr key={entry.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">
                      {format(new Date(entry.timestamp), 'yyyy-MM-dd HH:mm:ss')}
                    </td>
                    <td className="px-4 py-3 text-sm font-medium text-gray-900 whitespace-nowrap">
                      {entry.action}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700">
                      {entry.actorName}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700">
                      {entry.targetType}{entry.targetId ? `: ${entry.targetId}` : ''}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {entry.details ?? '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
