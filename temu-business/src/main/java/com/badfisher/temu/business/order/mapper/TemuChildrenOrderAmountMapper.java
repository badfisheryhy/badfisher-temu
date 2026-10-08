package com.badfisher.temu.business.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.order.model.TemuChildrenOrderAmount;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TemuChildrenOrderAmountMapper extends BaseMapper<TemuChildrenOrderAmount> {

	/**
	 * 批量 upsert：INSERT ... ON DUPLICATE KEY UPDATE
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuChildrenOrderAmount> list);

}
