export type TimeMasterEnergy = 'morning' | 'afternoon' | 'evening';
export type TimeMasterFocusStyle = 'deep' | 'balanced' | 'fragmented';
export type TimeMasterRestPattern = 'weekend-light' | 'steady' | 'weekday-only';
export type TimeMasterReviewPreference = 'daily' | 'weekly' | 'milestone';

export interface TimeMasterHabits {
  energy: TimeMasterEnergy;
  focusStyle: TimeMasterFocusStyle;
  restPattern: TimeMasterRestPattern;
  reviewPreference: TimeMasterReviewPreference;
}

export interface TimeMasterInput {
  taskTitle: string;
  taskContent: string;
  startDate: string;
  endDate: string;
  dailyMinutes: number;
  habits: TimeMasterHabits;
}

export interface ForecastPoint {
  date: string;
  day: number;
  value: number;
  phaseId: string;
}

export interface TimeMasterDailyPlan {
  date: string;
  title: string;
  focusMinutes: number;
  timeBlock: string;
  checklist: string[];
  scheduleTitle: string;
  scheduleNotes: string;
}

export interface TimeMasterPhase {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  objective: string;
  dailyPlans: TimeMasterDailyPlan[];
  forecast: ForecastPoint[];
}

export interface TimeMasterPlan {
  title: string;
  summary: string;
  totalDays: number;
  dailyMinutes: number;
  habits: TimeMasterHabits;
  phases: TimeMasterPhase[];
  forecast: ForecastPoint[];
  rationale: string;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

const phaseLibrary = [
  {
    id: 'calibrate',
    name: '启动校准',
    objective: '明确任务边界，建立能坚持的第一步。',
    checklist: ['写清最终成果', '准备资料和环境', '完成一次低风险试做']
  },
  {
    id: 'build',
    name: '稳定推进',
    objective: '把主要工作拆到每天，持续产出可见进度。',
    checklist: ['推进核心任务', '记录阻塞点', '沉淀阶段产物']
  },
  {
    id: 'deepen',
    name: '深化突破',
    objective: '处理难点和薄弱环节，提升完成质量。',
    checklist: ['攻克高难部分', '复盘薄弱点', '补齐关键细节']
  },
  {
    id: 'deliver',
    name: '收尾交付',
    objective: '留出检查、修正和交付缓冲。',
    checklist: ['完整走查成果', '修正遗留问题', '准备交付说明']
  }
];

export function createTimeMasterPlan(input: TimeMasterInput): TimeMasterPlan {
  const normalized = normalizeInput(input);
  const dates = listDates(normalized.startDate, normalized.endDate);
  const templates = choosePhaseTemplates(dates.length);
  const lengths = allocatePhaseLengths(dates.length, templates.length);
  const phasesWithoutForecast: Omit<TimeMasterPhase, 'forecast'>[] = [];
  let cursor = 0;

  templates.forEach((template, index) => {
    const phaseDates = dates.slice(cursor, cursor + lengths[index]);
    const dailyPlans = phaseDates.map((date, dayIndex) => createDailyPlan(normalized, template, date, dayIndex + 1));
    phasesWithoutForecast.push({
      id: template.id,
      name: template.name,
      startDate: phaseDates[0],
      endDate: phaseDates.at(-1) ?? phaseDates[0],
      objective: template.objective,
      dailyPlans
    });
    cursor += lengths[index];
  });

  const forecast = buildForecast(phasesWithoutForecast);
  const phases = phasesWithoutForecast.map((phase) => ({
    ...phase,
    forecast: forecast.filter((point) => point.phaseId === phase.id)
  }));

  return {
    title: normalized.taskTitle,
    summary: normalized.taskContent,
    totalDays: dates.length,
    dailyMinutes: normalized.dailyMinutes,
    habits: normalized.habits,
    phases,
    forecast,
    rationale: buildRationale(normalized, phases)
  };
}

export function scheduleTimesForDailyPlan(day: TimeMasterDailyPlan) {
  const [startHour, startMinute, endHour, endMinute] = day.timeBlock.split(/[:-]/).map(Number);
  return {
    startAt: `${day.date}T${String(startHour).padStart(2, '0')}:${String(startMinute).padStart(2, '0')}`,
    endAt: `${day.date}T${String(endHour).padStart(2, '0')}:${String(endMinute).padStart(2, '0')}`
  };
}

function normalizeInput(input: TimeMasterInput): TimeMasterInput {
  const taskTitle = input.taskTitle.trim();
  const taskContent = input.taskContent.trim();
  const startDate = normalizeDate(input.startDate);
  const endDate = normalizeDate(input.endDate);
  const dailyMinutes = Math.round(Number(input.dailyMinutes));

  if (!taskTitle) throw new Error('任务名称不能为空');
  if (!taskContent) throw new Error('任务内容不能为空');
  if (!Number.isFinite(dailyMinutes) || dailyMinutes < 25) throw new Error('每日投入至少需要 25 分钟');
  if (parseUtcDate(startDate).getTime() > parseUtcDate(endDate).getTime()) throw new Error('结束日期不能早于开始日期');

  return { ...input, taskTitle, taskContent, startDate, endDate, dailyMinutes };
}

function choosePhaseTemplates(totalDays: number) {
  if (totalDays <= 4) return [phaseLibrary[0], phaseLibrary[3]];
  if (totalDays <= 10) return [phaseLibrary[0], phaseLibrary[1], phaseLibrary[3]];
  return phaseLibrary;
}

function allocatePhaseLengths(totalDays: number, phaseCount: number) {
  const ratios: Record<number, number[]> = {
    2: [0.35, 0.65],
    3: [0.25, 0.5, 0.25],
    4: [0.2, 0.42, 0.23, 0.15]
  };
  const lengths = ratios[phaseCount].map((ratio) => Math.max(1, Math.floor(totalDays * ratio)));
  let remaining = totalDays - lengths.reduce((sum, value) => sum + value, 0);
  let cursor = 0;

  while (remaining > 0) {
    lengths[cursor % lengths.length] += 1;
    remaining -= 1;
    cursor += 1;
  }

  while (remaining < 0) {
    const largestIndex = lengths.indexOf(Math.max(...lengths));
    lengths[largestIndex] -= 1;
    remaining += 1;
  }

  return lengths;
}

function createDailyPlan(
  task: TimeMasterInput,
  phase: (typeof phaseLibrary)[number],
  date: string,
  phaseDay: number
): TimeMasterDailyPlan {
  const focusMinutes = focusMinutesForDate(date, task);
  const title = `${phase.name} D${phaseDay}: ${task.taskTitle}`;
  return {
    date,
    title,
    focusMinutes,
    timeBlock: preferredTimeBlock(task.habits.energy),
    checklist: [...phase.checklist, reviewAction(task.habits.reviewPreference)],
    scheduleTitle: title,
    scheduleNotes: `${phase.objective} 今日建议投入 ${focusMinutes} 分钟。`
  };
}

function focusMinutesForDate(date: string, task: TimeMasterInput) {
  const day = parseUtcDate(date).getUTCDay();
  const isWeekend = day === 0 || day === 6;

  if (task.habits.restPattern === 'weekday-only' && isWeekend) return 25;
  if (task.habits.restPattern === 'weekend-light' && isWeekend) return Math.max(25, Math.round(task.dailyMinutes * 0.65));
  if (task.habits.focusStyle === 'fragmented') return Math.max(25, Math.round(task.dailyMinutes * 0.8));
  return Math.max(45, task.dailyMinutes);
}

function preferredTimeBlock(energy: TimeMasterEnergy) {
  if (energy === 'afternoon') return '14:30-16:00';
  if (energy === 'evening') return '20:00-21:30';
  return '08:30-10:00';
}

function reviewAction(preference: TimeMasterReviewPreference) {
  if (preference === 'daily') return '写下今日复盘和明日第一步';
  if (preference === 'milestone') return '标记阶段里程碑状态';
  return '记录本周可复用经验';
}

function buildForecast(phases: Omit<TimeMasterPhase, 'forecast'>[]): ForecastPoint[] {
  const totalDays = phases.reduce((sum, phase) => sum + phase.dailyPlans.length, 0);
  let elapsed = 0;

  return phases.flatMap((phase) =>
    phase.dailyPlans.map((day) => {
      elapsed += 1;
      return {
        date: day.date,
        day: elapsed,
        phaseId: phase.id,
        value: Math.round((elapsed / totalDays) * 100)
      };
    })
  );
}

function buildRationale(task: TimeMasterInput, phases: TimeMasterPhase[]) {
  const names = phases.map((phase) => phase.name).join('、');
  return `这份计划按「${names}」推进：先降低启动阻力，再把主要工作放进稳定执行区，最后保留检查和交付缓冲。系统根据你的高能时段、每天 ${task.dailyMinutes} 分钟投入和休息策略调整每日强度，避免长周期任务中途透支。`;
}

function listDates(startDate: string, endDate: string) {
  const dates: string[] = [];
  let cursor = parseUtcDate(startDate).getTime();
  const end = parseUtcDate(endDate).getTime();

  while (cursor <= end) {
    dates.push(formatUtcDate(new Date(cursor)));
    cursor += MS_PER_DAY;
  }

  return dates;
}

function normalizeDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) throw new Error('日期格式需要为 YYYY-MM-DD');
  return formatUtcDate(parseUtcDate(value));
}

function parseUtcDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
    throw new Error('日期无效');
  }
  return date;
}

function formatUtcDate(date: Date) {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
