package com.badfisher.temu.server.api.product;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.badfisher.temu.business.product.dto.ProductQueryDTO;
import com.badfisher.temu.business.product.model.TemuProduct;
import com.badfisher.temu.business.product.model.TemuProductSku;
import com.badfisher.temu.business.product.service.TemuProductService;
import com.badfisher.temu.business.product.service.TemuProductSkuService;
import com.badfisher.temu.business.product.dto.ProductDetailVO;
import com.badfisher.temu.server.web.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Temu 商品档案查询接口。
 * <p>
 * 商品唯一维度为 appId、sellerId、productId；SKU 唯一维度为 appId、sellerId、productSkuId。
 * </p>
 *
 * @author lenkor
 */
@Api(tags = "Temu 商品查询")
@RestController
@RequestMapping("/temu/product")
@RequiredArgsConstructor
public class TemuProductController {

	private final TemuProductService productService;

	private final TemuProductSkuService productSkuService;

	@ApiOperation(value = "分页查询商品档案", notes = "按应用主体、卖家、商品ID、SKC、外部编码、名称和状态筛选")
	@GetMapping("/list")
	public Result<IPage<TemuProduct>> listProducts(ProductQueryDTO query) {
		Page<TemuProduct> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuProduct> result = productService.page(page,
				Wrappers.lambdaQuery(TemuProduct.class)
					.eq(query.getAppId() != null, TemuProduct::getAppId, query.getAppId())
					.eq(query.getSellerId() != null, TemuProduct::getSellerId, query.getSellerId())
					.like(query.getShopName() != null, TemuProduct::getShopName, query.getShopName())
					.eq(query.getProductId() != null, TemuProduct::getProductId, query.getProductId())
					.eq(query.getProductSkcId() != null, TemuProduct::getProductSkcId, query.getProductSkcId())
					.like(query.getExtCode() != null, TemuProduct::getExtCode, query.getExtCode())
					.like(query.getProductName() != null, TemuProduct::getProductName, query.getProductName())
					.eq(query.getSkcSiteStatus() != null, TemuProduct::getSkcSiteStatus, query.getSkcSiteStatus())
					.orderByDesc(TemuProduct::getUpdateTime));
		return Result.success(result);
	}

	@ApiOperation(value = "查询商品聚合详情", notes = "根据商品业务ID查询商品档案及其全部SKU")
	@GetMapping("/{productId}")
	public Result<ProductDetailVO> getProductDetail(
			@ApiParam(value = "Temu商品ID", required = true) @PathVariable Long productId,
			@ApiParam(value = "Temu应用主体ID", required = true) @RequestParam String appId,
			@ApiParam(value = "卖家ID/店铺编码", required = true) @RequestParam String sellerId) {
		TemuProduct product = productService.getOne(Wrappers.lambdaQuery(TemuProduct.class)
			.eq(TemuProduct::getAppId, appId)
			.eq(TemuProduct::getSellerId, sellerId)
			.eq(TemuProduct::getProductId, productId)
			.last("LIMIT 1"));
		if (product == null) {
			return Result.fail("商品不存在: " + productId);
		}
		List<TemuProductSku> skus = productSkuService.list(Wrappers.lambdaQuery(TemuProductSku.class)
			.eq(TemuProductSku::getAppId, appId)
			.eq(TemuProductSku::getSellerId, sellerId)
			.eq(TemuProductSku::getProductId, productId)
			.orderByAsc(TemuProductSku::getProductSkuId));
		ProductDetailVO detail = new ProductDetailVO();
		detail.setProduct(product);
		detail.setSkus(skus);
		return Result.success(detail);
	}

	@ApiOperation(value = "分页查询商品SKU", notes = "支持按商品ID、SKC、SKU ID和外部编码筛选")
	@GetMapping("/sku/list")
	public Result<IPage<TemuProductSku>> listSkus(ProductQueryDTO query) {
		Page<TemuProductSku> page = new Page<>(query.normalizedPageNum(), query.normalizedPageSize());
		IPage<TemuProductSku> result = productSkuService.page(page,
				Wrappers.lambdaQuery(TemuProductSku.class)
					.eq(query.getAppId() != null, TemuProductSku::getAppId, query.getAppId())
					.eq(query.getSellerId() != null, TemuProductSku::getSellerId, query.getSellerId())
					.like(query.getShopName() != null, TemuProductSku::getShopName, query.getShopName())
					.eq(query.getProductId() != null, TemuProductSku::getProductId, query.getProductId())
					.eq(query.getProductSkcId() != null, TemuProductSku::getProductSkcId, query.getProductSkcId())
					.eq(query.getProductSkuId() != null, TemuProductSku::getProductSkuId, query.getProductSkuId())
					.like(query.getExtCode() != null, TemuProductSku::getExtCode, query.getExtCode())
					.orderByDesc(TemuProductSku::getUpdateTime));
		return Result.success(result);
	}

	@ApiOperation(value = "根据SKU ID查询SKU详情", notes = "appId和sellerId为SKU租户隔离条件")
	@GetMapping("/sku/{productSkuId}")
	public Result<TemuProductSku> getSku(
			@ApiParam(value = "Temu商品SKU ID", required = true) @PathVariable Long productSkuId,
			@ApiParam(value = "Temu应用主体ID", required = true) @RequestParam String appId,
			@ApiParam(value = "卖家ID/店铺编码", required = true) @RequestParam String sellerId) {
		TemuProductSku sku = productSkuService.getOne(Wrappers.lambdaQuery(TemuProductSku.class)
			.eq(TemuProductSku::getAppId, appId)
			.eq(TemuProductSku::getSellerId, sellerId)
			.eq(TemuProductSku::getProductSkuId, productSkuId)
			.last("LIMIT 1"));
		return sku == null ? Result.fail("商品SKU不存在: " + productSkuId) : Result.success(sku);
	}

}
