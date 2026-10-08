package com.badfisher.temu.server.api.inventory;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.badfisher.temu.business.inventory.dto.InventoryQueryDTO;
import com.badfisher.temu.business.inventory.model.TemuProductStock;
import com.badfisher.temu.business.warehouse.model.TemuWarehouse;
import com.badfisher.temu.business.inventory.service.TemuProductStockService;
import com.badfisher.temu.business.warehouse.service.TemuWarehouseService;
import com.badfisher.temu.server.web.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Temu 仓库与当前库存查询接口。
 * <p>
 * 库存为最近一次完整同步成功后的当前快照，同一 SKU 可在多个仓库各有一条记录。
 * </p>
 *
 * @author lenkor
 */
@Api(tags = "Temu 仓库库存查询")
@RestController
@RequestMapping("/temu/inventory")
@RequiredArgsConstructor
public class TemuInventoryController {

	private final TemuWarehouseService warehouseService;

	private final TemuProductStockService productStockService;

	@ApiOperation(value = "分页查询仓库", notes = "按卖家、仓库ID、仓库名称和默认仓标识筛选")
	@GetMapping("/warehouse/list")
	public Result<IPage<TemuWarehouse>> listWarehouses(InventoryQueryDTO query) {
		Page<TemuWarehouse> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuWarehouse> result = warehouseService.page(page, Wrappers.lambdaQuery(TemuWarehouse.class)
			.eq(query.getSellerId() != null, TemuWarehouse::getSellerId, query.getSellerId())
			.eq(query.getWarehouseId() != null, TemuWarehouse::getWarehouseId, query.getWarehouseId())
			.like(query.getWarehouseName() != null, TemuWarehouse::getWarehouseName, query.getWarehouseName())
			.eq(query.getDefaultWarehouse() != null, TemuWarehouse::getDefaultWarehouse, query.getDefaultWarehouse())
			.orderByDesc(TemuWarehouse::getDefaultWarehouse)
			.orderByAsc(TemuWarehouse::getWarehouseId));
		return Result.success(result);
	}

	@ApiOperation(value = "查询仓库详情", notes = "仓库在卖家维度下唯一，必须同时传入sellerId")
	@GetMapping("/warehouse/{warehouseId}")
	public Result<TemuWarehouse> getWarehouse(
			@ApiParam(value = "仓库ID", required = true) @PathVariable String warehouseId,
			@ApiParam(value = "卖家ID/店铺编码", required = true) @RequestParam String sellerId) {
		TemuWarehouse warehouse = warehouseService.getOne(Wrappers.lambdaQuery(TemuWarehouse.class)
			.eq(TemuWarehouse::getSellerId, sellerId)
			.eq(TemuWarehouse::getWarehouseId, warehouseId)
			.last("LIMIT 1"));
		return warehouse == null ? Result.fail("仓库不存在: " + warehouseId) : Result.success(warehouse);
	}

	@ApiOperation(value = "分页查询当前库存", notes = "查询最近一次成功同步的SKU仓库库存快照，支持库存区间和快照时间筛选")
	@GetMapping("/stock/list")
	public Result<IPage<TemuProductStock>> listStocks(InventoryQueryDTO query) {
		Page<TemuProductStock> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuProductStock> result = productStockService.page(page, Wrappers.lambdaQuery(TemuProductStock.class)
			.eq(query.getAppId() != null, TemuProductStock::getAppId, query.getAppId())
			.eq(query.getSellerId() != null, TemuProductStock::getSellerId, query.getSellerId())
			.like(query.getShopName() != null, TemuProductStock::getShopName, query.getShopName())
			.eq(query.getProductSkcId() != null, TemuProductStock::getProductSkcId, query.getProductSkcId())
			.eq(query.getProductSkuId() != null, TemuProductStock::getProductSkuId, query.getProductSkuId())
			.eq(query.getWarehouseId() != null, TemuProductStock::getWarehouseId, query.getWarehouseId())
			.like(query.getWarehouseName() != null, TemuProductStock::getWarehouseName, query.getWarehouseName())
			.ge(query.getMinStockQuantity() != null, TemuProductStock::getStockQuantity, query.getMinStockQuantity())
			.le(query.getMaxStockQuantity() != null, TemuProductStock::getStockQuantity, query.getMaxStockQuantity())
			.ge(query.getSnapshotTimeStart() != null, TemuProductStock::getSnapshotTime, query.getSnapshotTimeStart())
			.le(query.getSnapshotTimeEnd() != null, TemuProductStock::getSnapshotTime, query.getSnapshotTimeEnd())
			.orderByDesc(TemuProductStock::getSnapshotTime)
			.orderByAsc(TemuProductStock::getProductSkuId));
		return Result.success(result);
	}

	@ApiOperation(value = "查询SKU在各仓库的当前库存", notes = "返回指定应用主体和卖家下，一个SKU在全部或指定仓库的当前库存")
	@GetMapping("/stock/sku/{productSkuId}")
	public Result<List<TemuProductStock>> listStockBySku(
			@ApiParam(value = "Temu商品SKU ID", required = true) @PathVariable Long productSkuId,
			@ApiParam(value = "Temu应用主体ID", required = true) @RequestParam String appId,
			@ApiParam(value = "卖家ID/店铺编码", required = true) @RequestParam String sellerId,
			@ApiParam("仓库ID；不传则查询全部仓库") @RequestParam(required = false) String warehouseId) {
		List<TemuProductStock> list = productStockService.list(Wrappers.lambdaQuery(TemuProductStock.class)
			.eq(TemuProductStock::getAppId, appId)
			.eq(TemuProductStock::getSellerId, sellerId)
			.eq(TemuProductStock::getProductSkuId, productSkuId)
			.eq(warehouseId != null, TemuProductStock::getWarehouseId, warehouseId)
			.orderByAsc(TemuProductStock::getWarehouseId));
		return Result.success(list);
	}

}
