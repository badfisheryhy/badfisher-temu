package com.badfisher.temu.business.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.product.model.TemuProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Temu 商品 SKU Mapper。 */
@Mapper
public interface TemuProductSkuMapper extends BaseMapper<TemuProductSku> {

	/** 利用唯一键 (app_id, seller_id, product_sku_id) 批量新增或更新SKU。 */
	int insertOrUpdateBatch(@Param("list") List<TemuProductSku> list);

	/** 将本次完整快照中未出现的历史SKU标记为删除。 */
	int markMissingDeleted(@Param("appId") String appId, @Param("sellerId") String sellerId,
			@Param("syncTaskId") Long syncTaskId);

}
