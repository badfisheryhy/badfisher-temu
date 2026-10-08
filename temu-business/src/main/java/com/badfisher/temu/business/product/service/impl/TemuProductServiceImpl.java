package com.badfisher.temu.business.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.product.mapper.TemuProductMapper;
import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.product.service.TemuProductService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemuProductServiceImpl extends ServiceImpl<TemuProductMapper, TemuProduct> implements TemuProductService {

	@Override
	public int insertOrUpdateBatch(List<TemuProduct> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

	@Override
	public void markMissingDeleted(String appId, String sellerId, Long syncTaskId) {
		baseMapper.markMissingDeleted(appId, sellerId, syncTaskId);
	}

}
