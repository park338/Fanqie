package cn.fanqie.pomodoro.web;

import cn.fanqie.pomodoro.dto.ApiDtos.SettingsResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.SettingsUpdateRequest;
import cn.fanqie.pomodoro.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsResponse get() {
        return settings.getSettings();
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsUpdateRequest request) {
        return settings.update(request);
    }
}
