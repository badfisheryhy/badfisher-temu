package com.badfisher.temu.business.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.order.model.TemuParentOrderAmount;

import java.util.List;

public interface TemuParentOrderAmountService extends IService<TemuParentOrderAmount> {

	/**
	 * 批量 upsert：INSERT ... ON DUPLICATE KEY UPDATE
	 */
	void insertOrUpdateBatch(List<TemuParentOrderAmount> list);

}
