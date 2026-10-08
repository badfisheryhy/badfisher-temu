package com.badfisher.temu.business.aftersales.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.aftersales.mapper.TemuParentAftersalesMapper;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;
import com.badfisher.temu.business.aftersales.service.TemuParentAftersalesService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Temu 父售后单 服务实现
 *
 * @author lenkor
 */
@Service
public class TemuParentAftersalesServiceImpl extends ServiceImpl<TemuParentAftersalesMapper, TemuParentAftersales>
		implements TemuParentAftersalesService {

	@Override
	public int insertOrUpdateBatch(List<TemuParentAftersales> list) {
		return baseMapper.insertOrUpdateBatch(list);
	}

}
