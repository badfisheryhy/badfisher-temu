package com.badfisher.temu.business.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.order.mapper.TemuOrderProductMapper;
import com.badfisher.temu.business.order.model.TemuOrderProduct;
import com.badfisher.temu.business.order.service.TemuOrderProductService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemuOrderProductServiceImpl extends ServiceImpl<TemuOrderProductMapper, TemuOrderProduct>
		implements TemuOrderProductService {

	@Override
	public int insertOrUpdateBatch(List<TemuOrderProduct> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

}
