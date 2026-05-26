import {
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
import type {
  AgentPlan,
  ScheduleBlock,
  ScheduleItem,
  ScheduleStatus,
  Settings,
  Stats,
  Task,
  TimerMode,
  TimerState
} from './types';

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
  const [settings, setSettings] = useState<Settings>(defaultSettings);
  const [settingsDraft, setSettingsDraft] = useState<Settings>(defaultSettings);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [timer, setTimer] = useState<TimerState | null>(null);
  const [schedule, setSchedule] = useState<ScheduleItem[]>([]);
  const [stats, setStats] = useState<Stats | null>(null);
  const [selectedMode, setSelectedMode] = useState<TimerMode>('POMODORO');
  const [localRemaining, setLocalRemaining] = useState(defaultSettings.workMinutes * 60);
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [newTaskPomodoros, setNewTaskPomodoros] = useState(1);
  const [scheduleForm, setScheduleForm] = useState<Omit<ScheduleItem, 'id'>>(emptyScheduleForm);
  const [scheduleNotice, setScheduleNotice] = useState<string | null>(null);
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
    const [nextSettings, nextTasks, nextTimer, nextSchedule, nextStats] = await Promise.all([
      api.settings(),
      api.tasks(),
      api.timer(),
      api.scheduleToday(),
      api.stats()
    ]);
    setSettings(nextSettings);
    setSettingsDraft(nextSettings);
    setTasks(nextTasks);
    setTimer(nextTimer);
    setSelectedMode(nextTimer.mode);
    setLocalRemaining(nextTimer.remainingSeconds);
    setSchedule(nextSchedule);
    setStats(nextStats);
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
    const [nextTasks, nextSchedule, nextStats] = await Promise.all([api.tasks(), api.scheduleToday(), api.stats()]);
    setTasks(nextTasks);
    setSchedule(nextSchedule);
    setStats(nextStats);
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
    for (let i = 0; i < repeat; i += 1) {
      [0, 0.18].forEach((offset, index) => {
        const oscillator = ctx.createOscillator();
        const gain = ctx.createGain();
        const start = ctx.currentTime + i * 0.58 + offset;
        oscillator.type = 'square';
        oscillator.frequency.setValueAtTime(index === 0 ? 1046 : 784, start);
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
    await run(() => api.applyPlan(plan.draftId, blockIndexes), async (items) => {
      setSchedule(await api.scheduleToday());
      addAgentMessage({ role: 'agent', text: `已加入 ${items.length} 个时间块，到点后我会提醒你。` });
    });
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
    <main className={`app-shell app-mode-${displayMode.toLowerCase().replace('_', '-')}`}>
      <header className="topbar">
        <div className="brand">
          <span className="tomato-mark" aria-hidden="true" />
          <div>
            <h1>Fanqie</h1>
          </div>
        </div>
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
                  <div>
                    <strong>{item.title}</strong>
                    <span>
                      {durationLabel(item.startAt, item.endAt)} · {item.source === 'AGENT' ? '小茄生成' : '手动加入'} · {statusLabels[item.status]}
                    </span>
                    {item.notes && <small>{item.notes}</small>}
                  </div>
                  <button title="删除安排" onClick={() => run(() => api.deleteSchedule(item.id), refreshSideData)}>
                    <Trash2 size={15} />
                  </button>
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
                        <button onClick={() => applyChatPlan(currentPlan)} className="command compact">
                          <Check size={16} />
                          <span>全部加入</span>
                        </button>
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
