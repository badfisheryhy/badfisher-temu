package com.badfisher.temu.business.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.product.model.TemuProduct;

import java.util.List;

/** Temu 商品档案服务。 */
public interface TemuProductService extends IService<TemuProduct> {

	int insertOrUpdateBatch(List<TemuProduct> list);

	void markMissingDeleted(String appId, String sellerId, Long syncTaskId);

}
