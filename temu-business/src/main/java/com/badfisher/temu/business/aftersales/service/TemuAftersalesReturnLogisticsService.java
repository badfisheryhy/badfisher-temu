package com.badfisher.temu.business.aftersales.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;

import java.util.Date;
import java.util.List;

/** Temu售后退货物流服务。 */
public interface TemuAftersalesReturnLogisticsService extends IService<TemuAftersalesReturnLogistics> {

	int softDeleteByParent(Integer channelId, String site, String sellerId, String parentAftersalesSn, Date updateTime);

	int insertOrUpdateBatch(List<TemuAftersalesReturnLogistics> list);

}
