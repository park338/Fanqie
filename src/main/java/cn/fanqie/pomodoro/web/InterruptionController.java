package cn.fanqie.pomodoro.web;

import cn.fanqie.pomodoro.dto.ApiDtos.CreateInterruptionRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.InterruptionDto;
import cn.fanqie.pomodoro.service.InterruptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interruptions")
public class InterruptionController {
    private final InterruptionService interruptions;

    public InterruptionController(InterruptionService interruptions) {
        this.interruptions = interruptions;
    }

    @GetMapping("/today")
    public List<InterruptionDto> today() {
        return interruptions.today();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterruptionDto record(@Valid @RequestBody(required = false) CreateInterruptionRequest request) {
        return interruptions.record(request);
    }
}
