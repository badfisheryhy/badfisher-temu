package com.badfisher.temu.business.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Temu 授权Token Mapper
 *
 * @author lenkor
 */
@Mapper
public interface TemuAccessTokenMapper extends BaseMapper<TemuAccessToken> {

}
