package com.badfisher.temu.business.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.order.model.TemuOrderProduct;

import java.util.List;

public interface TemuOrderProductService extends IService<TemuOrderProduct> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (parent_order_sn, children_order_no,
	 * product_sku_id)
	 */
	int insertOrUpdateBatch(List<TemuOrderProduct> list);

}
