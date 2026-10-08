package com.badfisher.temu.business.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.product.model.TemuProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Temu 商品档案 Mapper。 */
@Mapper
public interface TemuProductMapper extends BaseMapper<TemuProduct> {

	/** 利用唯一键 (app_id, seller_id, product_id) 批量新增或更新商品。 */
	int insertOrUpdateBatch(@Param("list") List<TemuProduct> list);

	/** 将本次完整快照中未出现的历史商品标记为删除。 */
	int markMissingDeleted(@Param("appId") String appId, @Param("sellerId") String sellerId,
			@Param("syncTaskId") Long syncTaskId);

}
