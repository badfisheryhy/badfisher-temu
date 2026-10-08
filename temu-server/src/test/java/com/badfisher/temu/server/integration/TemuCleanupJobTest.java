package com.badfisher.temu.server.integration;

import com.badfisher.temu.scheduler.xxljob.cleanup.TemuApiLogCleanupJob;
import com.badfisher.temu.scheduler.xxljob.cleanup.TemuStockSnapshotHistoryCleanJob;

import com.badfisher.temu.business.audit.mapper.TemuApiLogMapper;
import com.badfisher.temu.business.inventory.service.TemuProductStockService;
import com.xxl.job.core.context.XxlJobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.badfisher.temu.scheduler.xxljob.XxlJobTestSupport.executeJob;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 清理计划任务可执行测试。
 *
 * <p>直接调用 XXL-Job handler，不依赖调度中心和真实数据库，重点验证：</p>
 * <ul>
 *     <li>API日志只删除3个月前的数据；</li>
 *     <li>库存历史快照只删除7天前的数据；</li>
 *     <li>每批最多删除5000条，满批后继续，出现不足一批时停止。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TemuCleanupJobTest {

    private static final int DELETE_BATCH_SIZE = 5000;

    @Mock
    private TemuApiLogMapper temuApiLogMapper;

    @Mock
    private TemuProductStockService temuProductStockService;

    private TemuApiLogCleanupJob apiLogCleanupJob;
    private TemuStockSnapshotHistoryCleanJob stockHistoryCleanJob;

    @BeforeEach
    void setUp() {
        apiLogCleanupJob = new TemuApiLogCleanupJob();
        ReflectionTestUtils.setField(apiLogCleanupJob, "temuApiLogMapper", temuApiLogMapper);

        stockHistoryCleanJob = new TemuStockSnapshotHistoryCleanJob();
        ReflectionTestUtils.setField(
                stockHistoryCleanJob, "temuProductStockService", temuProductStockService);
    }

    @Test
    void shouldExecuteApiLogCleanupInBatchesAndKeepRecentMonth() {
        when(temuApiLogMapper.deleteExpiredLogs(any(Date.class), eq(DELETE_BATCH_SIZE)))
                .thenReturn(DELETE_BATCH_SIZE, 120);

        Date beforeExecute = new Date();
        XxlJobContext result = executeJob("", apiLogCleanupJob::execute);
        Date afterExecute = new Date();

        assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, result.getHandleCode(), result.getHandleMsg());

        ArgumentCaptor<Date> cutoffCaptor = ArgumentCaptor.forClass(Date.class);
        verify(temuApiLogMapper, times(2))
                .deleteExpiredLogs(cutoffCaptor.capture(), eq(DELETE_BATCH_SIZE));

        List<Date> cutoffs = cutoffCaptor.getAllValues();
        assertEquals(2, cutoffs.size());
        assertEquals(cutoffs.get(0), cutoffs.get(1), "同一次任务的所有批次必须使用相同截止时间");
        assertBetween(
                cutoffs.get(0),
                addMonths(beforeExecute, -1),
                addMonths(afterExecute, -1),
                "API日志清理截止时间应为执行时间减1个月");
    }

    @Test
    void shouldExecuteStockHistoryCleanupInBatchesAndKeepRecentSevenDays() {
        when(temuProductStockService.deleteExpiredHistory(
                any(Date.class), eq(DELETE_BATCH_SIZE)))
                .thenReturn(DELETE_BATCH_SIZE, 80);

        Date beforeExecute = new Date();
        XxlJobContext result = executeJob("", stockHistoryCleanJob::execute);
        Date afterExecute = new Date();

        assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, result.getHandleCode(), result.getHandleMsg());

        ArgumentCaptor<Date> cutoffCaptor = ArgumentCaptor.forClass(Date.class);
        verify(temuProductStockService, times(2))
                .deleteExpiredHistory(cutoffCaptor.capture(), eq(DELETE_BATCH_SIZE));

        List<Date> cutoffs = cutoffCaptor.getAllValues();
        assertEquals(2, cutoffs.size());
        assertEquals(cutoffs.get(0), cutoffs.get(1), "同一次任务的所有批次必须使用相同截止时间");
        assertBetween(
                cutoffs.get(0),
                addDays(beforeExecute, -7),
                addDays(afterExecute, -7),
                "库存历史快照清理截止时间应为执行时间减7天");
    }

    private static Date addMonths(Date source, int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(source);
        calendar.add(Calendar.MONTH, months);
        return calendar.getTime();
    }

    private static Date addDays(Date source, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(source);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    private static void assertBetween(Date actual, Date lowerBound, Date upperBound,
                                      String message) {
        assertFalse(actual.before(lowerBound), message + "，实际值早于下限");
        assertTrue(!actual.after(upperBound), message + "，实际值晚于上限");
    }
}
