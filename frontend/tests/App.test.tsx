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

const trend = {
  days: [
    { date: '2026-05-27', completedPomodoros: 1, sessions: 1, interruptions: 0, focusMinutes: 25 },
    { date: '2026-05-28', completedPomodoros: 0, sessions: 0, interruptions: 0, focusMinutes: 0 }
  ]
};

const mockScheduleItems: unknown[] = [];

function json(value: unknown) {
  return Promise.resolve(new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } }));
}

async function waitForHomeReady() {
  await screen.findByText('25:00');
  await waitFor(
    () => {
      expect(screen.queryByRole('dialog', { name: 'Fanqie 欢迎' })).not.toBeInTheDocument();
    },
    { timeout: 5000 }
  );
}

describe('App', () => {
  beforeEach(() => {
    mockScheduleItems.length = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        const url = input.toString();
        const method = init?.method ?? 'GET';
        if (url === '/api/settings') return json(settings);
        if (url === '/api/timer') return json(timer);
        if (url === '/api/stats/today') return json(stats);
        if (url === '/api/stats/range?days=7') return json(trend);
        if (url === '/api/schedule' && method === 'POST') {
          const item = JSON.parse(String(init?.body ?? '{}'));
          const created = {
            ...item,
            id: 99 + mockScheduleItems.length,
            status: 'PLANNED',
            source: item.source ?? 'AGENT'
          };
          mockScheduleItems.push(created);
          return json(created);
        }
        if (url === '/api/schedule/today') return json([]);
        if (url.startsWith('/api/schedule/upcoming')) return json([...mockScheduleItems]);
        if (url === '/api/interruptions/today') return json([]);
        if (url === '/api/interruptions' && method === 'POST') {
          return json({
            id: 1,
            note: '被会议打断',
            taskId: 1,
            taskTitle: '写代码',
            timerSessionId: null,
            occurredAt: '2026-05-28T16:30:00'
          });
        }
        if (url === '/api/agent/time-master' && method === 'POST') {
          return json({
            title: 'LLM 作品集长期资料卡',
            summary: '小茄根据早间深度工作习惯生成的周期规划。',
            totalDays: 14,
            dailyMinutes: 90,
            habits: {
              energy: 'morning',
              focusStyle: 'deep',
              restPattern: 'weekend-light',
              reviewPreference: 'weekly'
            },
            phases: [
              {
                id: 'calibrate',
                name: 'AI 拆出的启动阶段',
                startDate: '2026-06-01',
                endDate: '2026-06-03',
                objective: '先校准作品集范围和素材。',
                dailyPlans: [
                  {
                    date: '2026-06-01',
                    title: '筛选代表项目',
                    focusMinutes: 90,
                    timeBlock: '08:30-10:00',
                    checklist: ['列出候选项目', '选出三个代表案例'],
                    scheduleTitle: '作品集：筛选代表项目',
                    scheduleNotes: '来自 LLM 的第一天安排'
                  },
                  {
                    date: '2026-06-02',
                    title: '补齐项目说明',
                    focusMinutes: 90,
                    timeBlock: '09:00-10:30',
                    checklist: ['补 README', '整理截图'],
                    scheduleTitle: '作品集：补齐项目说明',
                    scheduleNotes: '来自 LLM 的第二天安排'
                  }
                ],
                forecast: [{ date: '2026-06-01', day: 1, value: 7, phaseId: 'calibrate' }]
              }
            ],
            forecast: [{ date: '2026-06-01', day: 1, value: 7, phaseId: 'calibrate' }],
            rationale: '先校准范围，再稳定推进，可以减少长期任务失焦。',
            warnings: []
          });
        }
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

  it('shows a progressive welcome card before entering the home page', async () => {
    const { container } = render(<App />);

    expect(await screen.findByRole('dialog', { name: 'Fanqie 欢迎' })).toBeInTheDocument();
    const copy = container.querySelector('.welcome-copy');
    const progress = screen.getByRole('progressbar', { name: '进入首页进度' });

    expect(copy).not.toHaveTextContent('安静的入口');
    expect(progress).toHaveAttribute('aria-valuenow', '0');

    await waitFor(() => {
      expect(copy?.textContent?.length ?? 0).toBeGreaterThan(8);
      expect(Number(progress.getAttribute('aria-valuenow'))).toBeGreaterThan(0);
    });
    expect(copy).not.toHaveTextContent('安静的入口');

    expect(await screen.findByText(/安静的入口/, undefined, { timeout: 3200 })).toBeInTheDocument();

    await waitFor(
      () => {
        expect(screen.queryByRole('dialog', { name: 'Fanqie 欢迎' })).not.toBeInTheDocument();
      },
      { timeout: 4500 }
    );

    expect(screen.getByRole('button', { name: '首页' })).toHaveClass('active');
    expect(screen.getByText('25:00')).toBeInTheDocument();
  });

  it('posts a new task from the task form', async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitForHomeReady();
    await user.type(screen.getByPlaceholderText('输入任务'), '写测试');
    await user.click(screen.getByTitle('新增任务'));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith('/api/tasks', expect.objectContaining({ method: 'POST' }));
    });
  });

  it('switches the idle timer mode before starting', async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await waitForHomeReady();
    await user.click(screen.getByRole('button', { name: '长休息' }));

    expect(screen.getByText('15:00')).toBeInTheDocument();
    expect(screen.getByText('长休息准备开始')).toBeInTheDocument();
    expect(container.querySelector('.app-mode-long-break')).toBeInTheDocument();
  });

  it('opens Xiao Qie chat and exposes a horizontal notification sound test', async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await waitForHomeReady();
    await user.click(screen.getByLabelText('打开小茄聊天'));
    expect(screen.getByText(/我是小茄/)).toBeInTheDocument();
    expect(container.querySelector('.tomato-chibi')).toBeInTheDocument();

    await user.click(screen.getByTitle('设置'));
    const soundButton = screen.getByTitle('测试通知音');
    expect(soundButton).toHaveClass('settings-sound-button');
    await user.click(soundButton);
    expect(screen.getByText('通知音测试已播放。')).toBeInTheDocument();
  });

  it('records an interruption from the timer panel', async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitForHomeReady();
    await user.type(screen.getByPlaceholderText('打断备注'), '被会议打断');
    await user.click(screen.getByRole('button', { name: '记录打断' }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith('/api/interruptions', expect.objectContaining({ method: 'POST' }));
    });
    expect(await screen.findByText(/已记录打断/)).toBeInTheDocument();
  });

  it('exposes theme, sound, and repeat reminder settings', async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitForHomeReady();
    await user.click(screen.getByTitle('设置'));

    expect(screen.getByLabelText('主题')).toBeInTheDocument();
    expect(screen.getByLabelText('提醒音')).toBeInTheDocument();
    expect(screen.getByLabelText('重复提醒')).toBeInTheDocument();
  });

  it('creates a time master plan and adds a daily plan to the home schedule', async () => {
    const user = userEvent.setup();
    render(<App />);

    await waitForHomeReady();
    await user.click(screen.getByRole('button', { name: '时间管理大师' }));

    expect(await screen.findByText('你通常哪段时间最适合处理难任务？')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /早上/ }));
    await user.click(screen.getByRole('button', { name: /整块深度/ }));
    await user.click(screen.getByRole('button', { name: /轻量维护/ }));
    await user.click(screen.getByRole('button', { name: /每周复盘/ }));

    await user.clear(screen.getByLabelText('任务名称'));
    await user.type(screen.getByLabelText('任务名称'), '完成作品集');
    await user.clear(screen.getByLabelText('任务内容'));
    await user.type(screen.getByLabelText('任务内容'), '整理项目、补文档、发布站点');
    await user.clear(screen.getByLabelText('开始日期'));
    await user.type(screen.getByLabelText('开始日期'), '2026-06-01');
    await user.clear(screen.getByLabelText('结束日期'));
    await user.type(screen.getByLabelText('结束日期'), '2026-06-14');
    await user.click(screen.getByRole('button', { name: '生成资料卡片' }));

    expect(await screen.findByText('LLM 作品集长期资料卡')).toBeInTheDocument();
    expect(screen.getAllByText('AI 拆出的启动阶段').length).toBeGreaterThan(0);
    expect(screen.getByText('线性提升预测')).toBeInTheDocument();
    expect(screen.getAllByText('06/01 · 7%').length).toBeGreaterThan(0);
    expect(screen.getAllByText('7%').length).toBeGreaterThan(0);
    expect(fetch).toHaveBeenCalledWith(
      '/api/agent/time-master',
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('完成作品集')
      })
    );

    await user.click(screen.getAllByRole('button', { name: '加入首页安排' })[1]);

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        '/api/schedule',
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('2026-06-02T09:00')
        })
      );
    });
    await user.click(screen.getByRole('button', { name: '首页' }));
    expect(await screen.findByText('06/02 · 09:00 - 10:30')).toBeInTheDocument();
    expect(screen.getByText('作品集：补齐项目说明')).toBeInTheDocument();
  }, 12000);

  it('animates the time master progress bar toward the next step', async () => {
    const user = userEvent.setup();
    const { container } = render(<App />);

    await waitForHomeReady();
    await user.click(screen.getByRole('button', { name: '时间管理大师' }));

    expect(await screen.findByText('你通常哪段时间最适合处理难任务？')).toBeInTheDocument();

    const progress = container.querySelector('.tm-progress');
    const fill = container.querySelector<HTMLElement>('.tm-progress i');

    expect(progress).toBeInTheDocument();
    expect(fill).toBeInTheDocument();
    expect(progress).not.toHaveTextContent('40%');
    expect(Number.parseFloat(fill?.style.width || '0')).toBeLessThan(40);

    await waitFor(
      () => {
        expect(progress).toHaveTextContent('20%');
        expect(Number.parseFloat(fill?.style.width || '0')).toBe(20);
      },
      { timeout: 2500 }
    );

    await user.click(screen.getByRole('button', { name: /早上/ }));

    expect(await screen.findByText('你更习惯怎样完成一段长期任务？')).toBeInTheDocument();
    expect(progress).not.toHaveTextContent('40%');
    expect(Number.parseFloat(fill?.style.width || '0')).toBeLessThan(40);

    await waitFor(
      () => {
        expect(progress).toHaveTextContent('40%');
        expect(Number.parseFloat(fill?.style.width || '0')).toBe(40);
      },
      { timeout: 2500 }
    );
  }, 12000);
});
