package com.badfisher.temu.business.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.audit.dto.ApiLogQueryDTO;
import com.badfisher.temu.business.audit.model.TemuApiLog;
import com.badfisher.temu.business.audit.dto.ApiLogSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * API 调用日志 Mapper
 *
 * @author lenkor
 */
@Mapper
public interface TemuApiLogMapper extends BaseMapper<TemuApiLog> {

	/** 分批删除指定时间以前的API调用日志。 */
	int deleteExpiredLogs(@Param("cutoffTime") Date cutoffTime, @Param("limit") int limit);

	/** 按API名称聚合调用次数、耗时和失败次数。 */
	List<ApiLogSummaryVO> selectSummary(@Param("query") ApiLogQueryDTO query);

}
