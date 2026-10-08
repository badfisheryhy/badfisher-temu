package com.badfisher.temu.business.aftersales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Temu 子售后单 Mapper
 *
 * @author lenkor
 */
@Mapper
public interface TemuChildrenAftersalesMapper extends BaseMapper<TemuChildrenAftersales> {

	/**
	 * 批量 INSERT ... ON DUPLICATE KEY UPDATE — 利用唯一键 (aftersales_sn, seller_id)
	 */
	int insertOrUpdateBatch(@Param("list") List<TemuChildrenAftersales> list);

}
