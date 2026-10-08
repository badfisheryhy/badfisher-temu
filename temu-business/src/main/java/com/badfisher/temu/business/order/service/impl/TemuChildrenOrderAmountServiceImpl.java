package com.badfisher.temu.business.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.order.mapper.TemuChildrenOrderAmountMapper;
import com.badfisher.temu.business.order.model.TemuChildrenOrderAmount;
import com.badfisher.temu.business.order.service.TemuChildrenOrderAmountService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemuChildrenOrderAmountServiceImpl extends
		ServiceImpl<TemuChildrenOrderAmountMapper, TemuChildrenOrderAmount> implements TemuChildrenOrderAmountService {

	@Override
	public void insertOrUpdateBatch(List<TemuChildrenOrderAmount> list) {
		if (list == null || list.isEmpty()) {
			return;
		}
		baseMapper.insertOrUpdateBatch(list);
	}

}
