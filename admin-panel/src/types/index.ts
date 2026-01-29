// Common enums
export type MessageType = 'PLAYER' | 'SYSTEM' | 'ADMIN' | 'REWARD';
export type NewsCategory = 'PATCH' | 'EVENT' | 'ANNOUNCEMENT' | 'MAINTENANCE' | 'DEV_BLOG' | 'COMMUNITY';
export type TaskStatus = 'pending' | 'in_progress' | 'completed';

// Message types
export interface Message {
  id: string;
  senderUuid: string | null;
  senderName: string | null;
  recipientUuid: string;
  subject: string;
  body: string | null;
  messageType: MessageType;
  createdAtMillis: number;
  readAtMillis: number | null;
  expiresAtMillis: number | null;
  hasAttachment: boolean;
  attachmentClaimed: boolean;
  attachmentData: string | null;
}

export interface CreateMessageRequest {
  recipientUuid: string;
  senderName?: string;
  subject: string;
  body?: string;
  messageType?: MessageType;
  attachmentData?: string;
  expiresAtMillis?: number;
}

export interface BroadcastRequest {
  senderName?: string;
  subject: string;
  body?: string;
  messageType?: MessageType;
  attachmentData?: string;
  expiresAtMillis?: number;
}

// News types
export interface NewsArticle {
  id: string;
  title: string;
  content: string;
  category: NewsCategory;
  authorName: string;
  publishedAtMillis: number | null;
  expiresAtMillis: number | null;
  priority: number;
  active: boolean;
  expired: boolean;
}

export interface CreateNewsRequest {
  title: string;
  content: string;
  category?: NewsCategory;
  authorName?: string;
  publishNow?: boolean;
  publishAtMillis?: number;
  expiresAtMillis?: number;
  priority?: number;
  active?: boolean;
}

export interface UpdateNewsRequest {
  title?: string;
  content?: string;
  category?: NewsCategory;
  authorName?: string;
  publishAtMillis?: number;
  expiresAtMillis?: number;
  priority?: number;
  active?: boolean;
}

// User types
export interface User {
  uuid: string;
  name: string | null;
  isAdmin: boolean;
  isTester: boolean;
  blockedSender: boolean;
  blockedReceiver: boolean;
}

export interface UserDetails {
  uuid: string;
  name: string | null;
  totalMessages: number;
  unreadMessages: number;
  unclaimedAttachments: number;
  isAdmin: boolean;
  isTester: boolean;
  blockedSender: boolean;
  blockedReceiver: boolean;
}

export interface UpdateUserAccessRequest {
  admin?: boolean;
  tester?: boolean;
  blockedSender?: boolean;
  blockedReceiver?: boolean;
}

// Task types
export interface TestTask {
  id: string;
  title: string;
  description: string | null;
  assignedTo: string;
  assignedByName: string | null;
  priority: number;
  status: TaskStatus;
  createdAtMillis: number;
  dueAtMillis: number | null;
  completedAtMillis: number | null;
  notes: string | null;
  overdue: boolean;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  assignedTo: string;
  assignedByName?: string;
  priority?: number;
  dueAtMillis?: number;
}

export interface UpdateTaskRequest {
  title?: string;
  description?: string;
  priority?: number;
  status?: TaskStatus;
  dueAtMillis?: number;
  notes?: string;
}

// Admin audit types
export interface AdminAuditEntry {
  id: string;
  action: string;
  actorUuid: string | null;
  actorName: string;
  targetType: string;
  targetId: string | null;
  details: string | null;
  timestamp: number;
}

// Stats types
export interface Stats {
  totalMessages: number;
  totalUnreadMessages: number;
  totalUsers: number;
  activeNewsArticles: number;
  totalTasks: number;
  pendingTasks: number;
  inProgressTasks: number;
  completedTasks: number;
  apiServerRunning: boolean;
  freeMemoryBytes: number;
  totalMemoryBytes: number;
  timestampMillis: number;
}

// Config types
export interface Config {
  enabled: boolean;
  playerToPlayerEnabled: boolean;
  maxMessagesPerPlayer: number;
  maxSubjectLength: number;
  maxBodyLength: number;
  defaultMessageTtlHours: number;
  maxMessagesPerMinute: number;
  maxMessagesPerDay: number;
  maxMessagesPerRecipientPerDay: number;
  sendCooldownSeconds: number;
  broadcastBatchSize: number;
  broadcastBatchDelayMs: number;
  broadcastQueueEnabled: boolean;
  broadcastQueueThreshold: number;
  deliveryDispatchEnabled: boolean;
  deliveryImmediateDispatchEnabled: boolean;
  deliveryDispatchIntervalSeconds: number;
  deliveryDispatchBatchSize: number;
  deliveryMaxAttempts: number;
  deliveryRetryDelaySeconds: number;
  deliveryRetryMaxDelaySeconds: number;
  deliveryRetryBackoffMultiplier: number;
  deliveryRetryJitterRatio: number;
  deliveryRecallEnabled: boolean;
  minLevelToSend: number;
  itemAttachmentsEnabled: boolean;
  currencyAttachmentsEnabled: boolean;
  maxAttachmentsPerMessage: number;
  messageRetentionDays: number;
  hardDeleteOnUserDelete: boolean;
  maintenanceMode: boolean;
  useOpLevelForRoles: boolean;
  contentFilterEnabled: boolean;
  contentFilterAction: string;
  contentFilterWords: string[];
  contentFilterPatterns: string[];
  itemAttachmentWhitelistEnabled: boolean;
  itemAttachmentWhitelist: string[];
  itemAttachmentBlacklist: string[];
  currencyAttachmentAllowed: string[];
  currencyAttachmentMaxAmounts: Record<string, number>;
}

export interface BroadcastJob {
  jobId: string;
  subject: string;
  totalRecipients: number;
  sentCount: number;
  failedCount: number;
  state: string;
  startedAtMillis: number | null;
  completedAtMillis: number | null;
  errorMessage: string | null;
}

// API response types
export interface ListResponse<T> {
  items: T[];
  count: number;
}

export interface ErrorResponse {
  status: string;
  message: string;
}

// Auth types
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  success: boolean;
  token: string | null;
  expiresAt: number | null;
  error?: string | null;
}
