import {
  AlertCircle,
  Bell,
  Bot,
  CalendarClock,
  Check,
  ChevronDown,
  ChevronUp,
  Clock3,
  ListChecks,
  Loader2,
  MessageCircle,
  Minimize2,
  Pause,
  Play,
  Plus,
  RotateCcw,
  Save,
  Send,
  Settings as SettingsIcon,
  SkipForward,
  Target,
  Trash2,
  Volume2,
  WandSparkles,
  X
} from 'lucide-react';
import { CSSProperties, FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api';
import { scheduleTimesForDailyPlan } from './timeMaster';
import type {
  AgentPlan,
  Interruption,
  ScheduleBlock,
  ScheduleItem,
  ScheduleStatus,
  Settings,
  Stats,
  StatsTrend,
  Task,
  TimerMode,
  TimerState
} from './types';
import type {
  TimeMasterDailyPlan,
  TimeMasterHabits,
  TimeMasterPlan
} from './timeMaster';

const defaultSettings: Settings = {
  workMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  longBreakInterval: 4,
  notificationsEnabled: false,
  alarmSound: 'simple-notification',
  alarmRepeat: false,
  alarmVolume: 50,
  theme: 'system'
};

const modeLabels: Record<TimerMode, string> = {
  POMODORO: '番茄钟',
  SHORT_BREAK: '短休息',
  LONG_BREAK: '长休息'
};

const statusLabels: Record<ScheduleStatus, string> = {
  PLANNED: '待开始',
  IN_PROGRESS: '进行中',
  DONE: '已完成',
  SKIPPED: '已跳过'
};

const mascotGreetings = [
  '我在这儿，需要我帮你排一下吗？',
  '小茄巡视中：先挑一个最小动作也很好。',
  '时间块到点我会提醒你。',
  '有压力就点我，我们把它拆小。'
];

type AgentMode = 'ADVICE' | 'PLAN';

interface ChatMessage {
  id: number;
  role: 'agent' | 'user';
  text: string;
  plan?: AgentPlan;
  warnings?: string[];
}

type AppPage = 'HOME' | 'TIME_MASTER';

interface TimeMasterQuestion<T extends keyof TimeMasterHabits> {
  id: T;
  eyebrow: string;
  title: string;
  options: Array<{ value: TimeMasterHabits[T]; label: string; description: string }>;
}

interface TimeMasterFormState {
  taskTitle: string;
  taskContent: string;
  startDate: string;
  endDate: string;
  dailyMinutes: number;
}

const timeMasterQuestions: TimeMasterQuestion<keyof TimeMasterHabits>[] = [
  {
    id: 'energy',
    eyebrow: '精力节律',
    title: '你通常哪段时间最适合处理难任务？',
    options: [
      { value: 'morning', label: '早上', description: '把关键推进放在上午' },
      { value: 'afternoon', label: '下午', description: '适合稳定执行和协作' },
      { value: 'evening', label: '晚上', description: '适合安静深度处理' }
    ]
  },
  {
    id: 'focusStyle',
    eyebrow: '专注方式',
    title: '你更习惯怎样完成一段长期任务？',
    options: [
      { value: 'deep', label: '整块深度', description: '每天安排较完整的专注块' },
      { value: 'balanced', label: '稳步推进', description: '强度均衡，便于持续' },
      { value: 'fragmented', label: '碎片拼接', description: '拆成更小的每日动作' }
    ]
  },
  {
    id: 'restPattern',
    eyebrow: '休息策略',
    title: '休息日应该如何参与计划？',
    options: [
      { value: 'weekend-light', label: '轻量维护', description: '周末降载，保留连续性' },
      { value: 'steady', label: '强度稳定', description: '每天保持接近投入' },
      { value: 'weekday-only', label: '工作日为主', description: '休息日只做复盘检查' }
    ]
  },
  {
    id: 'reviewPreference',
    eyebrow: '复盘频率',
    title: '你希望系统多久提醒你调整方向？',
    options: [
      { value: 'weekly', label: '每周复盘', description: '适合多数长任务节奏' },
      { value: 'daily', label: '每天复盘', description: '适合变化快的任务' },
      { value: 'milestone', label: '阶段复盘', description: '适合目标清晰的项目' }
    ]
  }
];

function formatSeconds(seconds: number) {
  const safe = Math.max(0, seconds);
  const minutes = Math.floor(safe / 60).toString().padStart(2, '0');
  const rest = (safe % 60).toString().padStart(2, '0');
  return `${minutes}:${rest}`;
}

function toInputDateTime(value: Date) {
  const offset = value.getTimezoneOffset() * 60000;
  return new Date(value.getTime() - offset).toISOString().slice(0, 16);
}

function toInputDate(value: Date) {
  return toInputDateTime(value).slice(0, 10);
}

function addInputDays(value: string, days: number) {
  const [year, month, day] = value.split('-').map(Number);
  return toInputDate(new Date(year, month - 1, day + days));
}

function defaultTimeMasterForm(): TimeMasterFormState {
  const startDate = toInputDate(new Date());
  return {
    taskTitle: '完成一个长期任务',
    taskContent: '明确目标、阶段推进、每日执行、复盘交付',
    startDate,
    endDate: addInputDays(startDate, 13),
    dailyMinutes: 90
  };
}

function resolveTimeMasterHabits(habits: Partial<TimeMasterHabits>): TimeMasterHabits {
  return {
    energy: habits.energy ?? 'morning',
    focusStyle: habits.focusStyle ?? 'balanced',
    restPattern: habits.restPattern ?? 'weekend-light',
    reviewPreference: habits.reviewPreference ?? 'weekly'
  };
}

function modeDuration(settings: Settings, mode: TimerMode) {
  if (mode === 'POMODORO') return settings.workMinutes * 60;
  if (mode === 'SHORT_BREAK') return settings.shortBreakMinutes * 60;
  return settings.longBreakMinutes * 60;
}

function formatClock(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '--:--';
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
}

function formatTimeRange(startAt: string, endAt: string) {
  return `${formatClock(startAt)} - ${formatClock(endAt)}`;
}

function durationLabel(startAt: string, endAt: string) {
  const start = new Date(startAt).getTime();
  const end = new Date(endAt).getTime();
  if (Number.isNaN(start) || Number.isNaN(end) || end <= start) return '时间未确定';
  const minutes = Math.round((end - start) / 60000);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`;
}

function emptyScheduleForm(): Omit<ScheduleItem, 'id'> {
  const start = new Date();
  start.setMinutes(Math.ceil(start.getMinutes() / 5) * 5, 0, 0);
  const end = new Date(start.getTime() + 25 * 60000);
  return {
    title: '',
    startAt: toInputDateTime(start),
    endAt: toInputDateTime(end),
    status: 'PLANNED',
    source: 'USER',
    taskId: null,
    notes: ''
  };
}

export default function App() {
  const [activePage, setActivePage] = useState<AppPage>('HOME');
  const [settings, setSettings] = useState<Settings>(defaultSettings);
  const [settingsDraft, setSettingsDraft] = useState<Settings>(defaultSettings);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [timer, setTimer] = useState<TimerState | null>(null);
  const [schedule, setSchedule] = useState<ScheduleItem[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [statsTrend, setStatsTrend] = useState<StatsTrend | null>(null);
  const [interruptions, setInterruptions] = useState<Interruption[]>([]);
  const [selectedMode, setSelectedMode] = useState<TimerMode>('POMODORO');
  const [localRemaining, setLocalRemaining] = useState(defaultSettings.workMinutes * 60);
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [newTaskPomodoros, setNewTaskPomodoros] = useState(1);
  const [scheduleForm, setScheduleForm] = useState<Omit<ScheduleItem, 'id'>>(emptyScheduleForm);
  const [editingScheduleId, setEditingScheduleId] = useState<number | null>(null);
  const [scheduleEditForm, setScheduleEditForm] = useState<Omit<ScheduleItem, 'id'>>(emptyScheduleForm);
  const [interruptionNote, setInterruptionNote] = useState('');
  const [scheduleNotice, setScheduleNotice] = useState<string | null>(null);
  const [timeMasterStep, setTimeMasterStep] = useState(0);
  const [timeMasterHabits, setTimeMasterHabits] = useState<Partial<TimeMasterHabits>>({});
  const [timeMasterForm, setTimeMasterForm] = useState<TimeMasterFormState>(() => defaultTimeMasterForm());
  const [timeMasterPlan, setTimeMasterPlan] = useState<TimeMasterPlan | null>(null);
  const [selectedTimeMasterPhase, setSelectedTimeMasterPhase] = useState(0);
  const [timeMasterRationaleOpen, setTimeMasterRationaleOpen] = useState(false);
  const [agentOpen, setAgentOpen] = useState(false);
  const [agentMode, setAgentMode] = useState<AgentMode>('ADVICE');
  const [agentInput, setAgentInput] = useState('');
  const [agentMessages, setAgentMessages] = useState<ChatMessage[]>(() => [
    {
      id: 1,
      role: 'agent',
      text: '你好，我是小茄。我可以结合你的任务、番茄记录和时间安排，给你时间管理建议，也能先生成计划草稿，等你确认后再加入时间安排。现在你想先处理什么？'
    }
  ]);
  const [mascotGreeting, setMascotGreeting] = useState(mascotGreetings[0]);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const completingRef = useRef(false);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const scheduleStartedRef = useRef<Set<number>>(new Set());
  const scheduleEndedRef = useRef<Set<number>>(new Set());
  const chatIdRef = useRef(2);

  const displayMode = timer?.status === 'PLAYING' || timer?.status === 'PAUSED' ? timer.mode : selectedMode;
  const displayTotalSeconds = timer?.status === 'PLAYING' || timer?.status === 'PAUSED'
    ? timer.totalSeconds
    : modeDuration(settings, selectedMode);
  const progress = 1 - localRemaining / Math.max(displayTotalSeconds, 1);
  const ringStyle = { '--progress': `${Math.max(0, Math.min(1, progress)) * 360}deg` } as CSSProperties;
  const canChangeMode = !timer || timer.status === 'NEW';
  const timerHint = timer?.status === 'NEW' && timer.mode !== displayMode
    ? `${modeLabels[displayMode]}准备开始`
    : timer?.hint ?? '准备开始';

  const refresh = useCallback(async () => {
    const [nextSettings, nextTasks, nextTimer, nextSchedule, nextStats, nextTrend, nextInterruptions] = await Promise.all([
      api.settings(),
      api.tasks(),
      api.timer(),
      api.scheduleToday(),
      api.stats(),
      api.statsTrend(7),
      api.interruptionsToday()
    ]);
    setSettings(nextSettings);
    setSettingsDraft(nextSettings);
    setTasks(nextTasks);
    setTimer(nextTimer);
    setSelectedMode(nextTimer.mode);
    setLocalRemaining(nextTimer.remainingSeconds);
    setSchedule(nextSchedule);
    setStats(nextStats);
    setStatsTrend(nextTrend);
    setInterruptions(nextInterruptions);
  }, []);

  useEffect(() => {
    refresh()
      .catch((ex: Error) => setError(ex.message))
      .finally(() => setLoading(false));
  }, [refresh]);

  useEffect(() => {
    if (!timer || timer.status !== 'PLAYING') return;
    const id = window.setInterval(() => {
      setLocalRemaining((remaining) => {
        if (remaining <= 1) {
          if (!completingRef.current) {
            completingRef.current = true;
            void completeTimer();
          }
          return 0;
        }
        return remaining - 1;
      });
    }, 1000);
    return () => window.clearInterval(id);
  }, [timer]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
      if (event.code === 'Space') {
        event.preventDefault();
        void toggleTimer();
      }
      if (event.code === 'Digit1') chooseMode('POMODORO');
      if (event.code === 'Digit2') chooseMode('SHORT_BREAK');
      if (event.code === 'Digit3') chooseMode('LONG_BREAK');
      if (event.code === 'KeyS') setSettingsOpen((open) => !open);
      if (event.code === 'KeyR') void resetTimer();
      if (event.code === 'KeyN' && timer?.status === 'PLAYING') void skipTimer();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [timer, selectedMode, settings]);

  useEffect(() => {
    const id = window.setInterval(() => {
      setMascotGreeting((current) => {
        const index = mascotGreetings.indexOf(current);
        return mascotGreetings[(index + 1) % mascotGreetings.length];
      });
    }, 12000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    const checkSchedule = () => {
      const now = Date.now();
      for (const item of schedule) {
        const start = new Date(item.startAt).getTime();
        const end = new Date(item.endAt).getTime();
        if (Number.isNaN(start) || Number.isNaN(end)) continue;
        if (now >= start && now < end && !scheduleStartedRef.current.has(item.id)) {
          scheduleStartedRef.current.add(item.id);
          const message = `现在开始：${item.title}，时间范围 ${formatTimeRange(item.startAt, item.endAt)}。`;
          setScheduleNotice(message);
          playAlarmSound();
          notify(message);
        }
        if (now >= end && !scheduleEndedRef.current.has(item.id)) {
          scheduleEndedRef.current.add(item.id);
          const message = `时间到：${item.title} 已结束，可以复盘一下再进入下一项。`;
          setScheduleNotice(message);
          playAlarmSound();
          notify(message);
        }
      }
    };
    checkSchedule();
    const id = window.setInterval(checkSchedule, 30000);
    return () => window.clearInterval(id);
  }, [schedule, settings.alarmVolume, settings.alarmRepeat, settings.notificationsEnabled]);

  useEffect(() => {
    const id = window.setInterval(() => {
      api.scheduleToday()
        .then(setSchedule)
        .catch(() => {
          // Keep the current timeline visible if a background refresh fails.
        });
    }, 30000);
    return () => window.clearInterval(id);
  }, []);

  async function run<T>(operation: () => Promise<T>, after?: (value: T) => void) {
    setBusy(true);
    setError(null);
    try {
      const value = await operation();
      after?.(value);
      return value;
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : '操作失败');
      return null;
    } finally {
      setBusy(false);
    }
  }

  function chooseMode(mode: TimerMode) {
    if (!canChangeMode) return;
    const seconds = modeDuration(settings, mode);
    setSelectedMode(mode);
    setLocalRemaining(seconds);
    setTimer((current) => current && current.status === 'NEW'
      ? { ...current, mode, totalSeconds: seconds, remainingSeconds: seconds, hint: `${modeLabels[mode]}准备开始` }
      : current);
  }

  async function toggleTimer() {
    if (timer?.status === 'PLAYING') {
      await run(api.pauseTimer, setTimerAndRemaining);
      return;
    }
    await run(() => api.startTimer(selectedMode), setTimerAndRemaining);
  }

  async function resetTimer() {
    await run(api.resetTimer, (next) => {
      setTimerAndRemaining(next);
      setSelectedMode(next.mode);
    });
  }

  async function skipTimer() {
    await run(api.skipTimer, (next) => {
      setTimerAndRemaining(next);
      setSelectedMode(next.mode);
      void refreshSideData();
    });
  }

  async function completeTimer() {
    await run(api.completeTimer, (next) => {
      setTimerAndRemaining(next);
      setSelectedMode(next.mode);
      playAlarmSound();
      notify(next.hint);
      void refreshSideData();
    });
    completingRef.current = false;
  }

  async function refreshSideData() {
    const [nextTasks, nextSchedule, nextStats, nextTrend, nextInterruptions] = await Promise.all([
      api.tasks(),
      api.scheduleToday(),
      api.stats(),
      api.statsTrend(7),
      api.interruptionsToday()
    ]);
    setTasks(nextTasks);
    setSchedule(nextSchedule);
    setStats(nextStats);
    setStatsTrend(nextTrend);
    setInterruptions(nextInterruptions);
  }

  function setTimerAndRemaining(next: TimerState) {
    setTimer(next);
    setLocalRemaining(next.remainingSeconds);
  }

  function notify(message: string) {
    if (!settings.notificationsEnabled || !('Notification' in window) || Notification.permission !== 'granted') return;
    new Notification('Fanqie', { body: message });
  }

  function playAlarmSound() {
    const AudioContextCtor = window.AudioContext
      ?? (window as Window & typeof globalThis & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextCtor) return;
    const ctx = audioCtxRef.current ?? new AudioContextCtor();
    audioCtxRef.current = ctx;
    if (ctx.state === 'suspended') {
      void ctx.resume();
    }
    const volume = Math.max(0.02, Math.min(1, settings.alarmVolume / 100));
    const repeat = settings.alarmRepeat ? 3 : 1;
    const tones = settings.alarmSound === 'soft-bell'
      ? [880, 660]
      : settings.alarmSound === 'deep-chime'
        ? [523, 392]
        : [1046, 784];
    for (let i = 0; i < repeat; i += 1) {
      [0, 0.18].forEach((offset, index) => {
        const oscillator = ctx.createOscillator();
        const gain = ctx.createGain();
        const start = ctx.currentTime + i * 0.58 + offset;
        oscillator.type = 'square';
        oscillator.frequency.setValueAtTime(tones[index], start);
        gain.gain.setValueAtTime(0.0001, start);
        gain.gain.exponentialRampToValueAtTime(0.18 * volume, start + 0.015);
        gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.13);
        oscillator.connect(gain);
        gain.connect(ctx.destination);
        oscillator.start(start);
        oscillator.stop(start + 0.15);
      });
    }
  }

  function testAlarmSound() {
    playAlarmSound();
    setScheduleNotice('通知音测试已播放。');
  }

  async function submitTask(event: FormEvent) {
    event.preventDefault();
    if (!newTaskTitle.trim()) return;
    await run(() => api.createTask(newTaskTitle, newTaskPomodoros), async () => {
      setNewTaskTitle('');
      setNewTaskPomodoros(1);
      await refreshSideData();
    });
  }

  async function patchTask(id: number, patch: Partial<Task>) {
    await run(() => api.updateTask(id, patch), async () => refreshSideData());
  }

  async function moveTask(task: Task, direction: -1 | 1) {
    const index = tasks.findIndex((item) => item.id === task.id);
    const nextIndex = index + direction;
    if (nextIndex < 0 || nextIndex >= tasks.length) return;
    const ids = tasks.map((item) => item.id);
    [ids[index], ids[nextIndex]] = [ids[nextIndex], ids[index]];
    await run(() => api.reorderTasks(ids), setTasks);
  }

  async function submitSchedule(event: FormEvent) {
    event.preventDefault();
    if (!scheduleForm.title.trim()) return;
    await run(() => api.createSchedule(scheduleForm), async () => {
      setScheduleForm(emptyScheduleForm());
      setSchedule(await api.scheduleToday());
    });
  }

  function schedulePayload(item: ScheduleItem, patch: Partial<Omit<ScheduleItem, 'id'>> = {}): Omit<ScheduleItem, 'id'> {
    return {
      title: patch.title ?? item.title,
      startAt: patch.startAt ?? item.startAt,
      endAt: patch.endAt ?? item.endAt,
      status: patch.status ?? item.status,
      source: patch.source ?? item.source,
      taskId: patch.taskId ?? item.taskId,
      notes: patch.notes ?? item.notes
    };
  }

  function editSchedule(item: ScheduleItem) {
    setEditingScheduleId(item.id);
    setScheduleEditForm(schedulePayload(item));
  }

  async function saveScheduleEdit(event: FormEvent) {
    event.preventDefault();
    if (editingScheduleId == null || !scheduleEditForm.title.trim()) return;
    await run(() => api.updateSchedule(editingScheduleId, scheduleEditForm), async () => {
      setEditingScheduleId(null);
      await refreshSideData();
    });
  }

  async function patchScheduleStatus(item: ScheduleItem, status: ScheduleStatus) {
    await run(() => api.updateSchedule(item.id, schedulePayload(item, { status })), async () => refreshSideData());
  }

  async function recordInterruption(event: FormEvent) {
    event.preventDefault();
    await run(() => api.recordInterruption(interruptionNote), async (recorded) => {
      setInterruptionNote('');
      setScheduleNotice(`已记录打断：${recorded.note}`);
      await refreshSideData();
    });
  }

  async function saveSettings(event: FormEvent) {
    event.preventDefault();
    await run(() => api.updateSettings(settingsDraft), (next) => {
      setSettings(next);
      setSettingsDraft(next);
      setSettingsOpen(false);
      if (timer?.status === 'NEW') {
        setLocalRemaining(modeDuration(next, selectedMode));
      }
    });
  }

  async function requestNotifications() {
    if (!('Notification' in window)) return;
    const permission = await Notification.requestPermission();
    if (permission === 'granted') {
      setSettingsDraft((draft) => ({ ...draft, notificationsEnabled: true }));
    }
  }

  function addAgentMessage(message: Omit<ChatMessage, 'id'>) {
    setAgentMessages((messages) => [...messages, { ...message, id: chatIdRef.current++ }]);
  }

  async function sendAgentMessage(event: FormEvent) {
    event.preventDefault();
    const question = agentInput.trim();
    if (!question) return;
    const mode = agentMode;
    addAgentMessage({ role: 'user', text: question });
    setAgentInput('');
    setBusy(true);
    setError(null);
    try {
      if (mode === 'PLAN') {
        const nextPlan = await api.plan(question);
        addAgentMessage({
          role: 'agent',
          text: nextPlan.advice,
          plan: nextPlan,
          warnings: nextPlan.warnings
        });
      } else {
        const nextAdvice = await api.advice(question);
        addAgentMessage({
          role: 'agent',
          text: nextAdvice.advice,
          warnings: nextAdvice.warnings
        });
      }
    } catch (ex) {
      const message = ex instanceof Error ? ex.message : '小茄暂时没接上，请稍后再试。';
      setError(message);
      addAgentMessage({ role: 'agent', text: message });
    } finally {
      setBusy(false);
    }
  }

  async function applyChatPlan(plan: AgentPlan, blockIndexes?: number[]) {
    const preview = await api.previewPlan(plan.draftId);
    const selected = blockIndexes == null || blockIndexes.length === 0
      ? preview.blocks
      : preview.blocks.filter((block) => blockIndexes.includes(block.index));
    const conflicts = selected.filter((block) => block.conflict);
    if (conflicts.length > 0) {
      addAgentMessage({
        role: 'agent',
        text: conflicts.map((block) => block.conflictMessage ?? `${block.block.title} 时间冲突`).join('；')
      });
      return;
    }
    await run(() => api.applyPlan(plan.draftId, blockIndexes), async (items) => {
      setSchedule(await api.scheduleToday());
      addAgentMessage({ role: 'agent', text: `已加入 ${items.length} 个时间块，到点后我会提醒你。` });
    });
  }

  async function rejectChatPlan(plan: AgentPlan) {
    await run(() => api.rejectPlan(plan.draftId), () => {
      addAgentMessage({ role: 'agent', text: `已拒绝「${plan.title}」，我不会把它加入今日安排。` });
    });
  }

  function switchPage(page: AppPage) {
    setActivePage(page);
    setAgentOpen(false);
    setTimeMasterRationaleOpen(false);
  }

  function answerTimeMasterQuestion(id: keyof TimeMasterHabits, value: string) {
    setTimeMasterHabits((current) => ({ ...current, [id]: value }));
    setTimeMasterStep((step) => Math.min(step + 1, timeMasterQuestions.length));
  }

  function resetTimeMaster() {
    setTimeMasterStep(0);
    setTimeMasterHabits({});
    setTimeMasterForm(defaultTimeMasterForm());
    setTimeMasterPlan(null);
    setSelectedTimeMasterPhase(0);
    setTimeMasterRationaleOpen(false);
  }

  async function submitTimeMasterTask(event: FormEvent) {
    event.preventDefault();
    try {
      setBusy(true);
      setError(null);
      const nextPlan = await api.timeMasterPlan({
        ...timeMasterForm,
        habits: resolveTimeMasterHabits(timeMasterHabits)
      });
      setTimeMasterPlan(nextPlan);
      setSelectedTimeMasterPhase(0);
      setTimeMasterRationaleOpen(false);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : '时间管理大师生成失败');
    } finally {
      setBusy(false);
    }
  }

  async function addTimeMasterDayToSchedule(day: TimeMasterDailyPlan) {
    const { startAt, endAt } = scheduleTimesForDailyPlan(day);
    await run(
      () =>
        api.createSchedule({
          title: day.scheduleTitle,
          startAt,
          endAt,
          status: 'PLANNED',
          source: 'AGENT',
          taskId: null,
          notes: day.scheduleNotes
        }),
      async () => {
        setScheduleNotice(`${day.date} 已加入首页时间安排`);
        setSchedule(await api.scheduleToday());
      }
    );
  }

  function forecastChartPoints(points: TimeMasterPlan['forecast'], width = 620, height = 170) {
    const total = Math.max(1, points.length - 1);
    return points
      .map((point, index) => {
        const x = 20 + (index / total) * width;
        const y = 20 + height - (point.value / 100) * height;
        return {
          ...point,
          x: Math.round(x),
          y: Math.round(y)
        };
      });
  }

  function forecastPolyline(points: TimeMasterPlan['forecast'], width = 620, height = 170) {
    return forecastChartPoints(points, width, height)
      .map((point) => `${point.x},${point.y}`)
      .join(' ');
  }

  function forecastLabels(points: TimeMasterPlan['forecast']) {
    if (points.length <= 6) return forecastChartPoints(points);
    const last = points.length - 1;
    const indexes = new Set([0, Math.round(last * 0.25), Math.round(last * 0.5), Math.round(last * 0.75), last]);
    return forecastChartPoints(points).filter((_, index) => indexes.has(index));
  }

  function formatForecastDate(value: string) {
    const [, month, day] = value.split('-');
    return month && day ? `${month}/${day}` : value;
  }

  function renderTimeMasterPage() {
    if (!timeMasterPlan && timeMasterStep < timeMasterQuestions.length) {
      const question = timeMasterQuestions[timeMasterStep];
      const progress = Math.round(((timeMasterStep + 1) / (timeMasterQuestions.length + 1)) * 100);
      return (
        <section className="time-master-page">
          <div className="tm-progress" aria-label="生成进度">
            <span><i style={{ width: `${progress}%` }} /></span>
            <strong>{progress}%</strong>
          </div>
          <section className="tm-question-card">
            <p>{question.eyebrow}</p>
            <h2>{question.title}</h2>
            <div className="tm-option-grid">
              {question.options.map((option) => (
                <button key={option.value} type="button" onClick={() => answerTimeMasterQuestion(question.id, option.value)}>
                  <strong>{option.label}</strong>
                  <span>{option.description}</span>
                </button>
              ))}
            </div>
            {timeMasterStep > 0 && (
              <button className="command compact" type="button" onClick={() => setTimeMasterStep((step) => Math.max(0, step - 1))}>
                上一步
              </button>
            )}
          </section>
        </section>
      );
    }

    if (!timeMasterPlan) {
      return (
        <section className="time-master-page">
          <form className="tm-task-form" onSubmit={submitTimeMasterTask}>
            <div>
              <p>任务信息</p>
              <h2>把长期目标交给时间管理大师拆解</h2>
            </div>
            <label>
              任务名称
              <input
                value={timeMasterForm.taskTitle}
                onChange={(event) => setTimeMasterForm({ ...timeMasterForm, taskTitle: event.target.value })}
              />
            </label>
            <label>
              任务内容
              <textarea
                value={timeMasterForm.taskContent}
                onChange={(event) => setTimeMasterForm({ ...timeMasterForm, taskContent: event.target.value })}
              />
            </label>
            <div className="tm-form-grid">
              <label>
                开始日期
                <input
                  type="date"
                  value={timeMasterForm.startDate}
                  onChange={(event) => setTimeMasterForm({ ...timeMasterForm, startDate: event.target.value })}
                />
              </label>
              <label>
                结束日期
                <input
                  type="date"
                  value={timeMasterForm.endDate}
                  onChange={(event) => setTimeMasterForm({ ...timeMasterForm, endDate: event.target.value })}
                />
              </label>
              <label>
                每日可投入分钟
                <input
                  type="number"
                  min={25}
                  max={360}
                  value={timeMasterForm.dailyMinutes}
                  onChange={(event) => setTimeMasterForm({ ...timeMasterForm, dailyMinutes: Number(event.target.value) })}
                />
              </label>
            </div>
            <div className="tm-task-actions">
              <button className="command" type="button" onClick={() => setTimeMasterStep(timeMasterQuestions.length - 1)}>
                上一步
              </button>
              <button className="command primary" disabled={busy}>
                {busy ? <Loader2 className="spin" size={17} /> : <WandSparkles size={17} />}
                <span>{busy ? '生成中' : '生成资料卡片'}</span>
              </button>
            </div>
          </form>
        </section>
      );
    }

    const selectedPhase = timeMasterPlan.phases[selectedTimeMasterPhase] ?? timeMasterPlan.phases[0];
    const chartLabelPoints = forecastLabels(timeMasterPlan.forecast);
    return (
      <section className="time-master-page tm-plan-page">
        <section className="tm-plan-hero">
          <div>
            <p>资料卡片</p>
            <h2>{timeMasterPlan.title}</h2>
            <span>{timeMasterPlan.summary}</span>
            <div className="tm-plan-metrics">
              <strong>{timeMasterPlan.totalDays} 天周期</strong>
              <strong>每日 {timeMasterPlan.dailyMinutes} 分钟</strong>
              <strong>{timeMasterPlan.phases.length} 个阶段</strong>
            </div>
          </div>
          <button className="tm-tomato-agent" type="button" aria-label="番茄规划 agent" onClick={() => setTimeMasterRationaleOpen(true)}>
            <span className="tm-tomato-leaf" />
            <span className="tm-tomato-eye left" />
            <span className="tm-tomato-eye right" />
            <span className="tm-tomato-mouth" />
          </button>
        </section>

        <section className="tm-chart-panel">
          <div>
            <p>线性提升预测</p>
            <h2>阶段越靠后，输出占比越高</h2>
          </div>
          <div className="tm-chart-wrap">
            <svg viewBox="0 0 660 244" role="img" aria-label="线性提升曲线，包含日期和完成度百分比">
              <polyline
                className="tm-forecast-line"
                points={forecastPolyline(timeMasterPlan.forecast)}
                pathLength={1}
                fill="none"
                stroke="#e34f46"
                strokeWidth="4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              {forecastChartPoints(timeMasterPlan.forecast).map((point) => (
                <circle className="tm-forecast-dot" key={`${point.date}-${point.value}`} cx={point.x} cy={point.y} r="4.8" />
              ))}
              {chartLabelPoints.map((point, index) => {
                const isLast = index === chartLabelPoints.length - 1;
                const labelY = isLast && point.y < 60 ? point.y + 48 : point.y < 48 ? point.y + 30 : point.y - 18;
                const anchor = index === 0 ? 'start' : isLast ? 'end' : 'middle';
                return (
                  <g className="tm-forecast-label" key={`${point.date}-${point.value}-label`}>
                    <text x={point.x} y={labelY} textAnchor={anchor}>
                      {formatForecastDate(String(point.date))} · {point.value}%
                    </text>
                  </g>
                );
              })}
            </svg>
            <div className="tm-chart-data-strip" aria-label="线性提升预测数据">
              {timeMasterPlan.forecast.map((point) => (
                <span key={`${point.date}-${point.day}-${point.value}`}>
                  <strong>{formatForecastDate(String(point.date))}</strong>
                  <em>{point.value}%</em>
                </span>
              ))}
            </div>
          </div>
        </section>

        <section className="tm-phase-grid">
          {timeMasterPlan.phases.map((phase, index) => (
            <button
              type="button"
              key={phase.id}
              className={selectedTimeMasterPhase === index ? 'active' : ''}
              onClick={() => setSelectedTimeMasterPhase(index)}
            >
              <strong>{phase.name}</strong>
              <span>{phase.startDate} - {phase.endDate}</span>
              <small>{phase.objective}</small>
              <svg viewBox="0 0 180 58" aria-hidden="true">
                <polyline points={forecastPolyline(phase.forecast, 142, 34)} fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
              </svg>
            </button>
          ))}
        </section>

        <section className="tm-day-panel">
          <div className="tm-day-panel-head">
            <div>
              <p>{selectedPhase.startDate} - {selectedPhase.endDate}</p>
              <h2>{selectedPhase.name}</h2>
            </div>
            <button className="command compact" type="button" onClick={resetTimeMaster}>
              重新生成
            </button>
          </div>
          <div className="tm-day-list">
            {selectedPhase.dailyPlans.map((day) => (
              <article className="tm-day-item" key={`${selectedPhase.id}-${day.date}`}>
                <div>
                  <strong>{day.date}</strong>
                  <span>{day.title}</span>
                  <small>{day.timeBlock} · {day.focusMinutes} 分钟</small>
                </div>
                <ul>
                  {day.checklist.map((item) => <li key={item}>{item}</li>)}
                </ul>
                <button className="command compact" type="button" onClick={() => addTimeMasterDayToSchedule(day)} disabled={busy}>
                  <Plus size={16} />
                  <span>加入首页安排</span>
                </button>
              </article>
            ))}
          </div>
        </section>

        {timeMasterRationaleOpen && (
          <aside className="tm-rationale-dialog" aria-label="规划原因">
            <button className="tm-rationale-backdrop" type="button" aria-label="关闭规划原因" onClick={() => setTimeMasterRationaleOpen(false)} />
            <section>
              <div className="tm-tomato-agent static" aria-hidden="true">
                <span className="tm-tomato-leaf" />
                <span className="tm-tomato-eye left" />
                <span className="tm-tomato-eye right" />
                <span className="tm-tomato-mouth" />
              </div>
              <h2>为什么这样规划</h2>
              <p>{timeMasterPlan.rationale}</p>
              <button className="command primary" type="button" onClick={() => setTimeMasterRationaleOpen(false)}>
                知道了
              </button>
            </section>
          </aside>
        )}
      </section>
    );
  }

  if (loading) {
    return (
      <main className="loading">
        <Loader2 className="spin" />
        <span>Fanqie</span>
      </main>
    );
  }

  return (
    <main className={`app-shell app-mode-${displayMode.toLowerCase().replace('_', '-')} theme-${settings.theme}`}>
      <header className="topbar">
        <div className="brand">
          <span className="tomato-mark" aria-hidden="true" />
          <div>
            <h1>Fanqie</h1>
          </div>
        </div>
        <nav className="main-nav" aria-label="主导航">
          <button className={activePage === 'HOME' ? 'active' : ''} type="button" onClick={() => switchPage('HOME')}>
            首页
          </button>
          <button className={activePage === 'TIME_MASTER' ? 'active' : ''} type="button" onClick={() => switchPage('TIME_MASTER')}>
            时间管理大师
          </button>
        </nav>
        <div className="top-actions">
          <button className="icon-button" title="通知" onClick={requestNotifications}>
            <Bell size={18} />
          </button>
          <button className="icon-button" title="设置" onClick={() => setSettingsOpen(true)}>
            <SettingsIcon size={18} />
          </button>
        </div>
      </header>

      {error && (
        <div className="toast" role="alert">
          <span>{error}</span>
          <button title="关闭" onClick={() => setError(null)}>
            <X size={16} />
          </button>
        </div>
      )}

      {scheduleNotice && (
        <div className="schedule-alert" role="status">
          <Volume2 size={18} />
          <span>{scheduleNotice}</span>
          <button title="关闭时间提醒" onClick={() => setScheduleNotice(null)}>
            <X size={16} />
          </button>
        </div>
      )}

      {activePage === 'HOME' ? (
        <>
      <section className="focus-stack">
        <section className="panel timer-panel">
          <div className="mode-tabs" role="tablist">
            {(['POMODORO', 'SHORT_BREAK', 'LONG_BREAK'] as TimerMode[]).map((mode) => (
              <button
                key={mode}
                className={displayMode === mode ? 'active' : ''}
                onClick={() => chooseMode(mode)}
                disabled={!canChangeMode}
              >
                {modeLabels[mode]}
              </button>
            ))}
          </div>

          <div className={`timer-ring mode-${displayMode.toLowerCase().replace('_', '-')}`} style={ringStyle}>
            <div className="timer-core">
              <span>{modeLabels[displayMode]}</span>
              <strong>{formatSeconds(localRemaining)}</strong>
              <small>{timerHint}</small>
            </div>
          </div>

          <div className="timer-controls">
            <button className="command primary" onClick={toggleTimer} disabled={busy}>
              {timer?.status === 'PLAYING' ? <Pause size={18} /> : <Play size={18} />}
              <span>{timer?.status === 'PLAYING' ? '暂停' : '开始'}</span>
            </button>
            <button className="command" onClick={resetTimer} disabled={busy}>
              <RotateCcw size={18} />
              <span>重置</span>
            </button>
            <button className="command" onClick={skipTimer} disabled={busy}>
              <SkipForward size={18} />
              <span>跳过</span>
            </button>
          </div>

          <form className="interruption-form" onSubmit={recordInterruption}>
            <input
              value={interruptionNote}
              onChange={(event) => setInterruptionNote(event.target.value)}
              placeholder="打断备注"
            />
            <button className="command compact" disabled={busy}>
              <AlertCircle size={16} />
              <span>记录打断</span>
            </button>
          </form>

          <div className="metrics">
            <div>
              <span>今日番茄</span>
              <strong>{stats?.completedPomodorosToday ?? timer?.completedPomodorosToday ?? 0}</strong>
            </div>
            <div>
              <span>未完成</span>
              <strong>{stats?.unfinishedTasks ?? tasks.filter((task) => !task.done).length}</strong>
            </div>
            <div>
              <span>干扰</span>
              <strong>{stats?.interruptionsToday ?? 0}</strong>
            </div>
          </div>
          {statsTrend && (
            <div className="trend-strip" aria-label="最近 7 天趋势">
              {statsTrend.days.map((day) => (
                <span key={day.date} title={`${day.date} · ${day.focusMinutes} 分钟`}>
                  {day.completedPomodoros}
                </span>
              ))}
            </div>
          )}
          {interruptions.length > 0 && (
            <div className="interruption-list">
              {interruptions.slice(0, 3).map((item) => (
                <span key={item.id}>{formatClock(item.occurredAt)} · {item.note}</span>
              ))}
            </div>
          )}
        </section>

        <section className="work-lane">
          <section className="panel tasks-panel">
            <div className="panel-title">
              <ListChecks size={19} />
              <h2>任务管理</h2>
            </div>
            <form className="task-form" onSubmit={submitTask}>
              <input value={newTaskTitle} onChange={(event) => setNewTaskTitle(event.target.value)} placeholder="输入任务" />
              <input
                type="number"
                min={0}
                max={100}
                value={newTaskPomodoros}
                onChange={(event) => setNewTaskPomodoros(Number(event.target.value))}
                aria-label="预计番茄数"
              />
              <button className="icon-button strong" title="新增任务" disabled={busy}>
                <Plus size={18} />
              </button>
            </form>
            <div className="task-list">
              {tasks.map((task) => (
                <article className={`task-row ${task.active ? 'current' : ''}`} key={task.id}>
                  <button className="check-button" title="完成" onClick={() => patchTask(task.id, { done: !task.done })}>
                    {task.done && <Check size={16} />}
                  </button>
                  <input
                    value={task.title}
                    className={task.done ? 'done' : ''}
                    onChange={(event) => patchTask(task.id, { title: event.target.value })}
                  />
                  <div className="pomodoro-count">
                    <span>{task.completedPomodoros}/{task.estimatedPomodoros}</span>
                    <span className="mini-tomato" />
                  </div>
                  <div className="row-actions">
                    <button title="设为当前" onClick={() => run(() => api.setActiveTask(task.id), refreshSideData)}>
                      <Target size={16} />
                    </button>
                    <button title="上移" onClick={() => moveTask(task, -1)}>
                      <ChevronUp size={16} />
                    </button>
                    <button title="下移" onClick={() => moveTask(task, 1)}>
                      <ChevronDown size={16} />
                    </button>
                    <button title="删除" onClick={() => run(() => api.deleteTask(task.id), refreshSideData)}>
                      <Trash2 size={16} />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>

          <section className="panel schedule-panel">
            <div className="panel-title">
              <CalendarClock size={19} />
              <h2>时间安排</h2>
            </div>
            <div className="timeline">
              {schedule.length === 0 && <p className="empty-state">今天还没有时间块。</p>}
              {schedule.map((item) => (
                <article className={`timeline-item status-${item.status.toLowerCase().replace('_', '-')}`} key={item.id}>
                  <time>{formatTimeRange(item.startAt, item.endAt)}</time>
                  {editingScheduleId === item.id ? (
                    <form className="timeline-edit" onSubmit={saveScheduleEdit}>
                      <input value={scheduleEditForm.title} onChange={(event) => setScheduleEditForm({ ...scheduleEditForm, title: event.target.value })} />
                      <input type="datetime-local" value={scheduleEditForm.startAt} onChange={(event) => setScheduleEditForm({ ...scheduleEditForm, startAt: event.target.value })} />
                      <input type="datetime-local" value={scheduleEditForm.endAt} onChange={(event) => setScheduleEditForm({ ...scheduleEditForm, endAt: event.target.value })} />
                      <input value={scheduleEditForm.notes ?? ''} onChange={(event) => setScheduleEditForm({ ...scheduleEditForm, notes: event.target.value })} placeholder="备注" />
                      <button title="保存安排">
                        <Save size={15} />
                      </button>
                      <button type="button" title="取消编辑" onClick={() => setEditingScheduleId(null)}>
                        <X size={15} />
                      </button>
                    </form>
                  ) : (
                    <>
                      <div>
                        <strong>{item.title}</strong>
                        <span>
                          {durationLabel(item.startAt, item.endAt)} · {item.source === 'AGENT' ? '小茄生成' : '手动加入'} · {statusLabels[item.status]}
                        </span>
                        {item.notes && <small>{item.notes}</small>}
                      </div>
                      <button title="编辑安排" onClick={() => editSchedule(item)}>
                        <Save size={15} />
                      </button>
                      <button title="完成安排" onClick={() => patchScheduleStatus(item, 'DONE')}>
                        <Check size={15} />
                      </button>
                      <button title="跳过安排" onClick={() => patchScheduleStatus(item, 'SKIPPED')}>
                        <SkipForward size={15} />
                      </button>
                      <button title="删除安排" onClick={() => run(() => api.deleteSchedule(item.id), refreshSideData)}>
                        <Trash2 size={15} />
                      </button>
                    </>
                  )}
                </article>
              ))}
            </div>
            <form className="schedule-form" onSubmit={submitSchedule}>
              <input value={scheduleForm.title} onChange={(event) => setScheduleForm({ ...scheduleForm, title: event.target.value })} placeholder="安排标题" />
              <label className="time-field">
                <span>开始</span>
                <input type="datetime-local" value={scheduleForm.startAt} onChange={(event) => setScheduleForm({ ...scheduleForm, startAt: event.target.value })} />
              </label>
              <label className="time-field">
                <span>结束</span>
                <input type="datetime-local" value={scheduleForm.endAt} onChange={(event) => setScheduleForm({ ...scheduleForm, endAt: event.target.value })} />
              </label>
              <button className="command compact">
                <Plus size={16} />
                <span>加入</span>
              </button>
            </form>
            <div className="range-preview">
              <Clock3 size={16} />
              <span>已选 {formatTimeRange(scheduleForm.startAt, scheduleForm.endAt)} · {durationLabel(scheduleForm.startAt, scheduleForm.endAt)}</span>
            </div>
          </section>
        </section>
      </section>

      <div className={`agent-dock ${agentOpen ? 'hidden' : ''}`}>
        <div className="mascot-bubble">{mascotGreeting}</div>
        <button className="mascot-button" aria-label="打开小茄聊天" title="打开小茄聊天" onClick={() => setAgentOpen(true)}>
          <span className="tomato-chibi" aria-hidden="true">
            <span className="tomato-shadow" />
            <span className="tomato-leaf crown" />
            <span className="tomato-leaf left" />
            <span className="tomato-leaf right" />
            <span className="tomato-body">
              <span className="tomato-cheek left" />
              <span className="tomato-cheek right" />
              <span className="tomato-eye left" />
              <span className="tomato-eye right" />
              <span className="tomato-mouth" />
              <span className="tomato-sparkle" />
            </span>
            <span className="tomato-scarf" />
            <span className="tomato-arm left" />
            <span className="tomato-arm right" />
            <span className="tomato-foot left" />
            <span className="tomato-foot right" />
          </span>
        </button>
      </div>

      {agentOpen && (
        <aside className="agent-chat" aria-label="小茄聊天">
          <div className="chat-head">
            <div>
              <Bot size={18} />
              <strong>小茄</strong>
            </div>
            <button title="收起小茄" onClick={() => setAgentOpen(false)}>
              <Minimize2 size={17} />
            </button>
          </div>
          <div className="agent-mode-switch" aria-label="小茄模式">
            <button className={agentMode === 'ADVICE' ? 'active' : ''} onClick={() => setAgentMode('ADVICE')}>
              <MessageCircle size={16} />
              <span>建议</span>
            </button>
            <button className={agentMode === 'PLAN' ? 'active' : ''} onClick={() => setAgentMode('PLAN')}>
              <WandSparkles size={16} />
              <span>规划</span>
            </button>
          </div>
          <div className="chat-messages">
            {agentMessages.map((message) => (
              <article className={`chat-message ${message.role}`} key={message.id}>
                <p>{message.text}</p>
                {message.warnings && message.warnings.length > 0 && <small>{message.warnings.join('；')}</small>}
                {message.plan && (() => {
                  const currentPlan = message.plan;
                  return (
                    <div className="plan-preview">
                      <div className="plan-head">
                        <strong>{currentPlan.title}</strong>
                        <div className="plan-actions">
                          <button onClick={() => applyChatPlan(currentPlan)} className="command compact">
                            <Check size={16} />
                            <span>全部加入</span>
                          </button>
                          <button onClick={() => rejectChatPlan(currentPlan)} className="command compact">
                            <X size={16} />
                            <span>拒绝</span>
                          </button>
                        </div>
                      </div>
                      {currentPlan.blocks.length === 0 && <span className="empty-state">小茄没有生成可加入的时间块。</span>}
                      {currentPlan.blocks.map((block: ScheduleBlock, index) => (
                        <div className="plan-block" key={`${block.title}-${block.startAt}`}>
                          <span>{formatTimeRange(block.startAt, block.endAt)}</span>
                          <strong>{block.title}</strong>
                          <button onClick={() => applyChatPlan(currentPlan, [index])}>加入</button>
                        </div>
                      ))}
                    </div>
                  );
                })()}
              </article>
            ))}
          </div>
          <form className="chat-form" onSubmit={sendAgentMessage}>
            <textarea
              value={agentInput}
              onChange={(event) => setAgentInput(event.target.value)}
              placeholder="告诉小茄你的目标、截止时间或卡住的地方"
            />
            <button className="command primary" disabled={busy || !agentInput.trim()}>
              {busy ? <Loader2 className="spin" size={17} /> : <Send size={17} />}
              <span>发送</span>
            </button>
          </form>
        </aside>
      )}

        </>
      ) : (
        renderTimeMasterPage()
      )}

      {settingsOpen && (
        <aside className="settings-drawer" aria-label="设置">
          <form className="settings-sheet" onSubmit={saveSettings}>
            <div className="sheet-head">
              <h2>设置</h2>
              <button type="button" title="关闭" onClick={() => setSettingsOpen(false)}>
                <X size={18} />
              </button>
            </div>
            <label>
              番茄分钟
              <input type="number" min={1} max={180} value={settingsDraft.workMinutes} onChange={(event) => setSettingsDraft({ ...settingsDraft, workMinutes: Number(event.target.value) })} />
            </label>
            <label>
              短休息
              <input type="number" min={1} max={180} value={settingsDraft.shortBreakMinutes} onChange={(event) => setSettingsDraft({ ...settingsDraft, shortBreakMinutes: Number(event.target.value) })} />
            </label>
            <label>
              长休息
              <input type="number" min={1} max={180} value={settingsDraft.longBreakMinutes} onChange={(event) => setSettingsDraft({ ...settingsDraft, longBreakMinutes: Number(event.target.value) })} />
            </label>
            <label>
              长休息间隔
              <input type="number" min={1} max={12} value={settingsDraft.longBreakInterval} onChange={(event) => setSettingsDraft({ ...settingsDraft, longBreakInterval: Number(event.target.value) })} />
            </label>
            <label>
              主题
              <select value={settingsDraft.theme} onChange={(event) => setSettingsDraft({ ...settingsDraft, theme: event.target.value })}>
                <option value="system">跟随系统</option>
                <option value="light">浅色</option>
                <option value="dark">深色</option>
              </select>
            </label>
            <label>
              提醒音
              <select value={settingsDraft.alarmSound} onChange={(event) => setSettingsDraft({ ...settingsDraft, alarmSound: event.target.value })}>
                <option value="simple-notification">清脆提示</option>
                <option value="soft-bell">柔和铃声</option>
                <option value="deep-chime">低音钟声</option>
              </select>
            </label>
            <label>
              音量
              <input type="range" min={0} max={100} value={settingsDraft.alarmVolume} onChange={(event) => setSettingsDraft({ ...settingsDraft, alarmVolume: Number(event.target.value) })} />
            </label>
            <button className="command settings-sound-button" type="button" title="测试通知音" onClick={testAlarmSound}>
              <Volume2 size={17} />
              <span>测试通知音</span>
            </button>
            <label className="toggle-row">
              <input type="checkbox" checked={settingsDraft.notificationsEnabled} onChange={(event) => setSettingsDraft({ ...settingsDraft, notificationsEnabled: event.target.checked })} />
              通知
            </label>
            <label className="toggle-row">
              <input type="checkbox" checked={settingsDraft.alarmRepeat} onChange={(event) => setSettingsDraft({ ...settingsDraft, alarmRepeat: event.target.checked })} />
              重复提醒
            </label>
            <button className="command primary">
              <Check size={17} />
              <span>保存</span>
            </button>
          </form>
        </aside>
      )}
    </main>
  );
}
