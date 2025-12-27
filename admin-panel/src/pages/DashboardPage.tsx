import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { Stats } from '../types';
import { getApiErrorMessage } from '../utils/apiErrors';
import { Mail, Newspaper, Users, ClipboardList, AlertCircle } from 'lucide-react';
import clsx from 'clsx';

interface StatCardProps {
  title: string;
  value: number;
  subtitle?: string;
  icon: React.ElementType;
  color: 'blue' | 'green' | 'purple' | 'orange';
}

function StatCard({ title, value, subtitle, icon: Icon, color }: StatCardProps) {
  const colorClasses = {
    blue: 'bg-blue-50 text-blue-600',
    green: 'bg-green-50 text-green-600',
    purple: 'bg-purple-50 text-purple-600',
    orange: 'bg-orange-50 text-orange-600',
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-gray-500">{title}</p>
          <p className="text-3xl font-bold text-gray-900 mt-1">{value.toLocaleString()}</p>
          {subtitle && <p className="text-sm text-gray-500 mt-1">{subtitle}</p>}
        </div>
        <div className={clsx('p-3 rounded-lg', colorClasses[color])}>
          <Icon className="h-6 w-6" />
        </div>
      </div>
    </div>
  );
}

function formatBytes(bytes: number) {
  if (!Number.isFinite(bytes)) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(value >= 10 || unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export default function DashboardPage() {
  const { data: stats, isLoading, error, refetch } = useQuery<Stats>({
    queryKey: ['stats'],
    queryFn: () => api.getStats(),
  });

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
          <AlertCircle className="h-6 w-6 text-red-500 flex-shrink-0" />
          <div>
            <h3 className="text-lg font-medium text-red-900">Failed to load statistics</h3>
            <p className="text-red-700 mt-1">
              {getApiErrorMessage(error, 'Please check your connection and try again.')}
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => void refetch()}
          className="px-3 py-2 text-sm text-red-700 border border-red-200 rounded-lg hover:bg-red-100"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 mt-1">Overview of your mailbox system</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title="Total Messages"
          value={stats?.totalMessages ?? 0}
          subtitle={`${stats?.totalUnreadMessages ?? 0} unread`}
          icon={Mail}
          color="blue"
        />
        <StatCard
          title="News Articles"
          value={stats?.activeNewsArticles ?? 0}
          subtitle="Active"
          icon={Newspaper}
          color="green"
        />
        <StatCard
          title="Known Users"
          value={stats?.totalUsers ?? 0}
          icon={Users}
          color="purple"
        />
        <StatCard
          title="Tasks"
          value={stats?.totalTasks ?? 0}
          subtitle={`${stats?.pendingTasks ?? 0} pending, ${stats?.inProgressTasks ?? 0} active`}
          icon={ClipboardList}
          color="orange"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
          <div className="space-y-3">
            <a
              href="/messages"
              className="block p-4 rounded-lg border border-gray-200 hover:border-primary-300 hover:bg-primary-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <Mail className="h-5 w-5 text-primary-600" />
                <div>
                  <p className="font-medium text-gray-900">Send Broadcast</p>
                  <p className="text-sm text-gray-500">Message all known players</p>
                </div>
              </div>
            </a>
            <a
              href="/news"
              className="block p-4 rounded-lg border border-gray-200 hover:border-primary-300 hover:bg-primary-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <Newspaper className="h-5 w-5 text-primary-600" />
                <div>
                  <p className="font-medium text-gray-900">Create News Article</p>
                  <p className="text-sm text-gray-500">Announce updates or events</p>
                </div>
              </div>
            </a>
            <a
              href="/tasks"
              className="block p-4 rounded-lg border border-gray-200 hover:border-primary-300 hover:bg-primary-50 transition-colors"
            >
              <div className="flex items-center gap-3">
                <ClipboardList className="h-5 w-5 text-primary-600" />
                <div>
                  <p className="font-medium text-gray-900">Assign Tester Task</p>
                  <p className="text-sm text-gray-500">Create QA tasks for testers</p>
                </div>
              </div>
            </a>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">System Status</h2>
          <div className="space-y-4">
            <div
              className={clsx(
                'flex items-center justify-between p-3 rounded-lg',
                stats?.apiServerRunning ? 'bg-green-50' : 'bg-red-50'
              )}
            >
              <span className={clsx('font-medium', stats?.apiServerRunning ? 'text-green-700' : 'text-red-700')}>
                API Server
              </span>
              <span
                className={clsx(
                  'flex items-center gap-2',
                  stats?.apiServerRunning ? 'text-green-600' : 'text-red-600'
                )}
              >
                <span
                  className={clsx(
                    'h-2 w-2 rounded-full',
                    stats?.apiServerRunning ? 'bg-green-500' : 'bg-red-500'
                  )}
                />
                {stats?.apiServerRunning ? 'Online' : 'Offline'}
              </span>
            </div>
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <span className="text-gray-700 font-medium">Memory</span>
              <span className="text-gray-600">
                {formatBytes(stats?.freeMemoryBytes ?? 0)} free / {formatBytes(stats?.totalMemoryBytes ?? 0)} total
              </span>
            </div>
            <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
              <span className="text-gray-700 font-medium">Task Status</span>
              <span className="text-gray-600">
                {stats?.inProgressTasks ?? 0} in progress · {stats?.completedTasks ?? 0} done
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
