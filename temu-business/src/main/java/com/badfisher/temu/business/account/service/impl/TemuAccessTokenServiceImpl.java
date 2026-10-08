package com.badfisher.temu.business.account.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.account.mapper.TemuAccessTokenMapper;
import com.badfisher.temu.business.account.dto.TemuAccessTokenUpdateDTO;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.account.service.TemuAccessTokenService;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;

/**
 * Temu 授权Token 服务实现
 *
 * @author lenkor
 */
@Slf4j
@Service
public class TemuAccessTokenServiceImpl extends ServiceImpl<TemuAccessTokenMapper, TemuAccessToken>
		implements TemuAccessTokenService {

	/**
	 * Token刷新后有效期应从刷新时刻重新计算，不能继续使用授权记录最初的createTime， 否则旧记录更新Token后可能立即被判断为过期。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public TemuAccessToken updateAccessToken(TemuAccessTokenUpdateDTO dto) {
		validateUpdateParam(dto);

		TemuAccessToken token = getById(dto.getId());
		if (token == null || Integer.valueOf(1).equals(token.getDelFlag())) {
			throw new IllegalArgumentException("授权记录不存在或已删除: " + dto.getId());
		}

		Date updateTime = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(updateTime);
		calendar.add(Calendar.DAY_OF_MONTH, dto.getExpireDays());

		token.setAccessToken(dto.getAccessToken().trim());
		token.setExpireDays(dto.getExpireDays());
		token.setExpireTime(calendar.getTime());
		token.setUpdateTime(updateTime);
		if (dto.getChannelId() != null) {
			token.setChannelId(dto.getChannelId());
		}
		if (Integer.valueOf(0).equals(token.getType())) {
			token.setAuthRegion(TemuAuthRegion.CN.getCode());
		}
		else if (dto.getAuthRegion() != null && !dto.getAuthRegion().trim().isEmpty()) {
			TemuAuthRegion region = TemuAuthRegion.fromCode(dto.getAuthRegion());
			if (region == null) {
				throw new IllegalArgumentException("authRegion只支持US/GLOBAL/EU/CN");
			}
			token.setAuthRegion(region.getCode());
		}
		if (dto.getUpdateUserId() != null && !dto.getUpdateUserId().trim().isEmpty()) {
			token.setUpdateUserId(dto.getUpdateUserId().trim());
		}

		if (!updateById(token)) {
			throw new IllegalStateException("Token更新失败: " + dto.getId());
		}
		return token;
	}

	private void validateUpdateParam(TemuAccessTokenUpdateDTO dto) {
		if (dto == null || dto.getId() == null) {
			throw new IllegalArgumentException("授权记录ID不能为空");
		}
		if (dto.getAccessToken() == null || dto.getAccessToken().trim().isEmpty()) {
			throw new IllegalArgumentException("accessToken不能为空");
		}
		if (dto.getExpireDays() == null) {
			throw new IllegalArgumentException("expireDays不能为空");
		}
		if (dto.getExpireDays() < 1 || dto.getExpireDays() > 3650) {
			throw new IllegalArgumentException("expireDays必须在1~3650之间");
		}
	}

}
