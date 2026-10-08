package com.badfisher.temu.business.aftersales.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.aftersales.mapper.TemuAftersalesProductMapper;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesProductService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Temu 售后产品 服务实现
 *
 * @author lenkor
 */
@Service
public class TemuAftersalesProductServiceImpl extends ServiceImpl<TemuAftersalesProductMapper, TemuAftersalesProduct>
		implements TemuAftersalesProductService {

	@Override
	public int insertOrUpdateBatch(List<TemuAftersalesProduct> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

}
