package com.badfisher.temu.business.sync.execution;

import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.sync.service.SyncTaskService;
import com.badfisher.temu.server.TemuApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用真实 Mapper 和独立 H2 数据库验证执行身份与事务互斥，不访问平台。 */
@SpringBootTest(classes = TemuApplication.class, properties = {
		"spring.datasource.url=jdbc:h2:mem:task-execution;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
		"spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
		"spring.datasource.password=", "temu.security.api-key=execution-test-key-at-least-32-characters",
		"temu.client.rate-limit-mode=local", "temu.scheduler.enabled=false" })
class SyncTaskExecutionIntegrationTest {

	@Autowired
	private SyncTaskService taskService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transaction;

	@BeforeEach
	void initializeSchema() throws Exception {
		String schema;
		try (InputStream input = new ClassPathResource("schema/schema.sql").getInputStream()) {
			schema = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
		}
		int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `tb_temu_order_request_task`");
		int end = schema.indexOf(';', start);
		jdbc.execute(schema.substring(start, end).replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", ""));
		jdbc.execute("CREATE TABLE IF NOT EXISTS execution_write_probe (id INT PRIMARY KEY)");
		jdbc.update("DELETE FROM execution_write_probe");
		jdbc.update("DELETE FROM tb_temu_order_request_task");
		jdbc.update("INSERT INTO tb_temu_order_request_task "
				+ "(id, app_id, auth_region, account_code, site, channel_id, pull_type, state, del_flag, "
				+ "begin_time, end_time) VALUES (1, 'demo-app', 'US', 'demo-seller', 'US', 1001, 'ORDER', 0, 0, "
				+ "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
		transaction = new TransactionTemplate(transactionManager);
	}

	@Test
	void oldExecutionCannotRenewFinishFailOrWriteAfterRecoveryAndRetry() {
		assertTrue(claim("execution-a"));
		expireLease();
		assertTrue(taskService.timeoutTask(1L, new Date(), "expired"));
		assertTrue(taskService.retryFailedTask(1L, "execution-b"));

		assertFalse(taskService.heartbeatTask(1L, "execution-a"));
		assertFalse(taskService.completeTask(1L, "execution-a"));
		assertFalse(taskService.failTask(1L, "execution-a", "stale failure"));
		assertThrows(TaskLeaseLostException.class, () -> guardedWrite(task("execution-a"), 1));
		assertEquals(0, writeCount());

		guardedWrite(task("execution-b"), 2);
		assertTrue(taskService.completeTask(1L, "execution-b"));
		assertEquals(1, writeCount());
		assertEquals(2, taskService.getById(1L).getState());
	}

	@Test
	void expiredLeaseCannotBeRevivedEvenBeforeRecoveryRuns() {
		assertTrue(claim("execution-a"));
		expireLease();

		assertFalse(taskService.heartbeatTask(1L, "execution-a"));
		assertFalse(taskService.completeTask(1L, "execution-a"));
		assertFalse(taskService.failTask(1L, "execution-a", "late failure"));
		assertThrows(TaskLeaseLostException.class, () -> taskService.checkExecution(task("execution-a")));
		assertThrows(TaskLeaseLostException.class, () -> guardedWrite(task("execution-a"), 1));
		assertEquals(0, writeCount());
	}

	@Test
	void heartbeatAndRecoveryShouldUseDatabaseLeaseDeadline() {
		assertTrue(claim("execution-a"));
		// 旧 update_time 不能覆盖仍有效的数据库租约。
		jdbc.update("UPDATE tb_temu_order_request_task SET update_time = TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP)");
		assertFalse(taskService.timeoutTask(1L, new Date(), "must not reclaim"));
		assertTrue(taskService.heartbeatTask(1L, "execution-a"));
		taskService.checkExecution(task("execution-a"));
	}

	@Test
	void executionLockMustShareBusinessTransactionAndRollbackWithIt() {
		assertTrue(claim("execution-a"));
		assertThrows(IllegalStateException.class, () -> taskService.lockExecution(task("execution-a")));
		assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
			taskService.lockExecution(task("execution-a"));
			jdbc.update("INSERT INTO execution_write_probe VALUES (1)");
			throw new IllegalStateException("business write failed");
		}));
		assertEquals(0, writeCount());
	}

	@Test
	void replacementMustWaitForCurrentBusinessTransactionAndThenFenceOldWrites() throws Exception {
		assertTrue(claim("execution-a"));
		ExecutorService workers = Executors.newFixedThreadPool(2);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch replacementStarted = new CountDownLatch(1);
		try {
			Future<?> writer = workers.submit(() -> transaction.executeWithoutResult(status -> {
				taskService.lockExecution(task("execution-a"));
				jdbc.update("INSERT INTO execution_write_probe VALUES (1)");
				locked.countDown();
				await(release);
			}));
			assertTrue(locked.await(5, TimeUnit.SECONDS));
			Future<?> replacement = workers.submit(() -> {
				replacementStarted.countDown();
				assertTrue(taskService.failTask(1L, "execution-a", "replace after current write"));
				assertTrue(taskService.retryFailedTask(1L, "execution-b"));
			});
			assertTrue(replacementStarted.await(5, TimeUnit.SECONDS));
			assertThrows(TimeoutException.class, () -> replacement.get(150, TimeUnit.MILLISECONDS));
			release.countDown();
			writer.get(5, TimeUnit.SECONDS);
			replacement.get(5, TimeUnit.SECONDS);
			assertThrows(TaskLeaseLostException.class, () -> guardedWrite(task("execution-a"), 2));
			assertEquals(1, writeCount());
		}
		finally {
			release.countDown();
			workers.shutdownNow();
			assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test
	void blankExecutionIdentityCannotClaimOrBypassWriteGuard() {
		assertThrows(IllegalArgumentException.class, () -> claim(" "));
		assertThrows(TaskLeaseLostException.class, () -> taskService.checkExecution(task(null)));
		assertEquals(0, taskService.getById(1L).getState());
	}

	private boolean claim(String executionId) {
		return taskService.claimTaskByAuth(1L, "demo-app", "demo-seller", "US", "ORDER", executionId);
	}

	private void expireLease() {
		jdbc.update("UPDATE tb_temu_order_request_task "
				+ "SET lease_expires_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP) WHERE id = 1");
	}

	private void guardedWrite(SyncTask task, int rowId) {
		transaction.executeWithoutResult(status -> {
			taskService.lockExecution(task);
			jdbc.update("INSERT INTO execution_write_probe VALUES (?)", rowId);
		});
	}

	private int writeCount() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM execution_write_probe", Integer.class);
	}

	private static SyncTask task(String executionId) {
		SyncTask task = new SyncTask();
		task.setId(1L);
		task.setExecutionId(executionId);
		return task;
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("test coordination timeout");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("test interrupted", e);
		}
	}

}
