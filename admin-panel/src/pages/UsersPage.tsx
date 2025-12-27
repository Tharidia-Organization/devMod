import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import { User, UserDetails, Message, ListResponse } from '../types';
import { getApiErrorMessage } from '../utils/apiErrors';
import { format } from 'date-fns';
import {
  Users,
  Mail,
  AlertCircle,
  X,
  Search,
  Inbox,
  Package,
} from 'lucide-react';
import clsx from 'clsx';

export default function UsersPage() {
  const [selectedUser, setSelectedUser] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const { data: users, isLoading, error, refetch } = useQuery<ListResponse<User>>({
    queryKey: ['users'],
    queryFn: () => api.getUsers(),
  });

  const {
    data: userDetails,
    error: userDetailsError,
    refetch: refetchUserDetails,
  } = useQuery<UserDetails>({
    queryKey: ['user', selectedUser],
    queryFn: () => api.getUser(selectedUser!),
    enabled: !!selectedUser,
  });

  const accessMutation = useMutation({
    mutationFn: (data: { uuid: string; payload: Record<string, boolean> }) =>
      api.updateUserAccess(data.uuid, data.payload),
    onSuccess: () => {
      void refetchUserDetails();
      void refetch();
    },
  });

  const {
    data: userInbox,
    error: userInboxError,
    refetch: refetchUserInbox,
  } = useQuery<ListResponse<Message>>({
    queryKey: ['userInbox', selectedUser],
    queryFn: () => api.getUserInbox(selectedUser!, 50, true),
    enabled: !!selectedUser,
  });

  const filteredUsers = users?.items.filter((user) =>
    user.uuid.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (user.name?.toLowerCase().includes(searchQuery.toLowerCase()) ?? false)
  );

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
            <h3 className="text-lg font-medium text-red-900">Failed to load users</h3>
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
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Users</h1>
          <p className="text-gray-500 mt-1">{users?.count ?? 0} known users</p>
        </div>
      </div>

      <div className="relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
        <input
          type="text"
          placeholder="Search by UUID or name..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-12 pr-4 py-3 border border-gray-300 rounded-xl focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* User List */}
        <div className="lg:col-span-2">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <div className="max-h-[600px] overflow-auto">
              {filteredUsers?.map((user) => (
                <div
                  key={user.uuid}
                  onClick={() => setSelectedUser(user.uuid)}
                  className={clsx(
                    'flex items-center gap-4 p-4 border-b border-gray-100 cursor-pointer transition-colors',
                    selectedUser === user.uuid
                      ? 'bg-primary-50'
                      : 'hover:bg-gray-50'
                  )}
                >
                  <div className="p-2 bg-gray-100 rounded-lg">
                    <Users className="h-5 w-5 text-gray-600" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-gray-900 truncate">
                      {user.name || 'Unknown Player'}
                    </p>
                    <p className="text-sm text-gray-500 font-mono truncate">{user.uuid}</p>
                  </div>
                </div>
              ))}
              {filteredUsers?.length === 0 && (
                <div className="p-8 text-center text-gray-500">
                  No users found
                </div>
              )}
            </div>
          </div>
        </div>

        {/* User Details */}
        <div className="lg:col-span-1">
          {selectedUser ? (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-6">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-gray-900">User Details</h2>
                <button
                  onClick={() => setSelectedUser(null)}
                  className="p-2 hover:bg-gray-100 rounded-lg"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {(userDetailsError || userInboxError) && (
                <div className="space-y-2">
                  {userDetailsError && (
                    <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700 flex items-center justify-between gap-3">
                      <span>{getApiErrorMessage(userDetailsError, 'Failed to load user details.')}</span>
                      <button
                        type="button"
                        onClick={() => void refetchUserDetails()}
                        className="px-2 py-1 border border-red-200 rounded-md hover:bg-red-100"
                      >
                        Retry
                      </button>
                    </div>
                  )}
                  {userInboxError && (
                    <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700 flex items-center justify-between gap-3">
                      <span>{getApiErrorMessage(userInboxError, 'Failed to load inbox.')}</span>
                      <button
                        type="button"
                        onClick={() => void refetchUserInbox()}
                        className="px-2 py-1 border border-red-200 rounded-md hover:bg-red-100"
                      >
                        Retry
                      </button>
                    </div>
                  )}
                </div>
              )}

              {userDetails ? (
                <>
                  <div className="space-y-4">
                    <div>
                      <p className="text-sm text-gray-500">UUID</p>
                      <p className="font-mono text-sm text-gray-900 break-all">{userDetails.uuid}</p>
                    </div>
                    <div>
                      <p className="text-sm text-gray-500">Name</p>
                      <p className="text-gray-900">{userDetails.name || 'Unknown'}</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-3 gap-4">
                    <div className="text-center p-4 bg-gray-50 rounded-lg">
                      <Mail className="h-5 w-5 text-gray-400 mx-auto mb-1" />
                      <p className="text-2xl font-bold text-gray-900">{userDetails.totalMessages}</p>
                      <p className="text-xs text-gray-500">Messages</p>
                    </div>
                    <div className="text-center p-4 bg-yellow-50 rounded-lg">
                      <Inbox className="h-5 w-5 text-yellow-500 mx-auto mb-1" />
                      <p className="text-2xl font-bold text-gray-900">{userDetails.unreadMessages}</p>
                      <p className="text-xs text-gray-500">Unread</p>
                    </div>
                    <div className="text-center p-4 bg-green-50 rounded-lg">
                      <Package className="h-5 w-5 text-green-500 mx-auto mb-1" />
                      <p className="text-2xl font-bold text-gray-900">{userDetails.unclaimedAttachments}</p>
                      <p className="text-xs text-gray-500">Unclaimed</p>
                    </div>
                  </div>

                  <div className="space-y-3">
                    <h3 className="text-sm font-medium text-gray-700">Access</h3>
                    <div className="space-y-2">
                      <label className="flex items-center justify-between text-sm text-gray-700">
                        <span>Admin</span>
                        <input
                          type="checkbox"
                          checked={userDetails.isAdmin}
                          disabled={accessMutation.isPending}
                          onChange={(e) =>
                            accessMutation.mutate({
                              uuid: userDetails.uuid,
                              payload: { admin: e.target.checked },
                            })
                          }
                          className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                        />
                      </label>
                      <label className="flex items-center justify-between text-sm text-gray-700">
                        <span>Tester</span>
                        <input
                          type="checkbox"
                          checked={userDetails.isTester}
                          disabled={accessMutation.isPending}
                          onChange={(e) =>
                            accessMutation.mutate({
                              uuid: userDetails.uuid,
                              payload: { tester: e.target.checked },
                            })
                          }
                          className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                        />
                      </label>
                      <label className="flex items-center justify-between text-sm text-gray-700">
                        <span>Block sending</span>
                        <input
                          type="checkbox"
                          checked={userDetails.blockedSender}
                          disabled={accessMutation.isPending}
                          onChange={(e) =>
                            accessMutation.mutate({
                              uuid: userDetails.uuid,
                              payload: { blockedSender: e.target.checked },
                            })
                          }
                          className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                        />
                      </label>
                      <label className="flex items-center justify-between text-sm text-gray-700">
                        <span>Block receiving</span>
                        <input
                          type="checkbox"
                          checked={userDetails.blockedReceiver}
                          disabled={accessMutation.isPending}
                          onChange={(e) =>
                            accessMutation.mutate({
                              uuid: userDetails.uuid,
                              payload: { blockedReceiver: e.target.checked },
                            })
                          }
                          className="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                        />
                      </label>
                    </div>
                    {accessMutation.isError && (
                      <p className="text-xs text-red-600">
                        {getApiErrorMessage(accessMutation.error, 'Failed to update access.')}
                      </p>
                    )}
                  </div>
                </>
              ) : (
                <p className="text-sm text-gray-500">Loading user details…</p>
              )}

              {userInbox && userInbox.items.length > 0 && (
                <div>
                  <h3 className="text-sm font-medium text-gray-700 mb-3">Recent Messages</h3>
                  <div className="space-y-2 max-h-64 overflow-auto">
                    {userInbox.items.slice(0, 5).map((msg) => (
                      <div
                        key={msg.id}
                        className="p-3 bg-gray-50 rounded-lg"
                      >
                        <p className="font-medium text-gray-900 text-sm truncate">{msg.subject}</p>
                        <p className="text-xs text-gray-500 mt-1">
                          {format(new Date(msg.createdAtMillis), 'MMM d, HH:mm')}
                          {!msg.readAtMillis && (
                            <span className="ml-2 text-yellow-600">Unread</span>
                          )}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-8 text-center">
              <Users className="h-12 w-12 text-gray-300 mx-auto mb-4" />
              <p className="text-gray-500">Select a user to view details</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
