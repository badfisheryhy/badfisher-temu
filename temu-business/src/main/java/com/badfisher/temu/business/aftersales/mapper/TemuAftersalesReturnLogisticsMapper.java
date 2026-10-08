package com.badfisher.temu.business.aftersales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Temu售后退货物流Mapper。 */
@Mapper
public interface TemuAftersalesReturnLogisticsMapper extends BaseMapper<TemuAftersalesReturnLogistics> {

	/** 将一个父售后单的旧物流记录标记为删除。 */
	int softDeleteByParent(@Param("channelId") Integer channelId, @Param("site") String site,
			@Param("sellerId") String sellerId, @Param("parentAftersalesSn") String parentAftersalesSn,
			@Param("updateTime") java.util.Date updateTime);

	/** 批量写入并恢复本次接口返回的有效物流记录。 */
	int insertOrUpdateBatch(@Param("list") List<TemuAftersalesReturnLogistics> list);

}
