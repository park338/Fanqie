import { describe, expect, it } from 'vitest';
import { createTimeMasterPlan } from '../src/timeMaster';

describe('time master planner', () => {
  it('splits a long task into phased daily plans with forecast points', () => {
    const plan = createTimeMasterPlan({
      taskTitle: '完成作品集',
      taskContent: '整理项目、补文档、发布站点',
      startDate: '2026-06-01',
      endDate: '2026-06-14',
      dailyMinutes: 90,
      habits: {
        energy: 'morning',
        focusStyle: 'deep',
        restPattern: 'weekend-light',
        reviewPreference: 'weekly'
      }
    });

    expect(plan.title).toBe('完成作品集');
    expect(plan.totalDays).toBe(14);
    expect(plan.phases.length).toBeGreaterThanOrEqual(3);
    expect(plan.phases[0].startDate).toBe('2026-06-01');
    expect(plan.phases.at(-1)?.endDate).toBe('2026-06-14');
    expect(plan.phases.flatMap((phase) => phase.dailyPlans)).toHaveLength(14);
    expect(plan.phases.every((phase) => phase.forecast.length === phase.dailyPlans.length)).toBe(true);
    expect(plan.forecast.at(-1)?.value).toBe(100);
  });
});
