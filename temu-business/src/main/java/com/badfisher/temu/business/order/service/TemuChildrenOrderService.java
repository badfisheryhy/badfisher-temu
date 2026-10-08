package com.badfisher.temu.business.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.order.model.TemuChildrenOrder;

import java.util.List;

public interface TemuChildrenOrderService extends IService<TemuChildrenOrder> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (parent_order_sn, seller_id,
	 * order_sn)
	 */
	int insertOrUpdateBatch(List<TemuChildrenOrder> list);

}
