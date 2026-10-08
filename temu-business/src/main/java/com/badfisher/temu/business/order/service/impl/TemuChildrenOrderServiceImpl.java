package com.badfisher.temu.business.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.order.mapper.TemuChildrenOrderMapper;
import com.badfisher.temu.business.order.model.TemuChildrenOrder;
import com.badfisher.temu.business.order.service.TemuChildrenOrderService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemuChildrenOrderServiceImpl extends ServiceImpl<TemuChildrenOrderMapper, TemuChildrenOrder>
		implements TemuChildrenOrderService {

	@Override
	public int insertOrUpdateBatch(List<TemuChildrenOrder> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

}
