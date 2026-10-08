package com.badfisher.temu.business.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.order.model.TemuOrderProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TemuOrderProductMapper extends BaseMapper<TemuOrderProduct> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (parent_order_sn, children_order_no,
	 * product_sku_id) 一条 SQL 完成批量 upsert，避免 MyBatis-Plus saveOrUpdateBatch 逐条 roundtrip
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuOrderProduct> list);

}
