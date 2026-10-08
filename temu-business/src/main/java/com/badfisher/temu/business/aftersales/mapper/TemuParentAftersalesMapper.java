package com.badfisher.temu.business.aftersales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Temu 父售后单 Mapper
 *
 * @author lenkor
 */
@Mapper
public interface TemuParentAftersalesMapper extends BaseMapper<TemuParentAftersales> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (parent_aftersales_sn, seller_id)
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuParentAftersales> list);

}
