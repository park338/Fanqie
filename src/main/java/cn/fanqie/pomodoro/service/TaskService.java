package cn.fanqie.pomodoro.service;

import cn.fanqie.pomodoro.dto.ApiDtos.CreateTaskRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.ReorderTasksRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TaskDto;
import cn.fanqie.pomodoro.dto.ApiDtos.UpdateTaskRequest;
import cn.fanqie.pomodoro.entity.TaskEntity;
import cn.fanqie.pomodoro.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final Clock clock;

    public TaskService(TaskRepository tasks, Clock clock) {
        this.tasks = tasks;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> list() {
        return tasks.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public TaskDto create(CreateTaskRequest request) {
        LocalDateTime now = now();
        TaskEntity entity = new TaskEntity();
        entity.setTitle(normalizeTitle(request.title()));
        entity.setEstimatedPomodoros(request.estimatedPomodoros());
        entity.setCompletedPomodoros(0);
        entity.setDone(false);
        entity.setActive(tasks.count() == 0);
        entity.setSortOrder((int) tasks.count());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toDto(tasks.save(entity));
    }

    @Transactional
    public TaskDto update(Long id, UpdateTaskRequest request) {
        TaskEntity entity = requireTask(id);
        if (request.title() != null) {
            entity.setTitle(normalizeTitle(request.title()));
        }
        if (request.estimatedPomodoros() != null) {
            entity.setEstimatedPomodoros(request.estimatedPomodoros());
        }
        if (request.completedPomodoros() != null) {
            entity.setCompletedPomodoros(request.completedPomodoros());
        }
        if (request.done() != null) {
            entity.setDone(request.done());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        if (Boolean.TRUE.equals(request.active())) {
            setActiveInternal(entity);
        } else if (Boolean.FALSE.equals(request.active())) {
            entity.setActive(false);
        }
        entity.touch(now());
        ensureOneActiveTask();
        return toDto(entity);
    }

    @Transactional
    public TaskDto setActive(Long id) {
        TaskEntity entity = requireTask(id);
        setActiveInternal(entity);
        entity.touch(now());
        return toDto(entity);
    }

    @Transactional
    public List<TaskDto> reorder(ReorderTasksRequest request) {
        List<TaskEntity> all = tasks.findAllByOrderBySortOrderAscIdAsc();
        List<Long> ids = new ArrayList<>(request.taskIds());
        for (TaskEntity task : all) {
            int index = ids.indexOf(task.getId());
            if (index >= 0) {
                task.setSortOrder(index);
                task.touch(now());
            }
        }
        return tasks.findAllByOrderBySortOrderAscIdAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(Long id) {
        TaskEntity entity = requireTask(id);
        boolean wasActive = entity.isActive();
        tasks.delete(entity);
        if (wasActive) {
            ensureOneActiveTask();
        }
    }

    @Transactional
    public void incrementActiveCompletedPomodoros() {
        tasks.findByActiveTrue().ifPresent(task -> {
            task.setCompletedPomodoros(task.getCompletedPomodoros() + 1);
            task.touch(now());
        });
    }

    @Transactional(readOnly = true)
    public TaskEntity activeTaskOrNull() {
        return tasks.findByActiveTrue().orElse(null);
    }

    @Transactional(readOnly = true)
    public int unfinishedCount() {
        return (int) tasks.findAll().stream().filter(task -> !task.isDone()).count();
    }

    public TaskDto toDto(TaskEntity entity) {
        return new TaskDto(
                entity.getId(),
                entity.getTitle(),
                entity.getEstimatedPomodoros(),
                entity.getCompletedPomodoros(),
                entity.isDone(),
                entity.isActive(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    TaskEntity requireTask(Long id) {
        return tasks.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private void setActiveInternal(TaskEntity active) {
        tasks.findAll().forEach(task -> task.setActive(task.getId().equals(active.getId())));
    }

    private void ensureOneActiveTask() {
        if (tasks.findByActiveTrue().isPresent()) {
            return;
        }
        tasks.findAllByOrderBySortOrderAscIdAsc().stream().findFirst().ifPresent(task -> {
            task.setActive(true);
            task.touch(now());
        });
    }

    private String normalizeTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "任务名称不能为空");
        }
        return value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
