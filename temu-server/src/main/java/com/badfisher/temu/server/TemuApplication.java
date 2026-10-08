package com.badfisher.temu.server;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Temu 服务启动类
 *
 * @author lenkor
 */
@EnableAsync(proxyTargetClass = true)
@EnableTransactionManagement(proxyTargetClass = true)
@SpringBootApplication(scanBasePackages = { "com.badfisher.temu.business", "com.badfisher.temu.server" },
		exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.badfisher.temu.business.**.mapper")
@Slf4j
public class TemuApplication {

	public static void main(String[] args) {
		SpringApplication.run(TemuApplication.class, args);
		log.info("Temu 服务启动成功");
	}

}
