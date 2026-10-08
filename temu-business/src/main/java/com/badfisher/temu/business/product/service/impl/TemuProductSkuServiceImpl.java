package com.badfisher.temu.business.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.product.mapper.TemuProductSkuMapper;
import com.badfisher.temu.business.product.model.TemuProductSku;
import com.badfisher.temu.business.product.service.TemuProductSkuService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemuProductSkuServiceImpl extends ServiceImpl<TemuProductSkuMapper, TemuProductSku>
		implements TemuProductSkuService {

	@Override
	public int insertOrUpdateBatch(List<TemuProductSku> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

	@Override
	public void markMissingDeleted(String appId, String sellerId, Long syncTaskId) {
		baseMapper.markMissingDeleted(appId, sellerId, syncTaskId);
	}

}
