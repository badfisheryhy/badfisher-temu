package com.badfisher.temu.business.aftersales.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.aftersales.mapper.TemuAftersalesReturnLogisticsMapper;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesReturnLogisticsService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/** Temu售后退货物流服务实现。 */
@Service
public class TemuAftersalesReturnLogisticsServiceImpl
		extends ServiceImpl<TemuAftersalesReturnLogisticsMapper, TemuAftersalesReturnLogistics>
		implements TemuAftersalesReturnLogisticsService {

	@Override
	public int softDeleteByParent(Integer channelId, String site, String sellerId, String parentAftersalesSn,
			Date updateTime) {
		return baseMapper.softDeleteByParent(channelId, site, sellerId, parentAftersalesSn, updateTime);
	}

	@Override
	public int insertOrUpdateBatch(List<TemuAftersalesReturnLogistics> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

}
