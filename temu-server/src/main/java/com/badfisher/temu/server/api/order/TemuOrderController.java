package com.badfisher.temu.server.api.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.badfisher.temu.business.order.dto.ChildrenOrderQueryDTO;
import com.badfisher.temu.business.order.dto.OrderQueryDTO;
import com.badfisher.temu.business.order.model.TemuChildrenOrder;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.order.model.TemuOrderProduct;
import com.badfisher.temu.business.order.model.TemuParentOrder;
import com.badfisher.temu.business.order.service.TemuChildrenOrderService;
import com.badfisher.temu.business.order.service.TemuOrderAddressService;
import com.badfisher.temu.business.order.service.TemuOrderProductService;
import com.badfisher.temu.business.order.service.TemuParentOrderService;
import com.badfisher.temu.business.order.dto.OrderDetailVO;
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
 * Temu 订单查询控制器 — 提供订单及关联数据的查询能力
 *
 * @author lenkor
 */
@Slf4j
@Api(tags = "Temu 订单查询")
@RestController
@RequestMapping("/temu/order")
public class TemuOrderController {

	@Resource
	private TemuParentOrderService temuParentOrderService;

	@Resource
	private TemuChildrenOrderService temuChildrenOrderService;

	@Resource
	private TemuOrderProductService temuOrderProductService;

	@Resource
	private TemuOrderAddressService temuOrderAddressService;

	@ApiOperation(value = "查询订单聚合详情", notes = "一次返回父订单、全部子订单、订单货品和收货地址；channelId和site用于店铺隔离")
	@GetMapping("/detail/{parentOrderSn}")
	public Result<OrderDetailVO> getOrderDetail(
			@ApiParam(value = "父订单号", required = true) @PathVariable String parentOrderSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam("卖家ID/店铺编码") @RequestParam(required = false) String sellerId) {
		TemuParentOrder parentOrder = temuParentOrderService.getOne(Wrappers.lambdaQuery(TemuParentOrder.class)
			.eq(TemuParentOrder::getParentOrderSn, parentOrderSn)
			.eq(TemuParentOrder::getChannelId, channelId)
			.eq(TemuParentOrder::getSite, site)
			.eq(sellerId != null, TemuParentOrder::getSellerId, sellerId)
			.last("LIMIT 1"));
		if (parentOrder == null) {
			return Result.fail("父订单不存在: " + parentOrderSn);
		}

		List<TemuChildrenOrder> children = temuChildrenOrderService.list(Wrappers.lambdaQuery(TemuChildrenOrder.class)
			.eq(TemuChildrenOrder::getParentOrderSn, parentOrderSn)
			.eq(TemuChildrenOrder::getChannelId, channelId)
			.eq(TemuChildrenOrder::getSite, site)
			.eq(sellerId != null, TemuChildrenOrder::getSellerId, sellerId)
			.orderByAsc(TemuChildrenOrder::getOrderSn));
		List<TemuOrderProduct> products = temuOrderProductService.list(Wrappers.lambdaQuery(TemuOrderProduct.class)
			.eq(TemuOrderProduct::getParentOrderSn, parentOrderSn)
			.eq(TemuOrderProduct::getChannelId, channelId)
			.eq(TemuOrderProduct::getSite, site)
			.eq(sellerId != null, TemuOrderProduct::getSellerId, sellerId)
			.orderByAsc(TemuOrderProduct::getChildrenOrderNo));
		TemuOrderAddress address = temuOrderAddressService.getOne(Wrappers.lambdaQuery(TemuOrderAddress.class)
			.eq(TemuOrderAddress::getParentOrderSn, parentOrderSn)
			.eq(TemuOrderAddress::getChannelId, channelId)
			.eq(TemuOrderAddress::getSite, site)
			.eq(sellerId != null, TemuOrderAddress::getSellerId, sellerId)
			.last("LIMIT 1"));

		OrderDetailVO detail = new OrderDetailVO();
		detail.setParentOrder(parentOrder);
		detail.setChildrenOrders(children);
		detail.setProducts(products);
		detail.setAddress(address);
		return Result.success(detail);
	}

	@ApiOperation("分页查询父订单")
	@GetMapping("/parent/list")
	public Result<IPage<TemuParentOrder>> listParentOrders(OrderQueryDTO query) {
		Page<TemuParentOrder> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuParentOrder> result = temuParentOrderService.page(page,
				Wrappers.lambdaQuery(TemuParentOrder.class)
					.like(query.getParentOrderSn() != null, TemuParentOrder::getParentOrderSn, query.getParentOrderSn())
					.eq(query.getSellerId() != null, TemuParentOrder::getSellerId, query.getSellerId())
					.eq(query.getChannelId() != null, TemuParentOrder::getChannelId, query.getChannelId())
					.eq(query.getSite() != null, TemuParentOrder::getSite, query.getSite())
					.eq(query.getParentOrderStatus() != null, TemuParentOrder::getParentOrderStatus,
							query.getParentOrderStatus())
					.like(query.getBuyerName() != null, TemuParentOrder::getBuyerName, query.getBuyerName())
					.ge(query.getParentOrderTimeStart() != null, TemuParentOrder::getParentOrderTime,
							query.getParentOrderTimeStart())
					.le(query.getParentOrderTimeEnd() != null, TemuParentOrder::getParentOrderTime,
							query.getParentOrderTimeEnd())
					.orderByDesc(TemuParentOrder::getCreateTime));
		return Result.success(result);
	}

