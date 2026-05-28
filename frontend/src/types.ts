export type TimerMode = 'POMODORO' | 'SHORT_BREAK' | 'LONG_BREAK';
export type TimerRunStatus = 'NEW' | 'PLAYING' | 'PAUSED';
export type ScheduleStatus = 'PLANNED' | 'IN_PROGRESS' | 'DONE' | 'SKIPPED';
export type ScheduleSource = 'USER' | 'AGENT';

export interface Settings {
  workMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  longBreakInterval: number;
  notificationsEnabled: boolean;
  alarmSound: string;
  alarmRepeat: boolean;
  alarmVolume: number;
  theme: string;
}

export interface Task {
  id: number;
  title: string;
  estimatedPomodoros: number;
  completedPomodoros: number;
  done: boolean;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface TimerState {
  mode: TimerMode;
  status: TimerRunStatus;
  totalSeconds: number;
  remainingSeconds: number;
  sessionId: number | null;
  activeTaskId: number | null;
  activeTaskTitle: string | null;
  completedPomodorosToday: number;
  hint: string;
}

export interface ScheduleItem {
  id: number;
  title: string;
  startAt: string;
  endAt: string;
  status: ScheduleStatus;
  source: ScheduleSource;
  taskId: number | null;
  notes: string | null;
}

export interface Stats {
  completedPomodorosToday: number;
  sessionsToday: number;
  interruptionsToday: number;
  unfinishedTasks: number;
  recentSessions: Array<{
    id: number;
    mode: TimerMode;
    status: string;
    taskTitle: string | null;
    startedAt: string;
    endedAt: string | null;
    elapsedSeconds: number;
  }>;
}

export interface DailyStats {
  date: string;
  completedPomodoros: number;
  sessions: number;
  interruptions: number;
  focusMinutes: number;
}

export interface StatsTrend {
  days: DailyStats[];
}

export interface Interruption {
  id: number;
  note: string;
  taskId: number | null;
  taskTitle: string | null;
  timerSessionId: number | null;
  occurredAt: string;
}

export interface ScheduleBlock {
  title: string;
  startAt: string;
  endAt: string;
  notes: string | null;
  taskId: number | null;
}

export interface AgentAdvice {
  conversationId: number;
  advice: string;
  reasoningSummary: string;
  warnings: string[];
}

export interface AgentPlan {
  draftId: number;
  title: string;
  advice: string;
  reasoningSummary: string;
  blocks: ScheduleBlock[];
  warnings: string[];
}

export interface ScheduleBlockPreview {
  index: number;
  block: ScheduleBlock;
  conflict: boolean;
  conflictMessage: string | null;
}

export interface AgentPlanPreview {
  draftId: number;
  blocks: ScheduleBlockPreview[];
}
