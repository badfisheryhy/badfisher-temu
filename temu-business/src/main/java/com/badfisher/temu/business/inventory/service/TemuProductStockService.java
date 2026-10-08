package com.badfisher.temu.business.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.inventory.model.TemuProductStock;

import java.util.List;

/** Temu商品仓库库存服务。 */
public interface TemuProductStockService extends IService<TemuProductStock> {

	int insertBatch(List<TemuProductStock> list);

	int insertHistoryBatch(List<TemuProductStock> list);

	int deleteAccountSnapshot(String appId, String sellerId);

	int deleteExpiredHistory(java.util.Date cutoffTime, int limit);

}
