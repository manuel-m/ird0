package com.ird0.directory.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Configuration for audit async operations with a dedicated thread pool. */
@Configuration
public class AuditAsyncConfig {

  public static final String AUDIT_EXECUTOR = "auditExecutor";

  @Bean(name = AUDIT_EXECUTOR)
  public Executor auditExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("audit-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
