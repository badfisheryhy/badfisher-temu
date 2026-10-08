package com.badfisher.temu.business.sync.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单页拉取结果 — fetchPage 的返回值。
 * <p>
 * 用三态显式区分"成功/空/失败"，避免 null 歧义，让外层翻页循环逻辑一目了然：
 * <ul>
 * <li>{@link #success(List, int, int)} — 成功拿到数据（可能不足一页）</li>
 * <li>{@link #empty()} — API 返回空（无更多数据）</li>
 * <li>{@link #failed()} — 重试耗尽仍失败</li>
 * </ul>
 *
 * @author lenkor
 */
public class PageResult {

	private final List<Map<String, Object>> items;

	private final int total;

	private final int pageSize;

	private final boolean failed;

	private PageResult(List<Map<String, Object>> items, int total, int pageSize, boolean failed) {
		this.items = items;
		this.total = total;
		this.pageSize = pageSize;
		this.failed = failed;
	}

	public static PageResult success(List<Map<String, Object>> items, int total, int pageSize) {
		return new PageResult(items, total, pageSize, false);
	}

	public static PageResult empty() {
		return new PageResult(Collections.emptyList(), 0, 0, false);
	}

	public static PageResult failed() {
		return new PageResult(null, 0, 0, true);
	}

	public boolean isFailed() {
		return failed;
	}

	public boolean isEmpty() {
		return !failed && (items == null || items.isEmpty());
	}

	public boolean isLastPage() {
		return !failed && items != null && items.size() < pageSize;
	}

	public List<Map<String, Object>> getItems() {
		return items;
	}

	public int getTotal() {
		return total;
	}

}
