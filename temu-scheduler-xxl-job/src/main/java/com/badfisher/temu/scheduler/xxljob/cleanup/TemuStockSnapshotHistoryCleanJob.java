package com.badfisher.temu.scheduler.xxljob.cleanup;

import com.badfisher.temu.business.inventory.service.TemuProductStockService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;

import static com.badfisher.temu.scheduler.xxljob.TemuXxlJobResult.run;

/**
 * Temu库存历史快照清理任务 — 分批删除7天前的数据，避免大事务和长时间锁表。
 *
 * @author lenkor
 */
@Slf4j
@Component
public class TemuStockSnapshotHistoryCleanJob {

	private static final int RETENTION_DAYS = 7;

	private static final int DELETE_BATCH_SIZE = 5000;

	private static final int MAX_BATCH_ROUNDS = 200;

	@Resource
	private TemuProductStockService temuProductStockService;

	@XxlJob("temuStockSnapshotHistoryCleanJob")
	public void execute() {
		run("Temu库存历史快照清理任务", this::cleanExpiredHistory);
	}

	private void cleanExpiredHistory() {
		log.info("开始执行库存历史快照清理任务");
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.DAY_OF_MONTH, -RETENTION_DAYS);
		Date cutoffTime = calendar.getTime();

		int total = 0;
		int rounds = 0;
		int deleted;
		do {
			deleted = temuProductStockService.deleteExpiredHistory(cutoffTime, DELETE_BATCH_SIZE);
			total += deleted;
			rounds++;
		}
		while (deleted == DELETE_BATCH_SIZE && rounds < MAX_BATCH_ROUNDS);

		if (rounds == MAX_BATCH_ROUNDS && deleted == DELETE_BATCH_SIZE) {
			log.warn("库存历史快照单次清理达到上限，剩余过期数据将在下次继续清理");
		}
		log.info("库存历史快照清理完成: cutoffTime={}, deleted={}", cutoffTime, total);
	}

}
