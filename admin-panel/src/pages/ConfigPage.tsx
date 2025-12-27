import { useEffect, useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { api } from '../api/client';
import { Config, Stats } from '../types';
import { useToast } from '../context/ToastContext';
import { getApiErrorMessage } from '../utils/apiErrors';
import {
  Settings,
  Save,
  AlertCircle,
  RefreshCw,
} from 'lucide-react';

export default function ConfigPage() {
  const [hasChanges, setHasChanges] = useState(false);
  const [formKey, setFormKey] = useState(0);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [broadcastPreview, setBroadcastPreview] = useState({ batchSize: 500, delayMs: 0 });
  const {
    pushToast,
    preferences: toastPreferences,
    updatePreferences: updateToastPreferences,
    resetPreferences: resetToastPreferences,
  } = useToast();
  const [toastForm, setToastForm] = useState({
    position: 'top-right' as 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left',
    successMs: 3500,
    infoMs: 4000,
    errorMs: 6000,
    maxVisible: 5,
    showSuccessToasts: true,
    showInfoToasts: true,
  });
  const [toastErrors, setToastErrors] = useState<Record<string, string>>({});

  const { data: config, isLoading, error, refetch } = useQuery<Config>({
    queryKey: ['config'],
    queryFn: () => api.getConfig(),
  });

  const { data: stats } = useQuery<Stats>({
    queryKey: ['stats'],
    queryFn: () => api.getStats(),
    staleTime: 60_000,
  });

  const updateMutation = useMutation({
    mutationFn: (data: Partial<Config>) => api.updateConfig(data),
    onSuccess: async () => {
      await refetch();
      setFormKey((prev) => prev + 1);
      setHasChanges(false);
      setErrors({});
      pushToast({ type: 'success', title: 'Configuration saved' });
    },
    onError: (err, variables) => {
      pushToast({
        type: 'error',
        title: 'Save failed',
        message: getApiErrorMessage(err, 'Failed to update configuration.'),
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

  useEffect(() => {
    if (!config) return;
    setBroadcastPreview({
      batchSize: config.broadcastBatchSize ?? 500,
      delayMs: config.broadcastBatchDelayMs ?? 0,
    });
  }, [config]);

  useEffect(() => {
    setToastForm({
      position: toastPreferences.position,
      successMs: toastPreferences.durations.success,
      infoMs: toastPreferences.durations.info,
      errorMs: toastPreferences.durations.error,
      maxVisible: toastPreferences.maxVisible,
      showSuccessToasts: toastPreferences.showSuccessToasts,
      showInfoToasts: toastPreferences.showInfoToasts,
    });
  }, [toastPreferences]);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!config) return;
    const formData = new FormData(e.currentTarget);

    const nextErrors: Record<string, string> = {};
    const readNumber = (name: string, min: number, max: number) => {
      const value = Number(formData.get(name));
      if (!Number.isFinite(value)) {
        nextErrors[name] = 'Required';
        return value;
      }
      if (!Number.isInteger(value)) {
        nextErrors[name] = 'Must be an integer';
      }
      if (value < min || value > max) {
        nextErrors[name] = `Must be between ${min} and ${max}`;
      }
      return value;
    };

    const readBoolean = (name: string) => formData.get(name) === 'on';
    const parseList = (value: FormDataEntryValue | null) => {
      if (typeof value !== 'string') return [];
      return value
        .split(/[\n,]/)
        .map((entry) => entry.trim())
        .filter(Boolean);
    };
    const parseAmountMap = (value: FormDataEntryValue | null) => {
      const result: Record<string, number> = {};
      if (typeof value !== 'string') return result;
      const lines = value.split(/[\n,]/).map((entry) => entry.trim()).filter(Boolean);
      for (const line of lines) {
        const parts = line.split(/[:=]/).map((entry) => entry.trim()).filter(Boolean);
        if (parts.length !== 2) {
          nextErrors.currencyAttachmentMaxAmounts = 'Use format currency=amount per line';
          continue;
        }
        const amount = Number(parts[1]);
        if (!Number.isFinite(amount) || amount <= 0) {
          nextErrors.currencyAttachmentMaxAmounts = 'Amounts must be positive numbers';
          continue;
        }
        result[parts[0].toLowerCase()] = Math.floor(amount);
      }
      return result;
    };

    const contentFilterAction = (formData.get('contentFilterAction') as string || 'BLOCK').toUpperCase();
    if (!['BLOCK', 'FLAG', 'CENSOR'].includes(contentFilterAction)) {
      nextErrors.contentFilterAction = 'Invalid action';
    }

    const payload: Partial<Config> = {
      enabled: readBoolean('enabled'),
      maintenanceMode: readBoolean('maintenanceMode'),
      useOpLevelForRoles: readBoolean('useOpLevelForRoles'),
      playerToPlayerEnabled: readBoolean('playerToPlayerEnabled'),
      itemAttachmentsEnabled: readBoolean('itemAttachmentsEnabled'),
      currencyAttachmentsEnabled: readBoolean('currencyAttachmentsEnabled'),
      hardDeleteOnUserDelete: readBoolean('hardDeleteOnUserDelete'),
      contentFilterEnabled: readBoolean('contentFilterEnabled'),
      broadcastQueueEnabled: readBoolean('broadcastQueueEnabled'),
      itemAttachmentWhitelistEnabled: readBoolean('itemAttachmentWhitelistEnabled'),
      maxMessagesPerPlayer: readNumber('maxMessagesPerPlayer', 10, 500),
      maxSubjectLength: readNumber('maxSubjectLength', 32, 256),
      maxBodyLength: readNumber('maxBodyLength', 100, 10000),
      defaultMessageTtlHours: readNumber('defaultMessageTtlHours', 24, 8760),
      maxMessagesPerMinute: readNumber('maxMessagesPerMinute', 1, 60),
      maxMessagesPerDay: readNumber('maxMessagesPerDay', 0, 10000),
      maxMessagesPerRecipientPerDay: readNumber('maxMessagesPerRecipientPerDay', 0, 10000),
      sendCooldownSeconds: readNumber('sendCooldownSeconds', 0, 300),
      broadcastBatchSize: readNumber('broadcastBatchSize', 1, 5000),
      broadcastBatchDelayMs: readNumber('broadcastBatchDelayMs', 0, 60000),
      broadcastQueueThreshold: readNumber('broadcastQueueThreshold', 1, 1_000_000),
      minLevelToSend: readNumber('minLevelToSend', 0, 1000),
      maxAttachmentsPerMessage: readNumber('maxAttachmentsPerMessage', 0, 10),
      messageRetentionDays: readNumber('messageRetentionDays', 1, 365),
      contentFilterAction,
      contentFilterWords: parseList(formData.get('contentFilterWords')),
      contentFilterPatterns: parseList(formData.get('contentFilterPatterns')),
      itemAttachmentWhitelist: parseList(formData.get('itemAttachmentWhitelist')),
      itemAttachmentBlacklist: parseList(formData.get('itemAttachmentBlacklist')),
      currencyAttachmentAllowed: parseList(formData.get('currencyAttachmentAllowed')),
      currencyAttachmentMaxAmounts: parseAmountMap(formData.get('currencyAttachmentMaxAmounts')),
    };

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }

    setErrors({});
    updateMutation.mutate(payload);
  };

  const handleFieldChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const target = event.currentTarget;
    const field = target.name;
    setHasChanges(true);
    if (updateMutation.isError) {
      updateMutation.reset();
    }
    if (errors[field]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
    if (field === 'broadcastBatchSize' || field === 'broadcastBatchDelayMs') {
      const value = Number(target.value);
      if (Number.isFinite(value)) {
        setBroadcastPreview((prev) => ({
          ...prev,
          [field === 'broadcastBatchSize' ? 'batchSize' : 'delayMs']: value,
        }));
      }
    }
  };

  const inputClass = (field: string) =>
    `w-full px-4 py-2 border rounded-lg focus:ring-2 ${
      errors[field]
        ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
        : 'border-gray-300 focus:ring-primary-500 focus:border-primary-500'
    }`;

  const helperText = (field: string, text: string) =>
    errors[field] ? (
      <p className="text-xs text-red-600 mt-1">{errors[field]}</p>
    ) : (
      <p className="text-sm text-gray-500 mt-1">{text}</p>
    );

  const hasErrors = Object.keys(errors).length > 0;
  const toastInputClass = (field: string) =>
    `w-full px-4 py-2 border rounded-lg focus:ring-2 ${
      toastErrors[field]
        ? 'border-red-300 focus:ring-red-500 focus:border-red-500'
        : 'border-gray-300 focus:ring-primary-500 focus:border-primary-500'
    }`;
  const toastHelperText = (field: string, text: string) =>
    toastErrors[field] ? (
      <p className="text-xs text-red-600 mt-1">{toastErrors[field]}</p>
    ) : (
      <p className="text-sm text-gray-500 mt-1">{text}</p>
    );

  const handleToastFieldChange = (
    event: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    const field = event.currentTarget.name;
    const target = event.currentTarget;
    const isCheckbox = target instanceof HTMLInputElement && target.type === 'checkbox';
    const value = isCheckbox ? target.checked : target.value;
    setToastForm((prev) => ({
      ...prev,
      [field]: field === 'position'
        ? value
        : isCheckbox
          ? value
          : Number(value),
    }));
    if (toastErrors[field]) {
      setToastErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  };

  const handleToastSave = () => {
    const nextErrors: Record<string, string> = {};
    const readDuration = (field: string) => {
      const value = Number(toastForm[field as keyof typeof toastForm]);
      if (!Number.isFinite(value) || !Number.isInteger(value)) {
        nextErrors[field] = 'Must be an integer';
        return value;
      }
      if (value < 0 || value > 60000) {
        nextErrors[field] = 'Must be between 0 and 60000';
      }
      return value;
    };

    const successMs = readDuration('successMs');
    const infoMs = readDuration('infoMs');
    const errorMs = readDuration('errorMs');
    const maxVisible = Number(toastForm.maxVisible);
    if (!Number.isFinite(maxVisible) || !Number.isInteger(maxVisible)) {
      nextErrors.maxVisible = 'Must be an integer';
    } else if (maxVisible < 1 || maxVisible > 10) {
      nextErrors.maxVisible = 'Must be between 1 and 10';
    }

    if (Object.keys(nextErrors).length > 0) {
      setToastErrors(nextErrors);
      return;
    }

    setToastErrors({});
    updateToastPreferences({
      position: toastForm.position as 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left',
      durations: {
        success: successMs,
        info: infoMs,
        error: errorMs,
      },
      maxVisible,
      showSuccessToasts: toastForm.showSuccessToasts,
      showInfoToasts: toastForm.showInfoToasts,
    });
    pushToast({ type: 'success', title: 'UI preferences saved' });
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
            <h3 className="text-lg font-medium text-red-900">Failed to load configuration</h3>
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
          <h1 className="text-2xl font-bold text-gray-900">Configuration</h1>
          <p className="text-gray-500 mt-1">Manage mailbox system settings</p>
        </div>
      </div>

      <form key={formKey} onSubmit={handleSubmit} className="space-y-6">
        {hasErrors && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-red-500 mt-0.5" />
            <div>
              <p className="text-sm font-medium text-red-900">Please fix the highlighted fields.</p>
              <p className="text-sm text-red-700 mt-1">Values must match the allowed ranges.</p>
            </div>
          </div>
        )}
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-primary-100 rounded-lg">
              <Settings className="h-5 w-5 text-primary-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900">Messaging</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Messages Per Player
              </label>
              <input
                name="maxMessagesPerPlayer"
                type="number"
                min="10"
                max="500"
                defaultValue={config?.maxMessagesPerPlayer ?? 100}
                onChange={handleFieldChange}
                className={inputClass('maxMessagesPerPlayer')}
              />
              {helperText(
                'maxMessagesPerPlayer',
                'Maximum number of messages a player can have in their inbox'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Subject Length
              </label>
              <input
                name="maxSubjectLength"
                type="number"
                min="32"
                max="256"
                defaultValue={config?.maxSubjectLength ?? 128}
                onChange={handleFieldChange}
                className={inputClass('maxSubjectLength')}
              />
              {helperText(
                'maxSubjectLength',
                'Maximum number of characters allowed in the subject'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Body Length
              </label>
              <input
                name="maxBodyLength"
                type="number"
                min="100"
                max="10000"
                defaultValue={config?.maxBodyLength ?? 2000}
                onChange={handleFieldChange}
                className={inputClass('maxBodyLength')}
              />
              {helperText(
                'maxBodyLength',
                'Maximum number of characters allowed in the body'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Default Message TTL (Hours)
              </label>
              <input
                name="defaultMessageTtlHours"
                type="number"
                min="24"
                max="8760"
                defaultValue={config?.defaultMessageTtlHours ?? 720}
                onChange={handleFieldChange}
                className={inputClass('defaultMessageTtlHours')}
              />
              {helperText(
                'defaultMessageTtlHours',
                'How long messages stay before expiring (24h = 1 day)'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Min Level to Send
              </label>
              <input
                name="minLevelToSend"
                type="number"
                min="0"
                max="1000"
                defaultValue={config?.minLevelToSend ?? 0}
                onChange={handleFieldChange}
                className={inputClass('minLevelToSend')}
              />
              {helperText(
                'minLevelToSend',
                'Minimum player level required to send messages'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Attachments Per Message
              </label>
              <input
                name="maxAttachmentsPerMessage"
                type="number"
                min="0"
                max="10"
                defaultValue={config?.maxAttachmentsPerMessage ?? 5}
                onChange={handleFieldChange}
                className={inputClass('maxAttachmentsPerMessage')}
              />
              {helperText(
                'maxAttachmentsPerMessage',
                'Maximum attachments allowed in a single message'
              )}
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-4">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="playerToPlayerEnabled"
                defaultChecked={config?.playerToPlayerEnabled ?? true}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Enable player-to-player messaging</span>
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="itemAttachmentsEnabled"
                defaultChecked={config?.itemAttachmentsEnabled ?? true}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Allow item attachments</span>
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="currencyAttachmentsEnabled"
                defaultChecked={config?.currencyAttachmentsEnabled ?? true}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Allow currency attachments</span>
            </label>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-primary-100 rounded-lg">
              <Settings className="h-5 w-5 text-primary-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900">Content Filter</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Filter Action
              </label>
              <select
                name="contentFilterAction"
                defaultValue={config?.contentFilterAction ?? 'BLOCK'}
                onChange={handleFieldChange}
                className={inputClass('contentFilterAction')}
              >
                <option value="BLOCK">Block</option>
                <option value="FLAG">Flag</option>
                <option value="CENSOR">Censor</option>
              </select>
              {helperText(
                'contentFilterAction',
                'Choose how to handle messages that match filtered content'
              )}
            </div>

            <div className="flex items-center">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  name="contentFilterEnabled"
                  defaultChecked={config?.contentFilterEnabled ?? true}
                  onChange={handleFieldChange}
                  className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
                />
                <span className="text-sm text-gray-700">Enable content filter</span>
              </label>
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Prohibited Words
              </label>
              <textarea
                name="contentFilterWords"
                rows={5}
                defaultValue={(config?.contentFilterWords ?? []).join('\n')}
                onChange={handleFieldChange}
                className={inputClass('contentFilterWords')}
                placeholder="word1\nword2\nword3"
              />
              {helperText(
                'contentFilterWords',
                'One word per line (or comma-separated). Case-insensitive.'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Prohibited Patterns (regex)
              </label>
              <textarea
                name="contentFilterPatterns"
                rows={5}
                defaultValue={(config?.contentFilterPatterns ?? []).join('\n')}
                onChange={handleFieldChange}
                className={inputClass('contentFilterPatterns')}
                placeholder="(?i)\\bexample\\b"
              />
              {helperText(
                'contentFilterPatterns',
                'Regex patterns applied to the full message content.'
              )}
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-primary-100 rounded-lg">
              <Settings className="h-5 w-5 text-primary-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900">Attachments & Economy</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Item Whitelist (IDs)
              </label>
              <textarea
                name="itemAttachmentWhitelist"
                rows={4}
                defaultValue={(config?.itemAttachmentWhitelist ?? []).join('\n')}
                onChange={handleFieldChange}
                className={inputClass('itemAttachmentWhitelist')}
                placeholder="minecraft:diamond\nmodid:custom_item"
              />
              {helperText(
                'itemAttachmentWhitelist',
                'Items allowed when whitelist mode is enabled (one per line).'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Item Blacklist (IDs)
              </label>
              <textarea
                name="itemAttachmentBlacklist"
                rows={4}
                defaultValue={(config?.itemAttachmentBlacklist ?? []).join('\n')}
                onChange={handleFieldChange}
                className={inputClass('itemAttachmentBlacklist')}
                placeholder="minecraft:bedrock"
              />
              {helperText(
                'itemAttachmentBlacklist',
                'Items blocked from attachments (one per line).'
              )}
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Allowed Currencies
              </label>
              <textarea
                name="currencyAttachmentAllowed"
                rows={4}
                defaultValue={(config?.currencyAttachmentAllowed ?? []).join('\n')}
                onChange={handleFieldChange}
                className={inputClass('currencyAttachmentAllowed')}
                placeholder="tokens\nblood_gems\nprestige"
              />
              {helperText(
                'currencyAttachmentAllowed',
                'Leave empty to allow all supported currencies.'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Currency Max Amounts
              </label>
              <textarea
                name="currencyAttachmentMaxAmounts"
                rows={4}
                defaultValue={Object.entries(config?.currencyAttachmentMaxAmounts ?? {})
                  .map(([key, value]) => `${key}=${value}`)
                  .join('\n')}
                onChange={handleFieldChange}
                className={inputClass('currencyAttachmentMaxAmounts')}
                placeholder="tokens=5000\nblood_gems=100"
              />
              {helperText(
                'currencyAttachmentMaxAmounts',
                'Optional per-currency limits (format: currency=amount).'
              )}
            </div>
          </div>

          <div className="mt-6">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="itemAttachmentWhitelistEnabled"
                defaultChecked={config?.itemAttachmentWhitelistEnabled ?? false}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Enable item whitelist mode</span>
            </label>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-primary-100 rounded-lg">
              <Settings className="h-5 w-5 text-primary-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900">Rate Limits</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Messages Per Minute
              </label>
              <input
                name="maxMessagesPerMinute"
                type="number"
                min="1"
                max="60"
                defaultValue={config?.maxMessagesPerMinute ?? 10}
                onChange={handleFieldChange}
                className={inputClass('maxMessagesPerMinute')}
              />
              {helperText(
                'maxMessagesPerMinute',
                'Rate limit for message sending per minute'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Messages Per Day
              </label>
              <input
                name="maxMessagesPerDay"
                type="number"
                min="0"
                max="10000"
                defaultValue={config?.maxMessagesPerDay ?? 0}
                onChange={handleFieldChange}
                className={inputClass('maxMessagesPerDay')}
              />
              {helperText(
                'maxMessagesPerDay',
                'Daily message limit per player (0 to disable)'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Max Per Recipient / Day
              </label>
              <input
                name="maxMessagesPerRecipientPerDay"
                type="number"
                min="0"
                max="10000"
                defaultValue={config?.maxMessagesPerRecipientPerDay ?? 0}
                onChange={handleFieldChange}
                className={inputClass('maxMessagesPerRecipientPerDay')}
              />
              {helperText(
                'maxMessagesPerRecipientPerDay',
                'Daily limit to the same recipient (0 to disable)'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Send Cooldown (Seconds)
              </label>
              <input
                name="sendCooldownSeconds"
                type="number"
                min="0"
                max="300"
                defaultValue={config?.sendCooldownSeconds ?? 5}
                onChange={handleFieldChange}
                className={inputClass('sendCooldownSeconds')}
              />
              {helperText(
                'sendCooldownSeconds',
                'Cooldown between two consecutive sends'
              )}
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-primary-100 rounded-lg">
              <Settings className="h-5 w-5 text-primary-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900">Broadcast</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Broadcast Batch Size
              </label>
              <input
                name="broadcastBatchSize"
                type="number"
                min="1"
                max="5000"
                defaultValue={config?.broadcastBatchSize ?? 500}
                onChange={handleFieldChange}
                className={inputClass('broadcastBatchSize')}
              />
              {helperText(
                'broadcastBatchSize',
                'Recipients per batch during broadcast sends'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Batch Delay (ms)
              </label>
              <input
                name="broadcastBatchDelayMs"
                type="number"
                min="0"
                max="60000"
                defaultValue={config?.broadcastBatchDelayMs ?? 0}
                onChange={handleFieldChange}
                className={inputClass('broadcastBatchDelayMs')}
              />
              {helperText(
                'broadcastBatchDelayMs',
                'Delay between batches (0 for immediate)'
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Queue Threshold
              </label>
              <input
                name="broadcastQueueThreshold"
                type="number"
                min="1"
                max="1000000"
                defaultValue={config?.broadcastQueueThreshold ?? 1000}
                onChange={handleFieldChange}
                className={inputClass('broadcastQueueThreshold')}
              />
              {helperText(
                'broadcastQueueThreshold',
                'Queue broadcasts when recipients exceed this threshold'
              )}
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-4">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="broadcastQueueEnabled"
                defaultChecked={config?.broadcastQueueEnabled ?? false}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Enable broadcast queueing</span>
            </label>
          </div>

          {stats?.totalUsers != null && (
            <div className="mt-6 rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm text-blue-800">
              <span className="font-medium">Estimated delay overhead:</span>{' '}
              {stats.totalUsers === 0
                ? 'No known recipients.'
                : (() => {
                    const recipients = stats.totalUsers;
                    const batchSize = Math.max(1, broadcastPreview.batchSize);
                    const delayMs = Math.max(0, broadcastPreview.delayMs);
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
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <div className="flex items-center gap-3 mb-6">
            <div className="p-2 bg-primary-100 rounded-lg">
              <Settings className="h-5 w-5 text-primary-600" />
            </div>
            <h2 className="text-lg font-semibold text-gray-900">System & Retention</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Message Retention (Days)
              </label>
              <input
                name="messageRetentionDays"
                type="number"
                min="1"
                max="365"
                defaultValue={config?.messageRetentionDays ?? 30}
                onChange={handleFieldChange}
                className={inputClass('messageRetentionDays')}
              />
              {helperText(
                'messageRetentionDays',
                'Days before soft-deleted messages are purged'
              )}
            </div>
          </div>

          <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-4">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="enabled"
                defaultChecked={config?.enabled ?? false}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Enable admin API</span>
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="maintenanceMode"
                defaultChecked={config?.maintenanceMode ?? false}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Maintenance mode (block player sends)</span>
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="useOpLevelForRoles"
                defaultChecked={config?.useOpLevelForRoles ?? false}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Allow op-level to grant admin/tester roles</span>
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                name="hardDeleteOnUserDelete"
                defaultChecked={config?.hardDeleteOnUserDelete ?? false}
                onChange={handleFieldChange}
                className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
              />
              <span className="text-sm text-gray-700">Hard delete on user delete</span>
            </label>
          </div>
          <p className="mt-4 text-xs text-gray-500">
            Disabling the admin API may require server access to re-enable.
          </p>
        </div>

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3 text-sm text-gray-500">
            <div className="flex items-center gap-2">
              <RefreshCw className="h-4 w-4" />
              Changes are applied immediately after saving
            </div>
            {hasChanges && (
              <span className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
                Unsaved changes
              </span>
            )}
          </div>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => {
                void refetch().then(() => setFormKey((prev) => prev + 1));
                setHasChanges(false);
                setErrors({});
                updateMutation.reset();
              }}
              disabled={!hasChanges}
              className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Reset
            </button>
            <button
              type="submit"
              disabled={!hasChanges || updateMutation.isPending}
              className="flex items-center gap-2 px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Save className="h-5 w-5" />
              {updateMutation.isPending ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </div>
      </form>

      {/* Admin UI Preferences */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Admin UI Preferences</h2>
            <p className="text-xs text-gray-500 mt-1">Saved locally in this browser.</p>
          </div>
          <button
            type="button"
            onClick={() => {
              resetToastPreferences();
              setToastErrors({});
              pushToast({ type: 'info', title: 'UI preferences reset' });
            }}
            className="px-3 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50"
          >
            Reset to Defaults
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Toast Position
            </label>
            <select
              name="position"
              value={toastForm.position}
              onChange={handleToastFieldChange}
              className={toastInputClass('position') + ' appearance-none'}
            >
              <option value="top-right">Top Right</option>
              <option value="top-left">Top Left</option>
              <option value="bottom-right">Bottom Right</option>
              <option value="bottom-left">Bottom Left</option>
            </select>
            {toastHelperText('position', 'Where notifications appear')}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Success Toast Duration (ms)
            </label>
            <input
              name="successMs"
              type="number"
              min="0"
              max="60000"
              value={toastForm.successMs}
              onChange={handleToastFieldChange}
              className={toastInputClass('successMs')}
            />
            {toastHelperText('successMs', '0 means sticky until dismissed')}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Info Toast Duration (ms)
            </label>
            <input
              name="infoMs"
              type="number"
              min="0"
              max="60000"
              value={toastForm.infoMs}
              onChange={handleToastFieldChange}
              className={toastInputClass('infoMs')}
            />
            {toastHelperText('infoMs', '0 means sticky until dismissed')}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Error Toast Duration (ms)
            </label>
            <input
              name="errorMs"
              type="number"
              min="0"
              max="60000"
              value={toastForm.errorMs}
              onChange={handleToastFieldChange}
              className={toastInputClass('errorMs')}
            />
            {toastHelperText('errorMs', '0 means sticky until dismissed')}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Max Visible Toasts
            </label>
            <input
              name="maxVisible"
              type="number"
              min="1"
              max="10"
              value={toastForm.maxVisible}
              onChange={handleToastFieldChange}
              className={toastInputClass('maxVisible')}
            />
            {toastHelperText('maxVisible', 'Limit the number of visible toasts')}
          </div>
        </div>

        <div className="mt-6 flex flex-wrap gap-6">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              name="showSuccessToasts"
              checked={toastForm.showSuccessToasts}
              onChange={handleToastFieldChange}
              className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
            />
            <span className="text-sm text-gray-700">Show success toasts</span>
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              name="showInfoToasts"
              checked={toastForm.showInfoToasts}
              onChange={handleToastFieldChange}
              className="h-4 w-4 text-primary-600 border-gray-300 rounded focus:ring-primary-500"
            />
            <span className="text-sm text-gray-700">Show info toasts</span>
          </label>
        </div>

        <div className="flex justify-end gap-3 mt-6">
          <button
            type="button"
            onClick={handleToastSave}
            className="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors"
          >
            Save UI Preferences
          </button>
        </div>
      </div>

      {/* System Info */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">System Information</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="p-4 bg-gray-50 rounded-lg">
            <p className="text-sm text-gray-500">API Version</p>
            <p className="text-lg font-semibold text-gray-900 mt-1">1.0.0</p>
          </div>
          <div className="p-4 bg-gray-50 rounded-lg">
            <p className="text-sm text-gray-500">Database</p>
            <p className="text-lg font-semibold text-gray-900 mt-1">DuckDB</p>
          </div>
          <div className="p-4 bg-gray-50 rounded-lg">
            <p className="text-sm text-gray-500">Cleanup Interval</p>
            <p className="text-lg font-semibold text-gray-900 mt-1">60 minutes</p>
          </div>
        </div>
      </div>
    </div>
  );
}
