package com.badfisher.temu.server.api.aftersales;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.badfisher.temu.business.aftersales.dto.AftersalesQueryDTO;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesProduct;
import com.badfisher.temu.business.aftersales.model.TemuAftersalesReturnLogistics;
import com.badfisher.temu.business.aftersales.model.TemuChildrenAftersales;
import com.badfisher.temu.business.aftersales.model.TemuParentAftersales;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesProductService;
import com.badfisher.temu.business.aftersales.service.TemuAftersalesReturnLogisticsService;
import com.badfisher.temu.business.aftersales.service.TemuChildrenAftersalesService;
import com.badfisher.temu.business.aftersales.service.TemuParentAftersalesService;
import com.badfisher.temu.business.aftersales.dto.AftersalesDetailVO;
import com.badfisher.temu.server.web.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Temu 售后单查询控制器 — 提供售后单及关联数据的查询能力
 *
 * @author lenkor
 */
@Slf4j
@Api(tags = "Temu 售后单查询")
@RestController
@RequestMapping("/temu/aftersales")
public class TemuAftersalesController {

	@Resource
	private TemuParentAftersalesService temuParentAftersalesService;

	@Resource
	private TemuChildrenAftersalesService temuChildrenAftersalesService;

	@Resource
	private TemuAftersalesProductService temuAftersalesProductService;

	@Resource
	private TemuAftersalesReturnLogisticsService temuAftersalesReturnLogisticsService;

	@ApiOperation(value = "查询售后聚合详情", notes = "一次返回父售后单、全部子售后单、售后商品和退货物流；channelId和site用于店铺隔离")
	@GetMapping("/detail/{parentAftersalesSn}")
	public Result<AftersalesDetailVO> getAftersalesDetail(
			@ApiParam(value = "父售后单号", required = true) @PathVariable String parentAftersalesSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam("卖家ID/店铺编码") @RequestParam(required = false) String sellerId) {
		TemuParentAftersales parent = temuParentAftersalesService
			.getOne(Wrappers.lambdaQuery(TemuParentAftersales.class)
				.eq(TemuParentAftersales::getParentAftersalesSn, parentAftersalesSn)
				.eq(TemuParentAftersales::getChannelId, channelId)
				.eq(TemuParentAftersales::getSite, site)
				.eq(sellerId != null, TemuParentAftersales::getSellerId, sellerId)
				.last("LIMIT 1"));
		if (parent == null) {
			return Result.fail("父售后单不存在: " + parentAftersalesSn);
		}

		List<TemuChildrenAftersales> children = temuChildrenAftersalesService
			.list(Wrappers.lambdaQuery(TemuChildrenAftersales.class)
				.eq(TemuChildrenAftersales::getParentAftersalesSn, parentAftersalesSn)
				.eq(TemuChildrenAftersales::getChannelId, channelId)
				.eq(TemuChildrenAftersales::getSite, site)
				.eq(sellerId != null, TemuChildrenAftersales::getSellerId, sellerId)
				.orderByAsc(TemuChildrenAftersales::getAftersalesSn));
		List<TemuAftersalesProduct> products = temuAftersalesProductService
			.list(Wrappers.lambdaQuery(TemuAftersalesProduct.class)
				.eq(TemuAftersalesProduct::getParentAftersalesSn, parentAftersalesSn)
				.eq(TemuAftersalesProduct::getChannelId, channelId)
				.eq(TemuAftersalesProduct::getSite, site)
				.eq(sellerId != null, TemuAftersalesProduct::getSellerId, sellerId)
				.orderByAsc(TemuAftersalesProduct::getAftersalesSn));
		List<TemuAftersalesReturnLogistics> logistics = temuAftersalesReturnLogisticsService
			.list(Wrappers.lambdaQuery(TemuAftersalesReturnLogistics.class)
				.eq(TemuAftersalesReturnLogistics::getParentAftersalesSn, parentAftersalesSn)
				.eq(TemuAftersalesReturnLogistics::getChannelId, channelId)
				.eq(TemuAftersalesReturnLogistics::getSite, site)
				.eq(sellerId != null, TemuAftersalesReturnLogistics::getSellerId, sellerId)
				.orderByAsc(TemuAftersalesReturnLogistics::getTrackingNumber));

		AftersalesDetailVO detail = new AftersalesDetailVO();
		detail.setParentAftersales(parent);
		detail.setChildrenAftersales(children);
		detail.setProducts(products);
		detail.setReturnLogistics(logistics);
		return Result.success(detail);
	}

