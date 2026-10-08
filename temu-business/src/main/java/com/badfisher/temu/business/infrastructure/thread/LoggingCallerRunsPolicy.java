package com.badfisher.temu.business.infrastructure.thread;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池拒绝策略 — CallerRuns 降级时记录告警日志，并保护调用方 MDC 不被异步清理逻辑污染。
 *
 * <h3>为什么需要保护调用方上下文</h3>
 * <p>
 * {@link ContextTaskDecorator} 会在任务执行完成后调用 {@code MDC.clear()} 来防止线程复用时的上下文残留。但
 * CallerRunsPolicy 会让任务在 <b>调用方线程</b>中直接执行，这意味着 finally 中的 clear() 会错误地清除调用方自己的 MDC。
 * </p>
 *
 * <pre>
 * 调用线程: MDC={traceId:"abc"}
 *   → submit → 队列满 → CallerRuns → 任务在调用线程同步执行
 *   → ContextTaskDecorator.finally { MDC.clear(); }
 *   → 调用方的 traceId 丢失！
 * </pre>
 *
 * <p>
 * 本策略在委托父类执行前快照调用方 MDC，执行后恢复，确保调用方上下文完整性。
 * </p>
 *
 * @author lenkor
 */
@Slf4j
public class LoggingCallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy {

	private final String poolName;

	public LoggingCallerRunsPolicy(String poolName) {
		this.poolName = poolName;
	}

	@Override
	public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
		log.warn("[{}] 线程池饱和降级 | active={} poolSize={} corePool={} maxPool={} queueSize={} queueRemaining={}", poolName,
				e.getActiveCount(), e.getPoolSize(), e.getCorePoolSize(), e.getMaximumPoolSize(), e.getQueue().size(),
				e.getQueue().remainingCapacity());

		// ========== 保存调用方 MDC（CallerRuns 会在调用方线程中执行 r.run()） ==========
		Map<String, String> callerMdcSnapshot = MDC.getCopyOfContextMap();

		try {
			// 委托父类执行 — ContextTaskDecorator 的 finally 会清除 MDC
			super.rejectedExecution(r, e);
		}
		finally {
			// ========== 恢复调用方 MDC，确保后续业务不受影响 ==========
			if (callerMdcSnapshot != null) {
				MDC.setContextMap(callerMdcSnapshot);
			}
			else {
				MDC.clear();
			}
		}
	}

}
