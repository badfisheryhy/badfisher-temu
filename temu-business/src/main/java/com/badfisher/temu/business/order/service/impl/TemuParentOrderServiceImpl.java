package com.badfisher.temu.business.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.order.mapper.TemuParentOrderMapper;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class TemuParentOrderServiceImpl extends ServiceImpl<TemuParentOrderMapper, TemuParentOrder>
		implements TemuParentOrderService {

	@Resource
	private TemuParentOrderMapper temuParentOrderMapper;

	@Override
	public List<String> listOrderSnsMissingAmount(String sellerId, long sinceSeconds) {
		return temuParentOrderMapper.selectOrderSnsMissingAmount(sellerId, sinceSeconds);
	}

	@Override
	public void insertOrUpdateBatch(List<TemuParentOrder> list) {
		if (list == null || list.isEmpty()) {
			return;
		}
		baseMapper.insertOrUpdateBatch(list);
	}

}
