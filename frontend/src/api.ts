import type {
  AgentAdvice,
  AgentPlan,
  ScheduleItem,
  Settings,
  Stats,
  Task,
  TimerMode,
  TimerState
} from './types';

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    ...init
  });
  if (!response.ok) {
    let message = `请求失败 (${response.status})`;
    try {
      const body = await response.json();
      message = body.message ?? message;
    } catch {
      // Keep the generic message.
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  settings: () => request<Settings>('/api/settings'),
  updateSettings: (settings: Settings) =>
    request<Settings>('/api/settings', { method: 'PUT', body: JSON.stringify(settings) }),

  tasks: () => request<Task[]>('/api/tasks'),
  createTask: (title: string, estimatedPomodoros: number) =>
    request<Task>('/api/tasks', { method: 'POST', body: JSON.stringify({ title, estimatedPomodoros }) }),
  updateTask: (id: number, patch: Partial<Task>) =>
    request<Task>(`/api/tasks/${id}`, { method: 'PATCH', body: JSON.stringify(patch) }),
  deleteTask: (id: number) => request<void>(`/api/tasks/${id}`, { method: 'DELETE' }),
  reorderTasks: (taskIds: number[]) =>
    request<Task[]>('/api/tasks/reorder', { method: 'POST', body: JSON.stringify({ taskIds }) }),
  setActiveTask: (id: number) => request<Task>(`/api/tasks/${id}/active`, { method: 'POST' }),

  timer: () => request<TimerState>('/api/timer'),
  startTimer: (mode?: TimerMode) =>
    request<TimerState>('/api/timer/start', { method: 'POST', body: JSON.stringify({ mode }) }),
  pauseTimer: () => request<TimerState>('/api/timer/pause', { method: 'POST' }),
  resetTimer: () => request<TimerState>('/api/timer/reset', { method: 'POST' }),
  skipTimer: () => request<TimerState>('/api/timer/skip', { method: 'POST' }),
  completeTimer: () => request<TimerState>('/api/timer/complete', { method: 'POST' }),

  scheduleToday: () => request<ScheduleItem[]>('/api/schedule/today'),
  createSchedule: (item: Omit<ScheduleItem, 'id'>) =>
    request<ScheduleItem>('/api/schedule/today', { method: 'POST', body: JSON.stringify(item) }),
  updateSchedule: (id: number, item: Omit<ScheduleItem, 'id'>) =>
    request<ScheduleItem>(`/api/schedule/today/${id}`, { method: 'PATCH', body: JSON.stringify(item) }),
  deleteSchedule: (id: number) => request<void>(`/api/schedule/today/${id}`, { method: 'DELETE' }),

  stats: () => request<Stats>('/api/stats/today'),
  advice: (question: string) =>
    request<AgentAdvice>('/api/agent/advice', { method: 'POST', body: JSON.stringify({ question }) }),
  plan: (question: string) =>
    request<AgentPlan>('/api/agent/plan', { method: 'POST', body: JSON.stringify({ question }) }),
  applyPlan: (draftId: number, blockIndexes?: number[]) =>
    request<ScheduleItem[]>(`/api/agent/plan/${draftId}/apply`, {
      method: 'POST',
      body: JSON.stringify({ blockIndexes })
    })
};
