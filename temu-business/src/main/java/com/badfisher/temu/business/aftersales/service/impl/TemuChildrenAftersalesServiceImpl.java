package com.badfisher.temu.business.aftersales.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.aftersales.mapper.TemuChildrenAftersalesMapper;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;
import com.badfisher.temu.business.aftersales.service.TemuChildrenAftersalesService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Temu 子售后单 服务实现
 *
 * @author lenkor
 */
@Service
public class TemuChildrenAftersalesServiceImpl extends ServiceImpl<TemuChildrenAftersalesMapper, TemuChildrenAftersales>
		implements TemuChildrenAftersalesService {

	@Override
	public int insertOrUpdateBatch(List<TemuChildrenAftersales> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

}
