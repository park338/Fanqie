package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.domain.ScheduleSource;
import cn.fanqie.pomodoro.domain.ScheduleStatus;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleBlockDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleUpsertRequest;
import cn.fanqie.pomodoro.entity.ScheduleItemEntity;
import cn.fanqie.pomodoro.entity.TaskEntity;
import cn.fanqie.pomodoro.repository.ScheduleItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {
    private final ScheduleItemRepository scheduleItems;
    private final TaskService tasks;
    private final Clock clock;

    public ScheduleService(ScheduleItemRepository scheduleItems, TaskService tasks, Clock clock) {
        this.scheduleItems = scheduleItems;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional
    public List<ScheduleItemDto> today() {
        LocalDate today = LocalDate.now(clock);
        return listForDate(today);
    }

    @Transactional
    public List<ScheduleItemDto> listForDate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        LocalDateTime now = now();
        List<ScheduleItemEntity> items = scheduleItems.findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, end);
        items.forEach(item -> advanceStatus(item, now));
        resolveExistingConflicts(items, now);
        return items.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public List<ScheduleItemDto> upcoming(int days) {
        LocalDate startDate = LocalDate.now(clock);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = start.plusDays(Math.max(1, Math.min(days, 30)));
        LocalDateTime now = now();
        List<ScheduleItemEntity> items = scheduleItems.findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, end);
        items.forEach(item -> advanceStatus(item, now));
        resolveExistingConflicts(items, now);
        return items.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ScheduleItemDto create(ScheduleUpsertRequest request) {
        LocalDateTime now = now();
        ScheduleItemEntity entity = new ScheduleItemEntity();
        apply(entity, request);
        advanceStatus(entity, now);
        rejectTimeConflict(entity, null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toDto(scheduleItems.save(entity));
    }

    @Transactional
    public ScheduleItemDto update(Long id, ScheduleUpsertRequest request) {
        ScheduleItemEntity entity = requireItem(id);
        apply(entity, request);
        advanceStatus(entity, now());
        rejectTimeConflict(entity, id);
        entity.touch(now());
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        scheduleItems.delete(requireItem(id));
    }

    @Transactional
    public ScheduleItemDto createFromBlock(ScheduleBlockDto block) {
        ScheduleUpsertRequest request = new ScheduleUpsertRequest(
                block.title(),
                block.startAt(),
                block.endAt(),
                ScheduleStatus.PLANNED,
                ScheduleSource.AGENT,
                block.taskId(),
                block.notes()
        );
        return create(request);
    }

    @Transactional(readOnly = true)
    public List<ScheduleItemDto> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return scheduleItems.findAllById(ids).stream()
                .sorted(Comparator.comparingInt(item -> ids.indexOf(item.getId())))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public String findConflictMessage(ScheduleBlockDto block) {
        if (!block.endAt().isAfter(block.startAt())) {
            return "结束时间必须晚于开始时间";
        }
        ScheduleItemEntity conflict = findActiveConflict(block.startAt(), block.endAt(), null);
        return conflict == null ? null : "时间冲突：已存在安排「" + conflict.getTitle() + "」。";
    }

    public ScheduleItemDto toDto(ScheduleItemEntity entity) {
        TaskEntity task = entity.getTask();
        return new ScheduleItemDto(
                entity.getId(),
                entity.getTitle(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getStatus(),
                entity.getSource(),
                task == null ? null : task.getId(),
                entity.getNotes()
        );
    }

    private void apply(ScheduleItemEntity entity, ScheduleUpsertRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        entity.setTitle(request.title().trim());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setStatus(request.status() == null ? ScheduleStatus.PLANNED : request.status());
        entity.setSource(request.source() == null ? ScheduleSource.USER : request.source());
        entity.setNotes(request.notes());
        entity.setTask(request.taskId() == null ? null : tasks.requireTask(request.taskId()));
    }

    private ScheduleItemEntity requireItem(Long id) {
        return scheduleItems.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "安排不存在"));
    }

    private void rejectTimeConflict(ScheduleItemEntity candidate, Long ignoredId) {
        ScheduleItemEntity conflict = findActiveConflict(candidate.getStartAt(), candidate.getEndAt(), ignoredId);
        if (conflict != null) {
            throw new ApiException(HttpStatus.CONFLICT, "时间冲突：已存在安排「" + conflict.getTitle() + "」。");
        }
    }

    private ScheduleItemEntity findActiveConflict(LocalDateTime startAt, LocalDateTime endAt, Long ignoredId) {
        LocalDateTime now = now();
        return scheduleItems
                .findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(endAt, startAt)
                .stream()
                .filter(item -> ignoredId == null || !ignoredId.equals(item.getId()))
                .filter(item -> {
                    advanceStatus(item, now);
                    return item.getStatus() == ScheduleStatus.PLANNED || item.getStatus() == ScheduleStatus.IN_PROGRESS;
                })
                .findFirst()
                .orElse(null);
    }

    private void resolveExistingConflicts(List<ScheduleItemEntity> items, LocalDateTime now) {
        ScheduleItemEntity accepted = null;
        for (ScheduleItemEntity item : items) {
            if (item.getStatus() != ScheduleStatus.PLANNED && item.getStatus() != ScheduleStatus.IN_PROGRESS) {
                continue;
            }
            if (accepted != null && item.getStartAt().isBefore(accepted.getEndAt())) {
                item.setStatus(ScheduleStatus.SKIPPED);
                item.touch(now);
                continue;
            }
            accepted = item;
        }
    }

    private void advanceStatus(ScheduleItemEntity item, LocalDateTime now) {
        if (item.getStatus() == ScheduleStatus.SKIPPED || item.getStatus() == ScheduleStatus.DONE) {
            return;
        }
        if (!now.isBefore(item.getEndAt())) {
            item.setStatus(ScheduleStatus.DONE);
            item.touch(now);
            return;
        }
        if (!now.isBefore(item.getStartAt())) {
            item.setStatus(ScheduleStatus.IN_PROGRESS);
            item.touch(now);
            return;
        }
        if (item.getStatus() != ScheduleStatus.PLANNED) {
            item.setStatus(ScheduleStatus.PLANNED);
            item.touch(now);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
