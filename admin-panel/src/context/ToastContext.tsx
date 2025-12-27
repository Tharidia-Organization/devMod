import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

type ToastType = 'success' | 'error' | 'info';
type ToastPosition = 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';

interface ToastAction {
  label: string;
  onClick: () => void;
}

interface ToastPreferences {
  position: ToastPosition;
  durations: Record<ToastType, number>;
  maxVisible: number;
  showSuccessToasts: boolean;
  showInfoToasts: boolean;
}

interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  durationMs?: number;
  action?: ToastAction;
}

interface ToastContextValue {
  pushToast: (toast: Omit<Toast, 'id'>) => void;
  preferences: ToastPreferences;
  updatePreferences: (next: Partial<ToastPreferences>) => void;
  resetPreferences: () => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

const STORAGE_KEY = 'devmod.admin.toastPreferences';
const MAX_DURATION_MS = 60000;
const MIN_MAX_VISIBLE = 1;
const MAX_MAX_VISIBLE = 10;
const DEFAULT_PREFERENCES: ToastPreferences = {
  position: 'top-right',
  durations: {
    success: 3500,
    info: 4000,
    error: 6000,
  },
  maxVisible: 5,
  showSuccessToasts: true,
  showInfoToasts: true,
};

const typeStyles: Record<ToastType, string> = {
  success: 'border-green-200 bg-green-50 text-green-900',
  info: 'border-blue-200 bg-blue-50 text-blue-900',
  error: 'border-red-200 bg-red-50 text-red-900',
};

const positionStyles: Record<ToastPosition, string> = {
  'top-right': 'top-2 right-2 sm:top-4 sm:right-4',
  'top-left': 'top-2 left-2 sm:top-4 sm:left-4',
  'bottom-right': 'bottom-2 right-2 sm:bottom-4 sm:right-4',
  'bottom-left': 'bottom-2 left-2 sm:bottom-4 sm:left-4',
};

const EXIT_ANIMATION_MS = 200;

const sanitizeDuration = (value: number, fallback: number) => {
  if (!Number.isFinite(value)) return fallback;
  return Math.max(0, Math.min(MAX_DURATION_MS, Math.round(value)));
};

const sanitizePreferences = (input?: Partial<ToastPreferences>): ToastPreferences => {
  const position = input?.position ?? DEFAULT_PREFERENCES.position;
  const durations = {
    success: sanitizeDuration(
      input?.durations?.success ?? DEFAULT_PREFERENCES.durations.success,
      DEFAULT_PREFERENCES.durations.success
    ),
    info: sanitizeDuration(
      input?.durations?.info ?? DEFAULT_PREFERENCES.durations.info,
      DEFAULT_PREFERENCES.durations.info
    ),
    error: sanitizeDuration(
      input?.durations?.error ?? DEFAULT_PREFERENCES.durations.error,
      DEFAULT_PREFERENCES.durations.error
    ),
  };
  const validPositions: ToastPosition[] = ['top-right', 'top-left', 'bottom-right', 'bottom-left'];
  return {
    position: validPositions.includes(position) ? position : DEFAULT_PREFERENCES.position,
    durations,
    maxVisible: Math.max(
      MIN_MAX_VISIBLE,
      Math.min(MAX_MAX_VISIBLE, Math.round(input?.maxVisible ?? DEFAULT_PREFERENCES.maxVisible))
    ),
    showSuccessToasts: input?.showSuccessToasts ?? DEFAULT_PREFERENCES.showSuccessToasts,
    showInfoToasts: input?.showInfoToasts ?? DEFAULT_PREFERENCES.showInfoToasts,
  };
};

const loadPreferences = (): ToastPreferences => {
  if (typeof window === 'undefined') {
    return DEFAULT_PREFERENCES;
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_PREFERENCES;
    const parsed = JSON.parse(raw) as Partial<ToastPreferences>;
    return sanitizePreferences(parsed);
  } catch {
    return DEFAULT_PREFERENCES;
  }
};

const typeIcon = (type: ToastType) => {
  switch (type) {
    case 'success':
      return <CheckCircle2 className="h-5 w-5 text-green-600" />;
    case 'error':
      return <AlertCircle className="h-5 w-5 text-red-600" />;
    default:
      return <Info className="h-5 w-5 text-blue-600" />;
  }
};

export function ToastProvider({
  children,
  position: positionOverride,
}: {
  children: React.ReactNode;
  position?: ToastPosition;
}) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [closingIds, setClosingIds] = useState<Record<string, boolean>>({});
  const [preferences, setPreferences] = useState<ToastPreferences>(() => loadPreferences());
  const counter = useRef(0);

