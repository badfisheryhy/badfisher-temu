package com.badfisher.temu.business.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.order.mapper.TemuParentOrderAmountMapper;
import com.badfisher.temu.business.order.model.TemuParentOrderAmount;
import com.badfisher.temu.business.order.service.TemuParentOrderAmountService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemuParentOrderAmountServiceImpl extends ServiceImpl<TemuParentOrderAmountMapper, TemuParentOrderAmount>
		implements TemuParentOrderAmountService {

	@Override
	public void insertOrUpdateBatch(List<TemuParentOrderAmount> list) {
		if (list == null || list.isEmpty()) {
			return;
		}
		baseMapper.insertOrUpdateBatch(list);
	}

}
