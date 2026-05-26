import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../src/App';

const settings = {
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

const timer = {
  mode: 'POMODORO',
  status: 'NEW',
  totalSeconds: 1500,
  remainingSeconds: 1500,
  sessionId: null,
  activeTaskId: 1,
  activeTaskTitle: '写代码',
  completedPomodorosToday: 0,
  hint: '准备开始'
};

const stats = {
  completedPomodorosToday: 0,
  sessionsToday: 0,
  interruptionsToday: 0,
  unfinishedTasks: 1,
  recentSessions: []
};

function json(value: unknown) {
  return Promise.resolve(new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        const url = input.toString();
        const method = init?.method ?? 'GET';
        if (url === '/api/settings') return json(settings);
        if (url === '/api/timer') return json(timer);
        if (url === '/api/stats/today') return json(stats);
        if (url === '/api/schedule/today') return json([]);
        if (url === '/api/tasks' && method === 'GET') {
          return json([
            {
              id: 1,
              title: '写代码',
              estimatedPomodoros: 2,
              completedPomodoros: 0,
              done: false,
              active: true,
              sortOrder: 0,
              createdAt: '2026-05-26T10:00:00',
              updatedAt: '2026-05-26T10:00:00'
            }
          ]);
        }
        if (url === '/api/tasks' && method === 'POST') {
          return json({
            id: 2,
            title: '写测试',
            estimatedPomodoros: 1,
            completedPomodoros: 0,
            done: false,
            active: false,
            sortOrder: 1,
            createdAt: '2026-05-26T10:01:00',
            updatedAt: '2026-05-26T10:01:00'
          });
        }
        return Promise.resolve(new Response('{}', { status: 200 }));
      })
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders timer, task, and stats from the API', async () => {
    render(<App />);

    expect(await screen.findByText('25:00')).toBeInTheDocument();
    expect(screen.getAllByDisplayValue('写代码')[0]).toBeInTheDocument();
    expect(screen.queryByText('写代码')).not.toBeInTheDocument();
    expect(screen.getByText('今日番茄')).toBeInTheDocument();
  });

  it('posts a new task from the task form', async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByText('25:00');
    await user.type(screen.getByPlaceholderText('输入任务'), '写测试');
    await user.click(screen.getByTitle('新增任务'));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith('/api/tasks', expect.objectContaining({ method: 'POST' }));
    });
  });

  it('switches the idle timer mode before starting', async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await screen.findByText('25:00');
    await user.click(screen.getByRole('button', { name: '长休息' }));

    expect(screen.getByText('15:00')).toBeInTheDocument();
    expect(screen.getByText('长休息准备开始')).toBeInTheDocument();
    expect(container.querySelector('.app-mode-long-break')).toBeInTheDocument();
  });

  it('opens Xiao Qie chat and exposes a horizontal notification sound test', async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await screen.findByText('25:00');
    await user.click(screen.getByLabelText('打开小茄聊天'));
    expect(screen.getByText(/我是小茄/)).toBeInTheDocument();
    expect(container.querySelector('.tomato-chibi')).toBeInTheDocument();

    await user.click(screen.getByTitle('设置'));
    const soundButton = screen.getByTitle('测试通知音');
    expect(soundButton).toHaveClass('settings-sound-button');
    await user.click(soundButton);
    expect(screen.getByText('通知音测试已播放。')).toBeInTheDocument();
  });
});
