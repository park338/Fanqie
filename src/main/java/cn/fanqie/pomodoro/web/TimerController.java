package cn.fanqie.pomodoro.web;

import cn.fanqie.pomodoro.dto.ApiDtos.TimerStartRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimerStateDto;
import cn.fanqie.pomodoro.service.TimerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timer")
public class TimerController {
    private final TimerService timer;

    public TimerController(TimerService timer) {
        this.timer = timer;
    }

    @GetMapping
    public TimerStateDto current() {
        return timer.current();
    }

    @PostMapping("/start")
    public TimerStateDto start(@RequestBody(required = false) TimerStartRequest request) {
        return timer.start(request);
    }

    @PostMapping("/pause")
    public TimerStateDto pause() {
        return timer.pause();
    }

    @PostMapping("/reset")
    public TimerStateDto reset() {
        return timer.reset();
    }

    @PostMapping("/skip")
    public TimerStateDto skip() {
        return timer.skip();
    }

    @PostMapping("/complete")
    public TimerStateDto complete() {
        return timer.complete();
    }
}
