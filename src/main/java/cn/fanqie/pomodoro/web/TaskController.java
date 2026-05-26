package cn.fanqie.pomodoro.web;

import cn.fanqie.pomodoro.dto.ApiDtos.CreateTaskRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.ReorderTasksRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TaskDto;
import cn.fanqie.pomodoro.dto.ApiDtos.UpdateTaskRequest;
import cn.fanqie.pomodoro.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public List<TaskDto> list() {
        return tasks.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@Valid @RequestBody CreateTaskRequest request) {
        return tasks.create(request);
    }

    @PatchMapping("/{id}")
    public TaskDto update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return tasks.update(id, request);
    }

    @PostMapping("/{id}/active")
    public TaskDto active(@PathVariable Long id) {
        return tasks.setActive(id);
    }

    @PostMapping("/reorder")
    public List<TaskDto> reorder(@Valid @RequestBody ReorderTasksRequest request) {
        return tasks.reorder(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tasks.delete(id);
    }
}
