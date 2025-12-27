import axios, { AxiosInstance, AxiosError } from 'axios';

const API_BASE_URL = '/api';

class ApiClient {
  private client: AxiosInstance;
  private token: string | null = null;

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Add auth token to requests
    this.client.interceptors.request.use((config) => {
      if (this.token) {
        config.headers.Authorization = `Bearer ${this.token}`;
      }
      return config;
    });

    // Handle 401 errors
    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        if (error.response?.status === 401) {
          this.token = null;
          localStorage.removeItem('auth_token');
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    );

    // Load token from storage
    const storedToken = localStorage.getItem('auth_token');
    if (storedToken) {
      this.token = storedToken;
    }
  }

  setToken(token: string | null) {
    this.token = token;
    if (token) {
      localStorage.setItem('auth_token', token);
    } else {
      localStorage.removeItem('auth_token');
    }
  }

  getToken(): string | null {
    return this.token;
  }

  isAuthenticated(): boolean {
    return this.token !== null;
  }

  // Auth
  async login(username: string, password: string): Promise<{ token: string; expiresAt: number }> {
    const response = await this.client.post('/auth/login', { username, password });
    return response.data;
  }

  // Messages
  async getMessages(limit = 50, offset = 0, recipientUuid?: string) {
    const params: Record<string, string | number> = { limit, offset };
    if (recipientUuid) params.recipientUuid = recipientUuid;
    const response = await this.client.get('/messages', { params });
    return response.data;
  }

  async getMessage(id: string) {
    const response = await this.client.get(`/messages/${id}`);
    return response.data;
  }

  async createMessage(data: {
    recipientUuid: string;
    senderName?: string;
    subject: string;
    body?: string;
    messageType?: string;
    attachmentData?: string;
    expiresAtMillis?: number;
  }) {
    const response = await this.client.post('/messages', data);
    return response.data;
  }

  async broadcastMessage(data: {
    senderName?: string;
    subject: string;
    body?: string;
    messageType?: string;
    attachmentData?: string;
    expiresAtMillis?: number;
  }) {
    const response = await this.client.post('/messages/broadcast', data);
    return response.data;
  }

  async getBroadcastJobsActive() {
    const response = await this.client.get('/broadcast/jobs/active');
    return response.data;
  }

  async getBroadcastJobsRecent(limit = 50) {
    const response = await this.client.get('/broadcast/jobs/recent', { params: { limit } });
    return response.data;
  }

  async getBroadcastJob(id: string) {
    const response = await this.client.get(`/broadcast/jobs/${id}`);
    return response.data;
  }

  async deleteMessage(id: string) {
    await this.client.delete(`/messages/${id}`);
  }

  // News
  async getNews(activeOnly = true, category?: string) {
    const params: Record<string, string | boolean> = { activeOnly };
    if (category) params.category = category;
    const response = await this.client.get('/news', { params });
    return response.data;
  }

  async getNewsArticle(id: string) {
    const response = await this.client.get(`/news/${id}`);
    return response.data;
  }

  async createNews(data: {
    title: string;
    content: string;
    category?: string;
    authorName?: string;
    publishNow?: boolean;
    publishAtMillis?: number;
    expiresAtMillis?: number;
    priority?: number;
    active?: boolean;
  }) {
    const response = await this.client.post('/news', data);
    return response.data;
  }

  async updateNews(id: string, data: {
    title?: string;
    content?: string;
    category?: string;
    authorName?: string;
    publishAtMillis?: number;
    expiresAtMillis?: number;
    priority?: number;
    active?: boolean;
  }) {
    const response = await this.client.put(`/news/${id}`, data);
    return response.data;
  }

  async deleteNews(id: string) {
    await this.client.delete(`/news/${id}`);
  }

  // Users
  async getUsers() {
    const response = await this.client.get('/users');
    return response.data;
  }

  async getUser(uuid: string) {
    const response = await this.client.get(`/users/${uuid}`);
    return response.data;
  }

  async updateUserAccess(uuid: string, data: {
    admin?: boolean;
    tester?: boolean;
    blockedSender?: boolean;
    blockedReceiver?: boolean;
  }) {
    const response = await this.client.put(`/users/${uuid}/access`, data);
    return response.data;
  }

  async getUserInbox(uuid: string, limit = 50, includeRead = true) {
    const response = await this.client.get(`/users/${uuid}/inbox`, {
      params: { limit, includeRead },
    });
    return response.data;
  }

  // Tasks
  async getTasks() {
    const response = await this.client.get('/tasks');
    return response.data;
  }

  async getTask(id: string) {
    const response = await this.client.get(`/tasks/${id}`);
    return response.data;
  }

  async createTask(data: {
    title: string;
    description?: string;
    assignedTo: string;
    assignedByName?: string;
    priority?: number;
    dueAt?: number;
  }) {
    const response = await this.client.post('/tasks', data);
    return response.data;
  }

  async updateTask(id: string, data: {
    title?: string;
    description?: string;
    assignedTo?: string;
    priority?: number;
    status?: string;
    dueAt?: number;
    notes?: string;
  }) {
    const response = await this.client.put(`/tasks/${id}`, data);
    return response.data;
  }

  async deleteTask(id: string) {
    await this.client.delete(`/tasks/${id}`);
  }

  // Admin Audit
  async getAuditEntries(params?: { limit?: number; action?: string; actor?: string }) {
    const response = await this.client.get('/audit', { params });
    return response.data;
  }

  // Config & Stats
  async getConfig() {
    const response = await this.client.get('/config');
    return response.data;
  }

  async updateConfig(data: Record<string, unknown>) {
    const response = await this.client.put('/config', data);
    return response.data;
  }

  async getStats() {
    const response = await this.client.get('/stats');
    return response.data;
  }
}

export const api = new ApiClient();
