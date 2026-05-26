package cn.fanqie.pomodoro.repository;

import cn.fanqie.pomodoro.entity.AgentConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConversationRepository extends JpaRepository<AgentConversationEntity, Long> {
}
