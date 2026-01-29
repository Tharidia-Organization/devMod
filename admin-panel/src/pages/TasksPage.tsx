import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { TestTask, ListResponse, TaskStatus } from '../types';
import { useToast } from '../context/ToastContext';
import { getApiErrorMessage } from '../utils/apiErrors';
import { format } from 'date-fns';
import {
  ClipboardList,
  Plus,
  Trash2,
  Edit,
  AlertCircle,
  X,
  ChevronDown,
  Clock,
  CheckCircle2,
  PlayCircle,
} from 'lucide-react';
import clsx from 'clsx';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const PRIORITY_MIN = 1;
const PRIORITY_MAX = 5;

interface TaskFormData {
  title: string;
  description: string;
  assignedTo: string;
  assignedByName: string;
  priority: number;
  status?: TaskStatus;
  notes?: string;
  dueAtMillis?: number;
}

const STATUS_LABELS: Record<TaskStatus, string> = {
  pending: 'Pending',
  in_progress: 'In Progress',
  completed: 'Completed',
};

export default function TasksPage() {
  const [showModal, setShowModal] = useState(false);
  const [editingTask, setEditingTask] = useState<TestTask | null>(null);
  const [statusFilter, setStatusFilter] = useState<'all' | TaskStatus>('all');
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});
  const queryClient = useQueryClient();
  const { pushToast } = useToast();

  const { data, isLoading, error, refetch } = useQuery<ListResponse<TestTask>>({
    queryKey: ['tasks'],
    queryFn: () => api.getTasks(),
  });

  const createMutation = useMutation({
    mutationFn: (data: Partial<TaskFormData>) => api.createTask({
      title: data.title!,
      description: data.description,
      assignedTo: data.assignedTo!,
      assignedByName: data.assignedByName,
      priority: Number(data.priority) || 1,
      dueAtMillis: data.dueAtMillis,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      closeModal();
      pushToast({ type: 'success', title: 'Task created' });
    },
    onError: (err, variables) => {
      pushToast({
        type: 'error',
        title: 'Create failed',
        message: getApiErrorMessage(err, 'Failed to create task.'),
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

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<TaskFormData> }) => api.updateTask(id, {
      title: data.title,
      description: data.description,
      priority: data.priority !== undefined ? Number(data.priority) : undefined,
      status: data.status,
      dueAtMillis: data.dueAtMillis,
      notes: data.notes,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      closeModal();
      pushToast({ type: 'success', title: 'Task updated' });
    },
    onError: (err, variables) => {
      pushToast({
        type: 'error',
        title: 'Update failed',
        message: getApiErrorMessage(err, 'Failed to update task.'),
        durationMs: 0,
        action: variables
          ? {
              label: 'Retry',
              onClick: () => updateMutation.mutate(variables),
            }
          : undefined,
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteTask(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      pushToast({ type: 'success', title: 'Task deleted' });
    },
    onError: (err, id) => {
      pushToast({
        type: 'error',
        title: 'Delete failed',
        message: getApiErrorMessage(err, 'Failed to delete task.'),
        durationMs: 0,
        action: {
          label: 'Retry',
          onClick: () => deleteMutation.mutate(id),
        },
      });
    },
  });

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const title = (formData.get('title') as string || '').trim();
    const assignedTo = (formData.get('assignedTo') as string || '').trim();
    const assignedByName = (formData.get('assignedByName') as string || '').trim() || 'Admin';
    const priorityValue = Number(formData.get('priority'));
    const dueAtRaw = (formData.get('dueAt') as string || '').trim();

    const nextErrors: Record<string, string> = {};
    if (!title) {
      nextErrors.title = 'Title is required';
    }
    if (!assignedTo) {
      nextErrors.assignedTo = 'Assigned To is required';
    } else if (!UUID_REGEX.test(assignedTo)) {
      nextErrors.assignedTo = 'Enter a valid UUID';
    }
    if (!Number.isFinite(priorityValue) || !Number.isInteger(priorityValue)) {
      nextErrors.priority = 'Must be an integer';
    } else if (priorityValue < PRIORITY_MIN || priorityValue > PRIORITY_MAX) {
      nextErrors.priority = `Must be between ${PRIORITY_MIN} and ${PRIORITY_MAX}`;
    }

    let dueAtMillis: number | undefined;
    if (dueAtRaw) {
      const parsed = new Date(`${dueAtRaw}T00:00:00`);
      if (Number.isNaN(parsed.getTime())) {
        nextErrors.dueAt = 'Enter a valid date';
      } else {
        dueAtMillis = parsed.getTime();
      }
    }

    if (Object.keys(nextErrors).length > 0) {
      setFormErrors(nextErrors);
      return;
    }

    setFormErrors({});
    const data: Partial<TaskFormData> = {
      title,
      description: formData.get('description') as string,
      assignedTo,
      assignedByName,
      priority: priorityValue,
      status: (formData.get('status') as TaskStatus) || undefined,
      notes: (formData.get('notes') as string) || undefined,
      dueAtMillis,
    };

    if (editingTask) {
      const payload = { id: editingTask.id, data };
      updateMutation.mutate(payload);
    } else {
      createMutation.mutate(data);
    }
  };

  const handleFieldChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const field = event.currentTarget.name;
    if (createMutation.isError) {
      createMutation.reset();
    }
    if (updateMutation.isError) {
      updateMutation.reset();
    }
    if (formErrors[field]) {
      setFormErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  };

  const inputClass = (field: string) =>
    `w-full px-4 py-2 border rounded-lg focus:ring-2 ${
      formErrors[field]
        ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
        : 'border-gray-300 focus:ring-primary-500 focus:border-primary-500'
    }`;

  const helperText = (field: string, text?: string) =>
    formErrors[field] ? (
      <p className="text-xs text-red-600 mt-1">{formErrors[field]}</p>
    ) : text ? (
      <p className="text-sm text-gray-500 mt-1">{text}</p>
    ) : null;

  const hasErrors = Object.keys(formErrors).length > 0;

  const getStatusIcon = (status: TaskStatus) => {
    switch (status) {
      case 'pending': return <Clock className="h-4 w-4" />;
      case 'in_progress': return <PlayCircle className="h-4 w-4" />;
      case 'completed': return <CheckCircle2 className="h-4 w-4" />;
      default: return null;
    }
  };

  const getStatusColor = (status: TaskStatus) => {
    switch (status) {
      case 'pending': return 'bg-yellow-100 text-yellow-700';
      case 'in_progress': return 'bg-blue-100 text-blue-700';
      case 'completed': return 'bg-green-100 text-green-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  const getPriorityColor = (priority: number) => {
    if (priority >= 3) return 'text-red-600';
    if (priority >= 2) return 'text-orange-600';
    return 'text-gray-600';
  };

  const filteredTasks = data?.items.filter((task) =>
    statusFilter === 'all' || task.status === statusFilter
  );

  const openEditModal = (task: TestTask) => {
    setEditingTask(task);
    setShowModal(true);
    setFormErrors({});
    createMutation.reset();
    updateMutation.reset();
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingTask(null);
    setFormErrors({});
    createMutation.reset();
    updateMutation.reset();
  };

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
            <h3 className="text-lg font-medium text-red-900">Failed to load tasks</h3>
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
          <h1 className="text-2xl font-bold text-gray-900">Tester Tasks</h1>
          <p className="text-gray-500 mt-1">{data?.count ?? 0} tasks total</p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors"
        >
          <Plus className="h-5 w-5" />
          New Task
        </button>
      </div>

      {/* Status Filter */}
      <div className="flex gap-2">
        {(['all', 'pending', 'in_progress', 'completed'] as const).map((status) => (
          <button
            key={status}
            onClick={() => setStatusFilter(status)}
            className={clsx(
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              statusFilter === status
                ? 'bg-primary-600 text-white'
                : 'bg-white border border-gray-200 text-gray-600 hover:bg-gray-50'
            )}
          >
            {status === 'all' ? 'All' : STATUS_LABELS[status]}
          </button>
        ))}
      </div>

      {/* Task List */}
      <div className="space-y-4">
        {filteredTasks?.map((task) => (
          <div
            key={task.id}
            className="bg-white rounded-xl shadow-sm border border-gray-200 p-6"
          >
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-3">
                  <ClipboardList className="h-5 w-5 text-gray-400" />
                  <h3 className="text-lg font-semibold text-gray-900">{task.title}</h3>
                  <span className={clsx(
                    'flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium',
                    getStatusColor(task.status)
                  )}>
                    {getStatusIcon(task.status)}
                    {STATUS_LABELS[task.status] ?? task.status}
                  </span>
                  <span className={clsx('text-sm font-medium', getPriorityColor(task.priority))}>
                    Priority {task.priority}
                  </span>
                  {task.overdue && (
                    <span className="px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-700">
                      Overdue
                    </span>
                  )}
                </div>
                {task.description && (
                  <p className="text-gray-600 mt-2">{task.description}</p>
                )}
                <div className="flex items-center gap-4 mt-3 text-sm text-gray-500">
                  <span>Assigned to: <span className="font-mono">{task.assignedTo.substring(0, 8)}...</span></span>
                  <span>By: {task.assignedByName || 'Admin'}</span>
                  <span>Created: {format(new Date(task.createdAtMillis), 'MMM d, yyyy')}</span>
                  {task.dueAtMillis && (
                    <span className="text-orange-600">Due: {format(new Date(task.dueAtMillis), 'MMM d, yyyy')}</span>
                  )}
                </div>
                {task.notes && (
                  <div className="mt-3 p-3 bg-gray-50 rounded-lg">
                    <p className="text-sm text-gray-600">{task.notes}</p>
                  </div>
                )}
              </div>
              <div className="flex items-center gap-2 ml-4">
                <button
                  onClick={() => openEditModal(task)}
                  className="p-2 text-gray-400 hover:text-primary-600 rounded-lg hover:bg-primary-50 transition-colors"
                >
                  <Edit className="h-5 w-5" />
                </button>
                <button
                  onClick={() => {
                    if (confirm('Delete this task?')) {
                      deleteMutation.mutate(task.id);
                    }
                  }}
                  className="p-2 text-gray-400 hover:text-red-600 rounded-lg hover:bg-red-50 transition-colors"
                >
                  <Trash2 className="h-5 w-5" />
                </button>
              </div>
            </div>
          </div>
        ))}

        {filteredTasks?.length === 0 && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-8 text-center">
            <ClipboardList className="h-12 w-12 text-gray-300 mx-auto mb-4" />
            <p className="text-gray-500">No tasks found</p>
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-auto">
            <div className="flex items-center justify-between p-6 border-b">
              <h2 className="text-xl font-semibold text-gray-900">
                {editingTask ? 'Edit Task' : 'New Task'}
              </h2>
              <button onClick={closeModal} className="p-2 hover:bg-gray-100 rounded-lg">
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              {hasErrors && (
                <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
                  Please fix the highlighted fields before saving.
                </div>
              )}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Title</label>
                <input
                  name="title"
                  required
                  defaultValue={editingTask?.title}
                  onChange={handleFieldChange}
                  className={inputClass('title')}
                />
                {helperText('title')}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
                <textarea
                  name="description"
                  rows={3}
                  defaultValue={editingTask?.description || ''}
                  onChange={handleFieldChange}
                  className={inputClass('description')}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Assigned To (UUID)</label>
                <input
                  name="assignedTo"
                  required
                  defaultValue={editingTask?.assignedTo}
                  disabled={!!editingTask}
                  onChange={handleFieldChange}
                  className={`${inputClass('assignedTo')} disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed`}
                  placeholder="e.g. 123e4567-e89b-12d3-a456-426614174000"
                />
                {helperText(
                  'assignedTo',
                  editingTask ? 'Reassigning requires a new task' : 'Must be a valid UUID'
                )}
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Assigned By</label>
                  <input
                    name="assignedByName"
                    defaultValue={editingTask?.assignedByName || 'Admin'}
                    onChange={handleFieldChange}
                    className={inputClass('assignedByName')}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
                  <input
                    name="priority"
                    type="number"
                    min={PRIORITY_MIN}
                    max={PRIORITY_MAX}
                    defaultValue={editingTask?.priority || 1}
                    onChange={handleFieldChange}
                    className={inputClass('priority')}
                  />
                  {helperText('priority', `Range ${PRIORITY_MIN} to ${PRIORITY_MAX}`)}
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Due Date</label>
                <input
                  name="dueAt"
                  type="date"
                  defaultValue={editingTask?.dueAtMillis ? format(new Date(editingTask.dueAtMillis), 'yyyy-MM-dd') : ''}
                  onChange={handleFieldChange}
                  className={inputClass('dueAt')}
                />
                {helperText('dueAt', 'Optional deadline for the task')}
              </div>
              {editingTask && (
                <>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                    <div className="relative">
                      <select
                        name="status"
                        defaultValue={editingTask.status}
                        onChange={handleFieldChange}
                        className={inputClass('status') + ' appearance-none'}
                      >
                        <option value="pending">Pending</option>
                        <option value="in_progress">In Progress</option>
                        <option value="completed">Completed</option>
                      </select>
                      <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400 pointer-events-none" />
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                    <textarea
                      name="notes"
                      rows={3}
                      defaultValue={editingTask.notes || ''}
                      onChange={handleFieldChange}
                      className={inputClass('notes')}
                    />
                  </div>
                </>
              )}
              <div className="flex justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={closeModal}
                  className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors disabled:opacity-50"
                >
                  {createMutation.isPending || updateMutation.isPending
                    ? 'Saving...'
                    : editingTask ? 'Update Task' : 'Create Task'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