	@ApiOperation("分页查询父售后单")
	@GetMapping("/parent/list")
	public Result<IPage<TemuParentAftersales>> listParentAftersales(AftersalesQueryDTO query) {
		Page<TemuParentAftersales> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuParentAftersales> result = temuParentAftersalesService.page(page, Wrappers
			.lambdaQuery(TemuParentAftersales.class)
			.like(query.getParentAftersalesSn() != null, TemuParentAftersales::getParentAftersalesSn,
					query.getParentAftersalesSn())
			.eq(query.getSellerId() != null, TemuParentAftersales::getSellerId, query.getSellerId())
			.eq(query.getChannelId() != null, TemuParentAftersales::getChannelId, query.getChannelId())
			.eq(query.getSite() != null, TemuParentAftersales::getSite, query.getSite())
			.like(query.getParentOrderSn() != null, TemuParentAftersales::getParentOrderSn, query.getParentOrderSn())
			.eq(query.getAftersalesStatus() != null, TemuParentAftersales::getParentAftersalesStatus,
					query.getAftersalesStatus())
			.eq(query.getAftersalesType() != null, TemuParentAftersales::getAftersalesType, query.getAftersalesType())
			.ge(query.getCreateTimeStart() != null, TemuParentAftersales::getCreateTimeApi,
					query.getCreateTimeStart() == null ? null : query.getCreateTimeStart().getTime() / 1000L)
			.le(query.getCreateTimeEnd() != null, TemuParentAftersales::getCreateTimeApi,
					query.getCreateTimeEnd() == null ? null : query.getCreateTimeEnd().getTime() / 1000L)
			.orderByDesc(TemuParentAftersales::getCreateTimeApi));
		return Result.success(result);
	}

	@ApiOperation("根据父售后单号查询父售后单详情")
	@GetMapping("/parent/{parentAftersalesSn}")
	public Result<TemuParentAftersales> getParentAftersales(
			@ApiParam(value = "父售后单号", required = true) @PathVariable String parentAftersalesSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		TemuParentAftersales aftersales = temuParentAftersalesService
			.getOne(Wrappers.lambdaQuery(TemuParentAftersales.class)
				.eq(TemuParentAftersales::getParentAftersalesSn, parentAftersalesSn)
				.eq(TemuParentAftersales::getChannelId, channelId)
				.eq(TemuParentAftersales::getSite, site)
				.eq(sellerId != null, TemuParentAftersales::getSellerId, sellerId)
				.last("LIMIT 1"));
		if (aftersales == null) {
			return Result.fail("父售后单不存在: " + parentAftersalesSn);
		}
		return Result.success(aftersales);
	}

	@ApiOperation("分页查询子售后单")
	@GetMapping("/children/list")
	public Result<IPage<TemuChildrenAftersales>> listChildrenAftersales(AftersalesQueryDTO query) {
		Page<TemuChildrenAftersales> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuChildrenAftersales> result = temuChildrenAftersalesService.page(page, Wrappers
			.lambdaQuery(TemuChildrenAftersales.class)
			.like(query.getAftersalesSn() != null, TemuChildrenAftersales::getAftersalesSn, query.getAftersalesSn())
			.like(query.getParentAftersalesSn() != null, TemuChildrenAftersales::getParentAftersalesSn,
					query.getParentAftersalesSn())
			.eq(query.getSellerId() != null, TemuChildrenAftersales::getSellerId, query.getSellerId())
			.eq(query.getChannelId() != null, TemuChildrenAftersales::getChannelId, query.getChannelId())
			.eq(query.getSite() != null, TemuChildrenAftersales::getSite, query.getSite())
			.eq(query.getAftersalesStatus() != null, TemuChildrenAftersales::getAftersalesStatus,
					query.getAftersalesStatus())
			.eq(query.getAftersalesType() != null, TemuChildrenAftersales::getAftersalesType, query.getAftersalesType())
			.orderByDesc(TemuChildrenAftersales::getCreateTime));
		return Result.success(result);
	}

