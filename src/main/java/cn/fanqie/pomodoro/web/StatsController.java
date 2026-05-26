package cn.fanqie.pomodoro.web;

import cn.fanqie.pomodoro.dto.ApiDtos.StatsResponse;
import cn.fanqie.pomodoro.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/today")
    public StatsResponse today() {
        return stats.today();
    }
}
