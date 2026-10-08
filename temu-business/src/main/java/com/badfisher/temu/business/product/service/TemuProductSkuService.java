package com.badfisher.temu.business.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.product.model.TemuProductSku;

import java.util.List;

/** Temu 商品 SKU 服务。 */
public interface TemuProductSkuService extends IService<TemuProductSku> {

	int insertOrUpdateBatch(List<TemuProductSku> list);

	void markMissingDeleted(String appId, String sellerId, Long syncTaskId);

}