	@ApiOperation("根据父单号查询父订单详情")
	@GetMapping("/parent/{parentOrderSn}")
	public Result<TemuParentOrder> getParentOrder(
			@ApiParam(value = "父单号", required = true) @PathVariable String parentOrderSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		TemuParentOrder order = temuParentOrderService.getOne(Wrappers.lambdaQuery(TemuParentOrder.class)
			.eq(TemuParentOrder::getParentOrderSn, parentOrderSn)
			.eq(TemuParentOrder::getChannelId, channelId)
			.eq(TemuParentOrder::getSite, site)
			.eq(sellerId != null, TemuParentOrder::getSellerId, sellerId)
			.last("LIMIT 1"));
		if (order == null) {
			return Result.fail("父订单不存在: " + parentOrderSn);
		}
		return Result.success(order);
	}

	@ApiOperation("分页查询子订单（按父单号）")
	@GetMapping("/children/list")
	public Result<IPage<TemuChildrenOrder>> listChildrenOrders(ChildrenOrderQueryDTO query) {
		if (query.getParentOrderSn() == null) {
			return Result.fail("父单号不能为空");
		}
		Page<TemuChildrenOrder> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuChildrenOrder> result = temuChildrenOrderService.page(page,
				Wrappers.lambdaQuery(TemuChildrenOrder.class)
					.eq(TemuChildrenOrder::getParentOrderSn, query.getParentOrderSn())
					.eq(query.getSellerId() != null, TemuChildrenOrder::getSellerId, query.getSellerId())
					.eq(query.getChannelId() != null, TemuChildrenOrder::getChannelId, query.getChannelId())
					.eq(query.getSite() != null, TemuChildrenOrder::getSite, query.getSite())
					.eq(query.getOrderStatus() != null, TemuChildrenOrder::getOrderStatus, query.getOrderStatus())
					.orderByDesc(TemuChildrenOrder::getCreateTime));
		return Result.success(result);
	}

	@ApiOperation("根据父单号查询全部子订单")
	@GetMapping("/children/{parentOrderSn}")
	public Result<List<TemuChildrenOrder>> listChildrenByParent(
			@ApiParam(value = "父单号", required = true) @PathVariable String parentOrderSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		List<TemuChildrenOrder> list = temuChildrenOrderService.list(Wrappers.lambdaQuery(TemuChildrenOrder.class)
			.eq(TemuChildrenOrder::getParentOrderSn, parentOrderSn)
			.eq(TemuChildrenOrder::getChannelId, channelId)
			.eq(TemuChildrenOrder::getSite, site)
			.eq(sellerId != null, TemuChildrenOrder::getSellerId, sellerId)
			.orderByDesc(TemuChildrenOrder::getCreateTime));
		return Result.success(list);
	}

	@ApiOperation("根据子单号查询订单货品列表")
	@GetMapping("/product/{childrenOrderNo}")
	public Result<List<TemuOrderProduct>> listProducts(
			@ApiParam(value = "子订单号", required = true) @PathVariable String childrenOrderNo,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site) {
		List<TemuOrderProduct> list = temuOrderProductService.list(Wrappers.lambdaQuery(TemuOrderProduct.class)
			.eq(TemuOrderProduct::getChildrenOrderNo, childrenOrderNo)
			.eq(TemuOrderProduct::getChannelId, channelId)
			.eq(TemuOrderProduct::getSite, site));
		return Result.success(list);
	}

	@ApiOperation("根据父单号查询收货地址")
	@GetMapping("/address/{parentOrderSn}")
	public Result<TemuOrderAddress> getAddress(
			@ApiParam(value = "父单号", required = true) @PathVariable String parentOrderSn,
			@ApiParam(value = "中台渠道编号", required = true) @RequestParam Integer channelId,
			@ApiParam(value = "站点", required = true) @RequestParam String site,
			@ApiParam(value = "卖家ID") String sellerId) {
		TemuOrderAddress address = temuOrderAddressService.getOne(Wrappers.lambdaQuery(TemuOrderAddress.class)
			.eq(TemuOrderAddress::getParentOrderSn, parentOrderSn)
			.eq(TemuOrderAddress::getChannelId, channelId)
			.eq(TemuOrderAddress::getSite, site)
			.eq(sellerId != null, TemuOrderAddress::getSellerId, sellerId)
			.last("LIMIT 1"));
		if (address == null) {
			return Result.fail("收货地址不存在: " + parentOrderSn);
		}
		return Result.success(address);
	}

}
