package com.badfisher.temu.business.account.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.account.dto.TemuAccessTokenUpdateDTO;
import com.badfisher.temu.business.account.model.TemuAccessToken;

/**
 * Temu 授权Token 服务接口
 *
 * @author lenkor
 */
public interface TemuAccessTokenService extends IService<TemuAccessToken> {

	/**
	 * 更新 accessToken，并以本次更新时间为起点计算过期日期。
	 * @param dto Token更新参数
	 * @return 更新后的授权信息
	 */
	TemuAccessToken updateAccessToken(TemuAccessTokenUpdateDTO dto);

}
