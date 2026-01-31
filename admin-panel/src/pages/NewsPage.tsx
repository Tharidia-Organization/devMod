import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { NewsArticle, ListResponse, NewsCategory } from '../types';
import { useToast } from '../context/ToastContext';
import { getApiErrorMessage } from '../utils/apiErrors';
import { format } from 'date-fns';
import {
  Newspaper,
  Plus,
  Trash2,
  Edit,
  AlertCircle,
  X,
  ChevronDown,
  Eye,
  EyeOff,
} from 'lucide-react';
import clsx from 'clsx';

const MAX_TITLE_LENGTH = 256;
const MAX_AUTHOR_LENGTH = 64;
const MIN_PRIORITY = 0;
const MAX_PRIORITY = 100;

interface NewsFormData {
  title: string;
  content: string;
  category: NewsCategory;
  authorName: string;
  publishNow: boolean;
  publishAtMillis?: number;
  expiresAtMillis?: number;
  priority: number;
  active: boolean;
}

export default function NewsPage() {
  const [showModal, setShowModal] = useState(false);
  const [editingArticle, setEditingArticle] = useState<NewsArticle | null>(null);
  const [showInactive, setShowInactive] = useState(false);
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});
  const queryClient = useQueryClient();
  const { pushToast } = useToast();

  const { data, isLoading, error, refetch } = useQuery<ListResponse<NewsArticle>>({
    queryKey: ['news', showInactive],
    queryFn: () => api.getNews(!showInactive),
  });

  const createMutation = useMutation({
    mutationFn: (data: NewsFormData) => api.createNews({
      ...data,
      priority: Number(data.priority),
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['news'] });
      closeModal();
      pushToast({ type: 'success', title: 'Article created' });
    },
    onError: (err, variables) => {
      pushToast({
        type: 'error',
        title: 'Create failed',
        message: getApiErrorMessage(err, 'Failed to create article.'),
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
    mutationFn: ({ id, data }: { id: string; data: Partial<NewsFormData> }) => api.updateNews(id, {
      ...data,
      priority: data.priority !== undefined ? Number(data.priority) : undefined,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['news'] });
      closeModal();
      pushToast({ type: 'success', title: 'Article updated' });
    },
    onError: (err, variables) => {
      pushToast({
        type: 'error',
        title: 'Update failed',
        message: getApiErrorMessage(err, 'Failed to update article.'),
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
    mutationFn: (id: string) => api.deleteNews(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['news'] });
      pushToast({ type: 'success', title: 'Article deleted' });
    },
    onError: (err, id) => {
      pushToast({
        type: 'error',
        title: 'Delete failed',
        message: getApiErrorMessage(err, 'Failed to delete article.'),
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
    const content = (formData.get('content') as string || '').trim();
    const authorName = (formData.get('authorName') as string || '').trim() || 'Admin';
    const priorityValue = Number(formData.get('priority'));
    const publishAtRaw = (formData.get('publishAt') as string || '').trim();
    const expiresAtRaw = (formData.get('expiresAt') as string || '').trim();
    const publishAtMillis = publishAtRaw ? new Date(publishAtRaw).getTime() : undefined;
    const expiresAtMillis = expiresAtRaw ? new Date(expiresAtRaw).getTime() : undefined;

    const nextErrors: Record<string, string> = {};
    if (!title) {
      nextErrors.title = 'Title is required';
    } else if (title.length > MAX_TITLE_LENGTH) {
      nextErrors.title = `Max ${MAX_TITLE_LENGTH} characters`;
    }
    if (!content) {
      nextErrors.content = 'Content is required';
    }
    if (authorName.length > MAX_AUTHOR_LENGTH) {
      nextErrors.authorName = `Max ${MAX_AUTHOR_LENGTH} characters`;
    }
    if (!Number.isFinite(priorityValue) || !Number.isInteger(priorityValue)) {
      nextErrors.priority = 'Must be an integer';
    } else if (priorityValue < MIN_PRIORITY || priorityValue > MAX_PRIORITY) {
      nextErrors.priority = `Must be between ${MIN_PRIORITY} and ${MAX_PRIORITY}`;
    }
    if (publishAtRaw && Number.isNaN(publishAtMillis)) {
      nextErrors.publishAt = 'Invalid publish date';
    }
    if (expiresAtRaw && Number.isNaN(expiresAtMillis)) {
      nextErrors.expiresAt = 'Invalid expiry date';
    }
    if (publishAtMillis && expiresAtMillis && expiresAtMillis <= publishAtMillis) {
      nextErrors.expiresAt = 'Expiry must be after publish time';
    }

    if (Object.keys(nextErrors).length > 0) {
      setFormErrors(nextErrors);
      return;
    }

    setFormErrors({});
    const data: NewsFormData = {
      title,
      content,
      category: formData.get('category') as NewsCategory,
      authorName,
      publishNow: publishAtMillis ? false : formData.get('publishNow') === 'on',
      publishAtMillis,
      expiresAtMillis,
      priority: priorityValue,
      active: formData.get('active') === 'on',
    };

    if (editingArticle) {
      const payload = { id: editingArticle.id, data };
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

  const getCategoryColor = (category: NewsCategory) => {
    switch (category) {
      case 'PATCH': return 'bg-blue-100 text-blue-700';
      case 'EVENT': return 'bg-purple-100 text-purple-700';
      case 'ANNOUNCEMENT': return 'bg-green-100 text-green-700';
      case 'MAINTENANCE': return 'bg-orange-100 text-orange-700';
      case 'DEV_BLOG': return 'bg-indigo-100 text-indigo-700';
      case 'COMMUNITY': return 'bg-pink-100 text-pink-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  const openEditModal = (article: NewsArticle) => {
    setEditingArticle(article);
    setShowModal(true);
    setFormErrors({});
    createMutation.reset();
    updateMutation.reset();
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingArticle(null);
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
            <h3 className="text-lg font-medium text-red-900">Failed to load news</h3>
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
          <h1 className="text-2xl font-bold text-gray-900">News Articles</h1>
          <p className="text-gray-500 mt-1">{data?.count ?? 0} articles</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowInactive(!showInactive)}
            className={clsx(
              'flex items-center gap-2 px-4 py-2 rounded-lg border transition-colors',
              showInactive
                ? 'bg-gray-100 border-gray-300 text-gray-700'
                : 'bg-white border-gray-200 text-gray-600 hover:bg-gray-50'
            )}
          >
            {showInactive ? <Eye className="h-5 w-5" /> : <EyeOff className="h-5 w-5" />}
            {showInactive ? 'Showing All' : 'Active Only'}
          </button>
          <button
            onClick={() => setShowModal(true)}
            className="flex items-center gap-2 px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors"
          >
            <Plus className="h-5 w-5" />
            New Article
          </button>
        </div>
      </div>

      <div className="grid gap-6">
        {data?.items.map((article) => (
          <div
            key={article.id}
            className={clsx(
              'bg-white rounded-xl shadow-sm border p-6',
              article.active ? 'border-gray-200' : 'border-gray-200 opacity-60'
            )}
          >
            <div className="flex items-start justify-between">
              <div className="flex items-start gap-4">
                <div className="p-3 bg-gray-100 rounded-lg">
                  <Newspaper className="h-6 w-6 text-gray-600" />
                </div>
                <div>
                  <div className="flex items-center gap-3">
                    <h3 className="text-lg font-semibold text-gray-900">{article.title}</h3>
                    <span className={clsx('px-2 py-1 rounded-full text-xs font-medium', getCategoryColor(article.category))}>
                      {article.category}
                    </span>
                    {!article.active && (
                      <span className="px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-600">
                        Inactive
                      </span>
                    )}
                    {article.expired && (
                      <span className="px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-600">
                        Expired
                      </span>
                    )}
                  </div>
                  <p className="text-gray-600 mt-2 line-clamp-2">{article.content}</p>
                  <div className="flex items-center gap-4 mt-3 text-sm text-gray-500">
                    <span>By {article.authorName}</span>
                    <span>Priority: {article.priority}</span>
                    {article.publishedAtMillis && (
                      <span>Published: {format(new Date(article.publishedAtMillis), 'MMM d, yyyy')}</span>
                    )}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => openEditModal(article)}
                  className="p-2 text-gray-400 hover:text-primary-600 rounded-lg hover:bg-primary-50 transition-colors"
                  aria-label="Edit article"
                >
                  <Edit className="h-5 w-5" />
                </button>
                <button
                  onClick={() => {
                    if (confirm('Delete this article?')) {
                      deleteMutation.mutate(article.id);
                    }
                  }}
                  className="p-2 text-gray-400 hover:text-red-600 rounded-lg hover:bg-red-50 transition-colors"
                  aria-label="Delete article"
                >
                  <Trash2 className="h-5 w-5" />
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-auto">
            <div className="flex items-center justify-between p-6 border-b">
              <h2 className="text-xl font-semibold text-gray-900">
                {editingArticle ? 'Edit Article' : 'New Article'}
              </h2>
              <button onClick={closeModal} className="p-2 hover:bg-gray-100 rounded-lg" aria-label="Close modal">
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
                <label htmlFor="title" className="block text-sm font-medium text-gray-700 mb-1">Title</label>
                <input
                  id="title"
                  name="title"
                  required
                  defaultValue={editingArticle?.title}
                  maxLength={MAX_TITLE_LENGTH}
                  onChange={handleFieldChange}
                  className={inputClass('title')}
                />
                {helperText('title', `Max ${MAX_TITLE_LENGTH} characters`)}
              </div>
              <div>
                <label htmlFor="content" className="block text-sm font-medium text-gray-700 mb-1">Content</label>
                <textarea
                  id="content"
                  name="content"
                  required
                  rows={6}
                  defaultValue={editingArticle?.content}
                  onChange={handleFieldChange}
                  className={inputClass('content')}
                />
                {helperText('content')}
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="category" className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                  <div className="relative">
                    <select
                      id="category"
                      name="category"
                      defaultValue={editingArticle?.category || 'ANNOUNCEMENT'}
                      onChange={handleFieldChange}
                      className={inputClass('category') + ' appearance-none'}
                    >
                      <option value="ANNOUNCEMENT">Announcement</option>
                      <option value="PATCH">Patch Notes</option>
                      <option value="EVENT">Event</option>
                      <option value="MAINTENANCE">Maintenance</option>
                      <option value="DEV_BLOG">Dev Blog</option>
                      <option value="COMMUNITY">Community</option>
                    </select>
                    <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400 pointer-events-none" />
                  </div>
                </div>
                <div>
                  <label htmlFor="authorName" className="block text-sm font-medium text-gray-700 mb-1">Author Name</label>
                  <input
                    id="authorName"
                    name="authorName"
                    defaultValue={editingArticle?.authorName || 'Admin'}
                    maxLength={MAX_AUTHOR_LENGTH}
                    onChange={handleFieldChange}
                    className={inputClass('authorName')}
                  />
                  {helperText('authorName', `Max ${MAX_AUTHOR_LENGTH} characters`)}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="priority" className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
                  <input
                    id="priority"
                    name="priority"
                    type="number"
                    defaultValue={editingArticle?.priority || 0}
                    min={MIN_PRIORITY}
                    max={MAX_PRIORITY}
                    onChange={handleFieldChange}
                    className={inputClass('priority')}
                  />
                  {helperText('priority', `Range ${MIN_PRIORITY} to ${MAX_PRIORITY}`)}
                </div>
                <div className="flex items-end gap-6 pb-2">
                  <label className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      name="publishNow"
                      defaultChecked={!editingArticle}
                      className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
                    />
                    <span className="text-sm text-gray-700">Publish Now</span>
                  </label>
                  <label className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      name="active"
                      defaultChecked={editingArticle?.active ?? true}
                      className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
                    />
                    <span className="text-sm text-gray-700">Active</span>
                  </label>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label htmlFor="publishAt" className="block text-sm font-medium text-gray-700 mb-1">Publish At</label>
                  <input
                    id="publishAt"
                    type="datetime-local"
                    name="publishAt"
                    defaultValue={
                      editingArticle?.publishedAtMillis
                        ? format(new Date(editingArticle.publishedAtMillis), "yyyy-MM-dd'T'HH:mm")
                        : ''
                    }
                    onChange={handleFieldChange}
                    className={inputClass('publishAt')}
                  />
                  {helperText('publishAt', 'Leave blank to publish now')}
                </div>
                <div>
                  <label htmlFor="expiresAt" className="block text-sm font-medium text-gray-700 mb-1">Expires At</label>
                  <input
                    id="expiresAt"
                    type="datetime-local"
                    name="expiresAt"
                    defaultValue={
                      editingArticle?.expiresAtMillis
                        ? format(new Date(editingArticle.expiresAtMillis), "yyyy-MM-dd'T'HH:mm")
                        : ''
                    }
                    onChange={handleFieldChange}
                    className={inputClass('expiresAt')}
                  />
                  {helperText('expiresAt')}
                </div>
              </div>
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
                    : editingArticle ? 'Update Article' : 'Create Article'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
