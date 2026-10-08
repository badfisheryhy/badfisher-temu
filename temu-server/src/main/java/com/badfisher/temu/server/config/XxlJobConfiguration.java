package com.badfisher.temu.server.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Optional scheduling adapter. Disabled by default for standalone development. */
@Configuration
@ConditionalOnProperty(prefix = "temu.scheduler", name = "enabled", havingValue = "true")
@ComponentScan("com.badfisher.temu.scheduler.xxljob")
public class XxlJobConfiguration {

	@Bean
	@ConfigurationProperties(prefix = "temu.scheduler")
	public Settings xxlJobSettings() {
		return new Settings();
	}

	@Bean
	public XxlJobSpringExecutor xxlJobExecutor(Settings settings) {
		if (settings.getAdminAddresses() == null || settings.getAdminAddresses().trim().isEmpty()) {
			throw new IllegalArgumentException("temu.scheduler.admin-addresses is required when enabled");
		}
		XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
		executor.setAdminAddresses(settings.getAdminAddresses());
		executor.setAppname(settings.getAppName());
		executor.setPort(settings.getPort());
		executor.setAccessToken(settings.getAccessToken());
		executor.setLogPath(settings.getLogPath());
		executor.setLogRetentionDays(settings.getLogRetentionDays());
		return executor;
	}

	@Data
	public static class Settings {

		private String adminAddresses;

		private String appName = "badfisher-temu";

		private int port = 9999;

		@lombok.ToString.Exclude
		private String accessToken;

		private String logPath = "logs/xxl-job";

		private int logRetentionDays = 7;

	}

}
