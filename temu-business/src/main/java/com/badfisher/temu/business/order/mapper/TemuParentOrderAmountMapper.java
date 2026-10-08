package com.badfisher.temu.business.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.order.model.TemuParentOrderAmount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TemuParentOrderAmountMapper extends BaseMapper<TemuParentOrderAmount> {

	/**
	 * 批量 upsert：INSERT ... ON DUPLICATE KEY UPDATE
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuParentOrderAmount> list);

}
