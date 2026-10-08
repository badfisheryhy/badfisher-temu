package com.badfisher.temu.business.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.order.model.TemuParentOrder;

import java.util.List;

public interface TemuParentOrderService extends IService<TemuParentOrder> {

	/**
	 * 查询指定卖家自指定时间起、无金额记录的父订单号
	 */
	List<String> listOrderSnsMissingAmount(String sellerId, long sinceSeconds);

	/**
	 * 批量 upsert：INSERT ... ON DUPLICATE KEY UPDATE
	 */
	void insertOrUpdateBatch(List<TemuParentOrder> list);

}
