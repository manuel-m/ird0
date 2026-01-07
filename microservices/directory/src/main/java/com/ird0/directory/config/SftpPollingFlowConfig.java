package com.ird0.directory.config;

import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.metadata.MetadataStore;
import org.springframework.integration.metadata.SimpleMetadataStore;
import org.springframework.integration.sftp.dsl.Sftp;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableIntegration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "directory.sftp-import", name = "enabled", havingValue = "true")
public class SftpPollingFlowConfig {

  private final SftpImportProperties properties;

  @SuppressWarnings("rawtypes")
  private final SessionFactory sftpSessionFactory;

  @Bean
  public MetadataStore metadataStore() {
    SimpleMetadataStore store = new SimpleMetadataStore();
    log.info("MetadataStore configured (in-memory)");
    return store;
  }

  @Bean
  public ThreadPoolTaskExecutor sftpImportTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(25);
    executor.setThreadNamePrefix("sftp-import-");
    executor.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    log.info("SFTP import task executor configured: core=5, max=10, queue=25");
    return executor;
  }

  @Bean
  public MessageChannel sftpFileChannel() {
    return MessageChannels.queue(10).getObject();
  }

  @Bean
  @SuppressWarnings("unchecked")
  public IntegrationFlow sftpPollingFlow() {
    log.info(
        "Configuring SFTP polling flow: poll interval={}ms, remote directory='.'",
        properties.getPolling().getFixedDelay());
    return IntegrationFlow.from(
            Sftp.inboundAdapter(sftpSessionFactory)
                .remoteDirectory(".")
                .filter(new AlwaysAcceptFileListFilter())
                .patternFilter("*.csv")
                .localDirectory(new File(properties.getLocalDirectory()))
                .preserveTimestamp(true)
                .deleteRemoteFiles(false)
                .autoCreateLocalDirectory(true)
                .localFilter(null),
            e ->
                e.poller(
                    Pollers.fixedDelay(properties.getPolling().getFixedDelay())
                        .maxMessagesPerPoll(1)))
        .channel(sftpFileChannel())
        .handle("csvFileProcessor", "processFile")
        .get();
  }
}