  const finalizeRemove = useCallback((id: string) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
    setClosingIds((prev) => {
      if (!prev[id]) return prev;
      const next = { ...prev };
      delete next[id];
      return next;
    });
  }, []);

  const dismissToast = useCallback((id: string) => {
    setClosingIds((prev) => (prev[id] ? prev : { ...prev, [id]: true }));
    window.setTimeout(() => finalizeRemove(id), EXIT_ANIMATION_MS);
  }, [finalizeRemove]);

  const dismissAll = useCallback(() => {
    toasts.forEach((toast) => dismissToast(toast.id));
  }, [dismissToast, toasts]);

  useEffect(() => {
    setToasts((prev) => {
      if (prev.length <= preferences.maxVisible) return prev;
      return prev.slice(prev.length - preferences.maxVisible);
    });
  }, [preferences.maxVisible]);

  const updatePreferences = useCallback((next: Partial<ToastPreferences>) => {
    setPreferences((prev) => {
      const merged = sanitizePreferences({
        position: next.position ?? prev.position,
        durations: { ...prev.durations, ...next.durations },
        maxVisible: next.maxVisible ?? prev.maxVisible,
        showSuccessToasts: next.showSuccessToasts ?? prev.showSuccessToasts,
        showInfoToasts: next.showInfoToasts ?? prev.showInfoToasts,
      });
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
      }
      return merged;
    });
  }, []);

  const resetPreferences = useCallback(() => {
    setPreferences(DEFAULT_PREFERENCES);
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  }, []);

  const pushToast = useCallback((toast: Omit<Toast, 'id'>) => {
    if (toast.type === 'success' && !preferences.showSuccessToasts) {
      return;
    }
    if (toast.type === 'info' && !preferences.showInfoToasts) {
      return;
    }
    const id = `toast-${Date.now()}-${counter.current++}`;
    setToasts((prev) => {
      const next = [...prev, { id, ...toast }];
      if (next.length > preferences.maxVisible) {
        return next.slice(next.length - preferences.maxVisible);
      }
      return next;
    });
    const duration = toast.durationMs ?? preferences.durations[toast.type] ?? 4000;
    if (duration > 0) {
      window.setTimeout(() => dismissToast(id), duration);
    }
  }, [dismissToast, preferences.durations, preferences.maxVisible]);

  const value = useMemo(
    () => ({ pushToast, preferences, updatePreferences, resetPreferences }),
    [pushToast, preferences, updatePreferences, resetPreferences]
  );

  const resolvedPosition = positionOverride ?? preferences.position;

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className={`fixed z-50 space-y-3 pointer-events-none ${positionStyles[resolvedPosition]}`}
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        {toasts.length > 1 && (
          <div className="pointer-events-auto flex justify-end">
            <button
              type="button"
              onClick={dismissAll}
              className="rounded-full border border-gray-200 bg-white px-3 py-1 text-xs font-medium text-gray-600 shadow-sm hover:bg-gray-50"
            >
              Clear all
            </button>
          </div>
        )}
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`toast-enter ${closingIds[toast.id] ? 'toast-exit' : ''} pointer-events-auto w-[calc(100vw-2rem)] sm:w-80 rounded-lg border px-4 py-3 shadow-sm ${typeStyles[toast.type]}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                {typeIcon(toast.type)}
                <div>
                  <p className="text-sm font-semibold">{toast.title}</p>
                  {toast.message && (
                    <p className="text-xs text-gray-600 mt-1">{toast.message}</p>
                  )}
                </div>
              </div>
              <button
                type="button"
                onClick={() => dismissToast(toast.id)}
                className="text-gray-400 hover:text-gray-600"
                aria-label="Dismiss"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            {toast.action && (
              <div className="mt-2 pl-8">
                <button
                  type="button"
                  onClick={() => {
                    toast.action?.onClick();
                    dismissToast(toast.id);
                  }}
                  className="text-xs font-medium text-gray-700 hover:text-gray-900 underline underline-offset-2"
                >
                  {toast.action.label}
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
}
