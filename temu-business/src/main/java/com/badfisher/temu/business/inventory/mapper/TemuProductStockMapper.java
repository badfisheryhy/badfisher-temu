package com.badfisher.temu.business.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.inventory.model.TemuProductStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Temu商品仓库库存Mapper。 */
@Mapper
public interface TemuProductStockMapper extends BaseMapper<TemuProductStock> {

	/** 批量写入一个店铺的新库存快照。 */
	int insertBatch(@Param("list") List<TemuProductStock> list);

	/** 批量追加历史库存快照，默认由清理任务保留最近7天。 */
	int insertHistoryBatch(@Param("list") List<TemuProductStock> list);

	/** 物理删除一个店铺的旧快照；必须与新快照插入处于同一事务。 */
	int deleteAccountSnapshot(@Param("appId") String appId, @Param("sellerId") String sellerId);

	/** 分批删除指定时间以前的历史库存快照。 */
	int deleteExpiredHistory(@Param("cutoffTime") java.util.Date cutoffTime, @Param("limit") int limit);

}
