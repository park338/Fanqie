package cn.fanqie.pomodoro.web;

import cn.fanqie.pomodoro.dto.ApiDtos.AgentAdviceRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentAdviceResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanDraftDto;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanPreviewResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.AgentPlanResponse;
import cn.fanqie.pomodoro.dto.ApiDtos.ApplyPlanRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.ScheduleItemDto;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPlanRequest;
import cn.fanqie.pomodoro.dto.ApiDtos.TimeMasterPlanResponse;
import cn.fanqie.pomodoro.service.AgentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agent;

    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    @PostMapping("/advice")
    public AgentAdviceResponse advice(@Valid @RequestBody AgentAdviceRequest request) {
        return agent.advice(request);
    }

    @PostMapping("/plan")
    public AgentPlanResponse plan(@Valid @RequestBody AgentAdviceRequest request) {
        return agent.plan(request);
    }

    @PostMapping("/time-master")
    public TimeMasterPlanResponse timeMaster(@Valid @RequestBody TimeMasterPlanRequest request) {
        return agent.timeMaster(request);
    }

    @PostMapping("/plan/{id}/apply")
    public List<ScheduleItemDto> applyPlan(@PathVariable Long id, @RequestBody(required = false) ApplyPlanRequest request) {
        return agent.applyPlan(id, request);
    }

    @PostMapping("/plan/{id}/reject")
    public AgentPlanDraftDto rejectPlan(@PathVariable Long id) {
        return agent.rejectPlan(id);
    }

    @GetMapping("/plan/{id}/preview")
    public AgentPlanPreviewResponse previewPlan(@PathVariable Long id) {
        return agent.previewPlan(id);
    }

    @GetMapping("/plans/recent")
    public List<AgentPlanDraftDto> recentPlans() {
        return agent.recentPlans();
    }
}
