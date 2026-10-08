package com.badfisher.temu.business.aftersales.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;

import java.util.List;

/**
 * Temu 父售后单 服务接口
 *
 * @author lenkor
 */
public interface TemuParentAftersalesService extends IService<TemuParentAftersales> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (parent_aftersales_sn, seller_id)
	 */
	int insertOrUpdateBatch(List<TemuParentAftersales> list);

}
