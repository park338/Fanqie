package cn.fanqie.pomodoro.web;

import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleUpsertRequest;
import cn.fanqie.pomodoro.service.ScheduleService;
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
@RequestMapping("/api/schedule")
public class ScheduleController {
    private final ScheduleService schedule;

    public ScheduleController(ScheduleService schedule) {
        this.schedule = schedule;
    }

    @GetMapping("/today")
    public List<ScheduleItemDto> today() {
        return schedule.today();
    }

    @PostMapping("/today")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleItemDto create(@Valid @RequestBody ScheduleUpsertRequest request) {
        return schedule.create(request);
    }

    @PatchMapping("/today/{id}")
    public ScheduleItemDto update(@PathVariable Long id, @Valid @RequestBody ScheduleUpsertRequest request) {
        return schedule.update(id, request);
    }

    @DeleteMapping("/today/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        schedule.delete(id);
    }
}
