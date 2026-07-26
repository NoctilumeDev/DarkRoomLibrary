package org.darkroomlibrary.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务在运行环境启用，测试环境通过直接调用验证任务逻辑。
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
