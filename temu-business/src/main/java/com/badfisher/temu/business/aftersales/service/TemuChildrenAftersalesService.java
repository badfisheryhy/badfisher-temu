package com.badfisher.temu.business.aftersales.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;

import java.util.List;

/**
 * Temu 子售后单 服务接口
 *
 * @author lenkor
 */
public interface TemuChildrenAftersalesService extends IService<TemuChildrenAftersales> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (aftersales_sn, seller_id)
	 */
	int insertOrUpdateBatch(List<TemuChildrenAftersales> list);

}
