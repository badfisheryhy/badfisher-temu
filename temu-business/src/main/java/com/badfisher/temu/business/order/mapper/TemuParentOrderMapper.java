package com.badfisher.temu.business.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TemuParentOrderMapper extends BaseMapper<TemuParentOrder> {

	/**
	 * 查询指定卖家自指定时间起、无金额记录的父订单号
	 * @param sellerId 卖家ID
	 * @param sinceSeconds 时间起点(epoch秒)，按父订单 create_time 过滤
	 * @return 待拉取金额的父订单号列表
	 */
	List<String> selectOrderSnsMissingAmount(@Param("sellerId") String sellerId,
			@Param("sinceSeconds") long sinceSeconds);

	/**
	 * 批量 upsert：INSERT ... ON DUPLICATE KEY UPDATE，一次 SQL 完成所有写入
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuParentOrder> list);

}
