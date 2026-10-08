package com.badfisher.temu.scheduler.xxljob.cleanup;

import com.badfisher.temu.business.audit.mapper.TemuApiLogMapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu API日志定期清理任务 — 默认保留最近一个月的请求及响应日志。
 * <p>
 * 使用小批量DELETE降低对线上写入、binlog及主从同步的影响；达到单次清理上限后， 剩余数据留待下一次调度继续处理。
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuApiLogCleanupJob {

	@Value("${temu.maintenance.api-log-retention-months:1}")
	private int retentionMonths = 1;

	private static final int DELETE_BATCH_SIZE = 5000;

	private static final int MAX_BATCH_ROUNDS = 200;

	@Resource
	private TemuApiLogMapper temuApiLogMapper;

	@XxlJob("temuApiLogCleanupJob")
	public void execute() {
		run("Temu API日志清理任务", this::cleanExpiredLogs);
	}

	private void cleanExpiredLogs() {
		if (retentionMonths < 1) {
			throw new IllegalArgumentException("API日志保留月数必须大于零");
		}
		log.info("开始执行API日志清理任务");
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MONTH, -retentionMonths);
		Date cutoffTime = calendar.getTime();

		int total = 0;
		int rounds = 0;
		int deleted;
		do {
			deleted = temuApiLogMapper.deleteExpiredLogs(cutoffTime, DELETE_BATCH_SIZE);
			total += deleted;
			rounds++;
		}
		while (deleted == DELETE_BATCH_SIZE && rounds < MAX_BATCH_ROUNDS);

		if (rounds == MAX_BATCH_ROUNDS && deleted == DELETE_BATCH_SIZE) {
			log.warn("API日志单次清理达到上限，剩余过期数据将在下次继续清理");
		}
		log.info("API日志清理完成: 保留月数={}, cutoffTime={}, deleted={}", retentionMonths, cutoffTime, total);
	}

}
