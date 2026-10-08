package com.badfisher.temu.business.sync.support;

import com.badfisher.temu.client.exception.TemuApiException;
import lombok.extern.slf4j.Slf4j;
import com.badfisher.temu.business.sync.execution.TaskLeaseLostException;

import java.util.function.Supplier;

/**
 * 通用重试工具 — 封装"失败重试一次"的公共逻辑。
 * <p>
 * Temu API 拉取中，单页/单批次请求失败后通常重试一次再放弃。 本工具将重试循环内联到一处，避免每个 fetchPage 方法重复编写 try-catch-retry。
 * <ul>
 * <li>accessToken 过期异常直接向上抛（不可重试）</li>
 * <li>其他异常重试一次，仍失败则返回 null 表示耗尽</li>
 * </ul>
 * 调用方用 {@code null} 判断重试耗尽，因为正常 API 调用不会返回 null（失败会抛异常）。
 *
 * @author lenkor
 */
@Slf4j
public final class RetryHelper {

	private RetryHelper() {
	}

	/**
	 * 执行操作，失败重试一次。
	 * @param desc 操作描述（用于日志）
	 * @param action 要执行的操作
	 * @return 操作结果；重试耗尽返回 null
	 * @throws TemuApiException 如果 accessToken 过期（直接向上抛）
	 */
	public static <T> T withRetry(String desc, Supplier<T> action) {
		boolean retried = false;
		while (true) {
			try {
				return action.get();
			}
			catch (TemuApiException e) {
				if (!e.isRetryable()) {
					throw e;
				}
				if (!retried) {
					retried = true;
					log.warn("{} 失败, 重试一次: code={}, msg={}", desc, e.getErrorCode(), e.getMessage());
					continue;
				}
				log.warn("{} 拉取失败(已重试): code={}, msg={}", desc, e.getErrorCode(), e.getMessage());
				return null;
			}
			catch (TaskLeaseLostException e) {
				throw e;
			}
			catch (Exception e) {
				if (!retried) {
					retried = true;
					log.warn("{} 失败, 重试一次: error={}", desc, e.getMessage());
					continue;
				}
				log.warn("{} 拉取失败(已重试): error={}", desc, e.getMessage());
				return null;
			}
		}
	}

}
