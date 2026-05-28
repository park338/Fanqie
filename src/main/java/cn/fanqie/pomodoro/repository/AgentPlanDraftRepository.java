package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.entity.AgentPlanDraftEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentPlanDraftRepository extends JpaRepository<AgentPlanDraftEntity, Long> {
    List<AgentPlanDraftEntity> findTop5ByOrderByCreatedAtDesc();
}