	@ApiOperation("根据父售后单号查询全部子售后单")
	@GetMapping("/children/parent/{parentAftersalesSn}")
	public Result<List<TemuChildrenAftersales>> listChildrenByParent(
			@ApiParam(value = "父售后单号", required = true) @PathVariable String parentAftersalesSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		List<TemuChildrenAftersales> list = temuChildrenAftersalesService
			.list(Wrappers.lambdaQuery(TemuChildrenAftersales.class)
				.eq(TemuChildrenAftersales::getParentAftersalesSn, parentAftersalesSn)
				.eq(TemuChildrenAftersales::getChannelId, channelId)
				.eq(TemuChildrenAftersales::getSite, site)
				.eq(sellerId != null, TemuChildrenAftersales::getSellerId, sellerId)
				.orderByDesc(TemuChildrenAftersales::getCreateTime));
		return Result.success(list);
	}

	@ApiOperation("根据子售后单号查询子售后单详情")
	@GetMapping("/children/{aftersalesSn}")
	public Result<TemuChildrenAftersales> getChildAftersales(
			@ApiParam(value = "子售后单号", required = true) @PathVariable String aftersalesSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		TemuChildrenAftersales aftersales = temuChildrenAftersalesService
			.getOne(Wrappers.lambdaQuery(TemuChildrenAftersales.class)
				.eq(TemuChildrenAftersales::getAftersalesSn, aftersalesSn)
				.eq(TemuChildrenAftersales::getChannelId, channelId)
				.eq(TemuChildrenAftersales::getSite, site)
				.eq(sellerId != null, TemuChildrenAftersales::getSellerId, sellerId)
				.last("LIMIT 1"));
		if (aftersales == null) {
			return Result.fail("子售后单不存在: " + aftersalesSn);
		}
		return Result.success(aftersales);
	}

	@ApiOperation("根据子售后单号查询售后产品列表")
	@GetMapping("/product/{aftersalesSn}")
	public Result<List<TemuAftersalesProduct>> listProducts(
			@ApiParam(value = "子售后单号", required = true) @PathVariable String aftersalesSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		List<TemuAftersalesProduct> list = temuAftersalesProductService
			.list(Wrappers.lambdaQuery(TemuAftersalesProduct.class)
				.eq(TemuAftersalesProduct::getAftersalesSn, aftersalesSn)
				.eq(TemuAftersalesProduct::getChannelId, channelId)
				.eq(TemuAftersalesProduct::getSite, site)
				.eq(sellerId != null, TemuAftersalesProduct::getSellerId, sellerId));
		return Result.success(list);
	}

	@ApiOperation("根据父售后单号查询退货物流")
	@GetMapping("/return-logistics/{parentAftersalesSn}")
	public Result<List<TemuAftersalesReturnLogistics>> listReturnLogistics(
			@ApiParam(value = "父售后单号", required = true) @PathVariable String parentAftersalesSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		List<TemuAftersalesReturnLogistics> list = temuAftersalesReturnLogisticsService
			.list(Wrappers.lambdaQuery(TemuAftersalesReturnLogistics.class)
				.eq(TemuAftersalesReturnLogistics::getParentAftersalesSn, parentAftersalesSn)
				.eq(TemuAftersalesReturnLogistics::getChannelId, channelId)
				.eq(TemuAftersalesReturnLogistics::getSite, site)
				.eq(sellerId != null, TemuAftersalesReturnLogistics::getSellerId, sellerId)
				.orderByAsc(TemuAftersalesReturnLogistics::getId));
		return Result.success(list);
	}

}
