package com.badfisher.temu.business.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.order.model.TemuChildrenOrderAmount;

import java.util.List;

public interface TemuChildrenOrderAmountService extends IService<TemuChildrenOrderAmount> {

	/**
	 * 批量 upsert：INSERT ... ON DUPLICATE KEY UPDATE
	 */
	void insertOrUpdateBatch(List<TemuChildrenOrderAmount> list);

}
