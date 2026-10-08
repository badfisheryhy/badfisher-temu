package com.badfisher.temu.server;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boot, mapper registration, pagination and authentication without corporate services or live API calls. */
@SpringBootTest(classes = TemuApplication.class, properties = {
		"spring.datasource.url=jdbc:h2:mem:standalone;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
		"spring.datasource.password=", "temu.security.api-key=standalone-test-key-at-least-32-characters",
		"temu.client.rate-limit-mode=local", "temu.scheduler.enabled=false" })
@AutoConfigureMockMvc
class StandaloneApplicationTest {

	private static final String KEY = "standalone-test-key-at-least-32-characters";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private ApplicationContext context;

	@BeforeEach
	void initializeSchema() throws Exception {
		String schema;
		try (java.io.InputStream input = new ClassPathResource("schema/schema.sql").getInputStream()) {
			schema = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
		}
		// H2 covers mapper contracts; MySQL storage-engine/database selection is deployment-specific.
		schema = schema.replaceAll("(?m)^CREATE DATABASE[^;]+;", "")
			.replaceAll("(?m)^USE[^;]+;", "")
			.replaceAll("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
		// MySQL index names are table-scoped; H2 requires schema-wide unique names.
		Matcher indexes = Pattern.compile("(?m)^(\\s*(?:UNIQUE )?KEY) `[^`]+`").matcher(schema);
		StringBuffer normalized = new StringBuffer();
		int index = 0;
		while (indexes.find()) {
			indexes.appendReplacement(normalized, indexes.group(1) + " `test_index_" + index++ + "`");
		}
		indexes.appendTail(normalized);
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ByteArrayResource(normalized.toString().getBytes(StandardCharsets.UTF_8)));
		populator.setSqlScriptEncoding("UTF-8");
		populator.execute(dataSource);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("DELETE FROM tb_temu_access_token");
		jdbc.update("INSERT INTO tb_temu_access_token "
				+ "(app_id, seller_id, auth_region, type, access_token, app_key, app_secret, del_flag) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
				"demo-app", "demo-seller", "US", 1, "private-token", "private-key", "private-secret", 0);
	}

	@Test
	void startsWithoutRedisNacosOrXxlJobAndRejectsAnonymousBusinessRequests() throws Exception {
		assertThat(context.getBeansOfType(XxlJobSpringExecutor.class)).isEmpty();
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(get("/temu/access-token/list")).andExpect(status().isUnauthorized());
		mvc.perform(get("/temu/access-token/list").header("X-Api-Key", "invalid"))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/temu/api/call").header("X-Api-Key", KEY)
			.contentType("application/json").content("{}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void authenticatesAndQueriesWithPaginationWithoutReturningCredentials() throws Exception {
		mvc.perform(get("/temu/access-token/list").header("X-Api-Key", KEY).param("pageSize", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.records[0].sellerId").value("demo-seller"))
			.andExpect(content().string(not(containsString("private-token"))))
			.andExpect(content().string(not(containsString("private-key"))))
			.andExpect(content().string(not(containsString("private-secret"))));
	}

}
