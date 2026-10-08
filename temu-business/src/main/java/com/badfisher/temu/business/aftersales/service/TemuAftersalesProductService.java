package com.badfisher.temu.business.aftersales.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;

import java.util.List;

/**
 * Temu 售后产品 服务接口
 *
 * @author lenkor
 */
public interface TemuAftersalesProductService extends IService<TemuAftersalesProduct> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (aftersales_sn, product_sku_id)
	 */
	int insertOrUpdateBatch(List<TemuAftersalesProduct> list);

}
