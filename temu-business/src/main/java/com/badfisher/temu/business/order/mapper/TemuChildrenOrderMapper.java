package com.badfisher.temu.business.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.order.model.TemuChildrenOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TemuChildrenOrderMapper extends BaseMapper<TemuChildrenOrder> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (parent_order_sn, seller_id,
	 * order_sn) 一条 SQL 完成批量 upsert，避免 MyBatis-Plus saveOrUpdateBatch 逐条 roundtrip
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuChildrenOrder> list);

}
