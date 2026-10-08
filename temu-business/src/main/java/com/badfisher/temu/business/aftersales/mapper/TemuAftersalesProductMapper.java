package com.badfisher.temu.business.aftersales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Temu 售后产品 Mapper
 *
 * @author lenkor
 */
@Mapper
public interface TemuAftersalesProductMapper extends BaseMapper<TemuAftersalesProduct> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (aftersales_sn, product_sku_id)
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuAftersalesProduct> list);

}
