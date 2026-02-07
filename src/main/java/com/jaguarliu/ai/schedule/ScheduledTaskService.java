package com.jaguarliu.ai.schedule;

import com.jaguarliu.ai.channel.ChannelRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final ScheduledTaskRepository repository;
    private final ScheduledTaskExecutor executor;
    private final ChannelRepository channelRepository;
    private final TaskScheduler taskScheduler;

    /**
     * 运行中的调度任务句柄
     */
    private final Map<String, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        List<ScheduledTaskEntity> enabledTasks = repository.findByEnabledTrue();
        for (ScheduledTaskEntity task : enabledTasks) {
            scheduleTask(task);
        }
        log.info("Loaded {} scheduled tasks", enabledTasks.size());
    }

    public ScheduledTaskEntity create(String name, String cronExpr, String prompt,
                                       String channelId, String channelType,
                                       String emailTo, String emailCc) {
        // 验证渠道存在
        channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        ScheduledTaskEntity task = ScheduledTaskEntity.builder()
                .name(name)
                .cronExpr(cronExpr)
                .prompt(prompt)
                .channelId(channelId)
                .channelType(channelType)
                .emailTo(emailTo)
                .emailCc(emailCc)
                .enabled(true)
                .build();

        task = repository.save(task);
        scheduleTask(task);
        log.info("Created scheduled task: name={}, cron={}", name, cronExpr);
        return task;
    }

    public void delete(String id) {
        ScheduledTaskEntity task = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled task not found: " + id));
        cancelTask(id);
        repository.delete(task);
        log.info("Deleted scheduled task: name={}", task.getName());
    }

    public void toggle(String id, boolean enabled) {
        ScheduledTaskEntity task = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled task not found: " + id));
        task.setEnabled(enabled);
        repository.save(task);

        if (enabled) {
            scheduleTask(task);
        } else {
            cancelTask(id);
        }
        log.info("Toggled scheduled task: name={}, enabled={}", task.getName(), enabled);
    }

    public void runNow(String id) {
        ScheduledTaskEntity task = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled task not found: " + id));
        // 在新线程中执行，不阻塞调用方
        new Thread(() -> executor.execute(task), "schedule-manual-" + task.getName()).start();
        log.info("Manual run triggered for scheduled task: name={}", task.getName());
    }

    public List<ScheduledTaskEntity> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    private void scheduleTask(ScheduledTaskEntity task) {
        cancelTask(task.getId());
        try {
            // Spring CronTrigger 要求 6 段（秒 分 时 日 月 周），自动兼容 5 段 Unix cron
            String springCron = toSpringCron(task.getCronExpr());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> executor.execute(task),
                    new CronTrigger(springCron)
            );
            scheduledFutures.put(task.getId(), future);
            log.info("Scheduled task registered: name={}, cron={} (spring: {})", task.getName(), task.getCronExpr(), springCron);
        } catch (IllegalArgumentException e) {
            log.error("Invalid cron expression for task {}: {} - {}", task.getName(), task.getCronExpr(), e.getMessage());
        }
    }

    /**
     * 将 5 段 Unix cron 转为 6 段 Spring cron（补秒字段 0）
     * 5 段: 分 时 日 月 周
     * 6 段: 秒 分 时 日 月 周
     */
    private static String toSpringCron(String cron) {
        String trimmed = cron.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 5) {
            return "0 " + trimmed;
        }
        return trimmed;
    }

    private void cancelTask(String id) {
        ScheduledFuture<?> future = scheduledFutures.remove(id);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 将实体转换为 DTO Map
     */
    public static Map<String, Object> toDto(ScheduledTaskEntity task) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", task.getId());
        dto.put("name", task.getName());
        dto.put("cronExpr", task.getCronExpr());
        dto.put("prompt", task.getPrompt());
        dto.put("channelId", task.getChannelId());
        dto.put("channelType", task.getChannelType());
        dto.put("emailTo", task.getEmailTo());
        dto.put("emailCc", task.getEmailCc());
        dto.put("enabled", task.isEnabled());
        dto.put("lastRunAt", task.getLastRunAt() != null ? task.getLastRunAt().toString() : null);
        dto.put("lastRunSuccess", task.getLastRunSuccess());
        dto.put("lastRunError", task.getLastRunError());
        dto.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        dto.put("updatedAt", task.getUpdatedAt() != null ? task.getUpdatedAt().toString() : null);
        return dto;
    }

}
