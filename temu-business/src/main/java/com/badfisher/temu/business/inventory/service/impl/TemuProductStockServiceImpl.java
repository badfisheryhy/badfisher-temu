package com.badfisher.temu.business.inventory.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.inventory.mapper.TemuProductStockMapper;
import com.badfisher.temu.business.inventory.model.TemuProductStock;
import com.badfisher.temu.business.inventory.service.TemuProductStockService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemuProductStockServiceImpl extends ServiceImpl<TemuProductStockMapper, TemuProductStock>
		implements TemuProductStockService {

	@Override
	public int insertBatch(List<TemuProductStock> list) {
		return baseMapper.insertBatch(list);
	}

	@Override
	public int insertHistoryBatch(List<TemuProductStock> list) {
		return baseMapper.insertHistoryBatch(list);
	}

	@Override
	public int deleteAccountSnapshot(String appId, String sellerId) {
		return baseMapper.deleteAccountSnapshot(appId, sellerId);
	}

	@Override
	public int deleteExpiredHistory(java.util.Date cutoffTime, int limit) {
		return baseMapper.deleteExpiredHistory(cutoffTime, limit);
	}

}
