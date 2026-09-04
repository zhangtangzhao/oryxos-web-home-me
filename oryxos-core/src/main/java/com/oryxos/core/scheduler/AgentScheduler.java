package com.oryxos.core.scheduler;

import com.oryxos.core.AgentService;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.agent.AgentLoader;
import com.oryxos.core.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * Third trigger source (FR-021): cron schedules from AGENT.md frontmatter are
 * registered dynamically into a ThreadPoolTaskScheduler (research R-6). At
 * fire time the schedule message flows through the same
 * SessionService + AgentService chain as every other trigger — the scheduler
 * session identity is fixed to scheduler:scheduler:<profile>. triggerNow()
 * provides manual catch-up running over the identical path.
 */
@Component
public class AgentScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduler.class);

    private final AgentLoader agentLoader;
    private final SessionService sessionService;
    private final AgentService agentService;

    private final Map<String, ScheduledFuture<?>> tasks = new LinkedHashMap<>();
    private ThreadPoolTaskScheduler taskScheduler;

    public AgentScheduler(AgentLoader agentLoader, SessionService sessionService, AgentService agentService) {
        this.agentLoader = agentLoader;
        this.sessionService = sessionService;
        this.agentService = agentService;
    }

    /** Starts the scheduler (gateway daemon); registering one task per schedule. */
    public synchronized void start() {
        if (taskScheduler != null) {
            return;
        }
        agentLoader.reload();
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(4);
        taskScheduler.setThreadNamePrefix("oryxos-sched-");
        taskScheduler.afterPropertiesSet();

        for (Profile profile : agentLoader.list()) {
            register(profile);
        }
        log.info("调度器已启动，注册定时任务 {} 个", tasks.size());
    }

    private void register(Profile profile) {
        if (profile.getSchedules() == null) {
            return;
        }
        for (Profile.ScheduleDef def : profile.getSchedules()) {
            if (def.getCron() == null || def.getCron().isBlank()) {
                continue;
            }
            String key = scheduleKey(profile.getName(), def);
            ZoneId zone = def.getZone() == null || def.getZone().isBlank()
                    ? ZoneId.systemDefault() : ZoneId.of(def.getZone());
            Runnable job = () -> fire(profile.getName(), def);
            tasks.put(key, taskScheduler.schedule(job, new CronTrigger(def.getCron(), zone)));
            log.info("已注册定时任务: {} cron={} zone={}", key, def.getCron(), zone);
        }
    }

    private static String scheduleKey(String profileName, Profile.ScheduleDef def) {
        return profileName + ":" + (def.getId() == null || def.getId().isBlank() ? def.getCron() : def.getId());
    }

    void fire(String profileName, Profile.ScheduleDef def) {
        String message = def.getMessage() == null || def.getMessage().isBlank()
                ? "执行定时任务 " + scheduleKey(profileName, def)
                : def.getMessage();
        try {
            Session session = sessionService.getOrCreate("scheduler", "scheduler", profileName);
            String reply = agentService.process(session, message);
            log.info("定时任务触发完成: profile={} schedule={} session={}",
                    profileName, def.getId(), session.getSessionId());
            log.debug("定时任务响应: {}", reply);
        } catch (Exception e) {
            log.error("定时任务触发失败: profile={} schedule={} 原因: {}",
                    profileName, def.getId(), e.getMessage(), e);
        }
    }

    /**
     * Manual catch-up (补跑): immediately run the profile's schedules (all of
     * them when scheduleId is null) through the exact cron path.
     *
     * @return number of schedules fired
     */
    public synchronized int triggerNow(String profileName, String scheduleId) {
        agentLoader.reload();
        Profile profile = agentLoader.require(profileName);
        int fired = 0;
        if (profile.getSchedules() != null) {
            for (Profile.ScheduleDef def : profile.getSchedules()) {
                if (scheduleId == null || scheduleId.isBlank() || scheduleId.equals(def.getId())) {
                    fire(profileName, def);
                    fired++;
                }
            }
        }
        return fired;
    }

    public synchronized boolean isRunning() {
        return taskScheduler != null;
    }

    public synchronized int taskCount() {
        return tasks.size();
    }
}
