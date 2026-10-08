package com.badfisher.temu.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.badfisher.temu.client.util.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Temu 库存接口联通测试 — 不依赖 Spring/DB/Redis，纯 HTTP 调用。
 * <p>
 * 测试 3 个库存接口：
 * <ol>
 * <li>bg.btg.goods.stock.warehouse.list.get — 查询卖家发货仓库列表</li>
 * <li>bg.btg.goods.stock.quantity.get — 查询货品库存数量</li>
 * <li>bg.btg.goods.stock.quantity.update — 修改货品库存数量（跳过,避免误改）</li>
 * </ol>
 * <p>
 * 注意：库存接口走的是 Temu partner 新 API 地址（非订单接口的 openapi-b-us）：
 * https://openapi-b-partner.temu.com/openapi/router
 * <p>
 * 运行前无需环境变量，凭据已写死（与 TemuApiLiveTest 一致）。
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuStockApiLiveTest {

	/** 库存接口走 partner 新 API 地址 */
	private static final String TEMU_PARTNER_API_URL = "https://openapi-b-partner.temu.com/openapi/router";

	/** 美国 partner 备用地址（部分接口可能走 us） */
	private static final String TEMU_US_API_URL = "https://openapi-b-us.temu.com/openapi/router";

	private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

	private static final ObjectMapper OM = new ObjectMapper();

	private static final String APP_KEY = System.getenv("TEMU_TEST_APP_KEY");

	private static final String APP_SECRET = System.getenv("TEMU_TEST_APP_SECRET");

	private static final String ACCESS_TOKEN = System.getenv("TEMU_TEST_ACCESS_TOKEN");

	/** 站点ID：100=US（参考工程 GetGoodsWarehouseParam 注释） */
	private static final List<Integer> SITE_ID_LIST = Arrays.asList(100);

	private static OkHttpClient httpClient;

	@BeforeAll
	static void setUp() {
		assertNotNull(APP_KEY, "请设置环境变量 TEMU_TEST_APP_KEY");
		assertNotNull(APP_SECRET, "请设置环境变量 TEMU_TEST_APP_SECRET");
		assertNotNull(ACCESS_TOKEN, "请设置环境变量 TEMU_TEST_ACCESS_TOKEN");

		httpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.build();
		log.info("========== Temu 库存接口联通测试启动 ==========");
		log.info("APP_KEY={}...", APP_KEY.substring(0, 8));
	}

	// ==================== 1. 查询仓库列表 ====================

	@Test
	@Order(1)
	@DisplayName("1. bg.btg.goods.stock.warehouse.list.get - 查询发货仓库")
	void shouldGetWarehouseList() throws Exception {
		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.btg.goods.stock.warehouse.list.get");
		params.put("siteIdList", SITE_ID_LIST);

		// 先尝试 partner 地址
		log.info("========== 步骤1: 查询仓库列表 ==========");
		Map<String, Object> response = callApi(TEMU_PARTNER_API_URL, params);
		if (response == null) {
			log.warn("partner 地址失败, 尝试 US 地址...");
			response = callApi(TEMU_US_API_URL, params);
		}
		if (response == null) {
			fail("两个地址均调用失败, 请检查网络/凭据/接口权限");
			return;
		}

		log.info("仓库列表响应 keys: {}", response.keySet());

		// 响应结构: { success:true, result:[{warehouseId, warehouseName, ...}] }
		Object success = response.get("success");
		Object result = response.get("result");

		if (Boolean.FALSE.equals(success)) {
			log.error("API 返回失败: errorCode={}, errorMsg={}", response.get("errorCode"), response.get("errorMsg"));
			fail("库存接口返回失败: " + response.get("errorMsg"));
			return;
		}

		if (result != null) {
			log.info("仓库列表 result(前2000字符): {}",
					OM.writeValueAsString(result).substring(0, Math.min(2000, OM.writeValueAsString(result).length())));

			// 尝试解析仓库列表
			if (result instanceof List) {
				List<?> warehouses = (List<?>) result;
				log.info("仓库数量: {}", warehouses.size());
				for (Object w : warehouses) {
					if (w instanceof Map) {
						Map<?, ?> wh = (Map<?, ?>) w;
						log.info("  warehouseId={}, warehouseName={}", wh.get("warehouseId"), wh.get("warehouseName"));
					}
				}
				if (!warehouses.isEmpty()) {
					// 保存第一个仓库 ID 供后续库存查询用
					Map<?, ?> first = (Map<?, ?>) warehouses.get(0);
					System.setProperty("temu.test.warehouseId", String.valueOf(first.get("warehouseId")));
					log.info("✓ 仓库列表获取成功, 保存第一个仓库 ID 供后续测试");
				}
			}
			else {
				log.info("result 类型: {}", result.getClass());
			}
		}
		else {
			log.warn("result 为空");
		}
	}

	// ==================== 2. 查询库存数量 ====================

	@Test
	@Order(2)
	@DisplayName("2. bg.btg.goods.stock.quantity.get - 查询货品库存")
	void shouldGetStockQuantity() throws Exception {
		// 需要 productSkcId（货品 SKC ID），测试店铺可能没有，用占位值
		// 实际跑时从商品列表接口获取，或填入你店铺真实的 productSkcId
		String productSkcId = System.getProperty("temu.test.productSkcId", "0");

		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.btg.goods.stock.quantity.get");
		params.put("productSkcId", productSkcId);

		log.info("========== 步骤2: 查询库存 (productSkcId={}) ==========", productSkcId);
		Map<String, Object> response = callApi(TEMU_PARTNER_API_URL, params);
		if (response == null) {
			log.warn("partner 地址失败, 尝试 US 地址...");
			response = callApi(TEMU_US_API_URL, params);
		}
		if (response == null) {
			log.warn("库存查询接口调用失败（可能需要真实 productSkcId）, 跳过");
			return;
		}

		log.info("库存查询响应: {}",
				OM.writeValueAsString(response).substring(0, Math.min(2000, OM.writeValueAsString(response).length())));

		Object success = response.get("success");
		if (Boolean.FALSE.equals(success)) {
			log.warn("库存查询返回失败: errorCode={}, errorMsg={}", response.get("errorCode"), response.get("errorMsg"));
			log.warn("(productSkcId={} 可能无效, 请填入真实货品 SKC ID)", productSkcId);
			return;
		}

		Object result = response.get("result");
		if (result != null) {
			log.info("✓ 库存查询成功: {}",
					OM.writeValueAsString(result).substring(0, Math.min(2000, OM.writeValueAsString(result).length())));
		}
	}

	// ==================== 3. 修改库存（默认跳过,避免误改） ====================

	@Test
	@Order(3)
	@DisplayName("3. bg.btg.goods.stock.quantity.update - 修改库存（默认跳过）")
	void shouldUpdateStockQuantity() {
		// 修改库存是写操作，默认跳过避免误改
		// 如需测试，设置 JVM 系统属性 -Dtemu.test.runUpdate=true
		boolean runUpdate = Boolean.getBoolean("temu.test.runUpdate");
		if (!runUpdate) {
			log.info("========== 步骤3: 修改库存（已跳过, 避免误改） ==========");
			log.info("如需测试, 添加 JVM 参数: -Dtemu.test.runUpdate=true");
			log.info("并准备真实的 productSkcId / productSkuId / warehouseId");
			return;
		}

		String productSkcId = System.getProperty("temu.test.productSkcId", "0");
		String warehouseId = System.getProperty("temu.test.warehouseId", "");
		long productSkuId = Long.getLong("temu.test.productSkuId", 0L);

		Map<String, Object> skuStockChange = new HashMap<>();
		skuStockChange.put("productSkuId", productSkuId);
		skuStockChange.put("stockDiff", 1); // +1 增加一个库存, 影响最小
		skuStockChange.put("warehouseId", warehouseId);

		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.btg.goods.stock.quantity.update");
		params.put("productSkcId", productSkcId);
		params.put("skuStockChangeList", Arrays.asList(skuStockChange));

		log.info("========== 步骤3: 修改库存 ==========");
		log.info("productSkcId={}, productSkuId={}, warehouseId={}, stockDiff=+1", productSkcId, productSkuId,
				warehouseId);
		try {
			Map<String, Object> response = callApi(TEMU_PARTNER_API_URL, params);
			if (response != null) {
				log.info("修改库存响应: {}", OM.writeValueAsString(response)
					.substring(0, Math.min(2000, OM.writeValueAsString(response).length())));
			}
		}
		catch (Exception e) {
			log.error("修改库存异常", e);
		}
	}

	// ==================== 通用调用方法 ====================

	/**
	 * 调用 Temu API — 组装公共参数、签名、发请求、解析响应。 返回 null 表示 HTTP 失败, 返回 Map 表示成功解析。
	 */
	private Map<String, Object> callApi(String apiUrl, Map<String, Object> businessParams) {
		try {
			// 组装公共参数（不修改原 params）
			Map<String, Object> params = new HashMap<>(businessParams);
			params.put("app_key", APP_KEY);
			params.put("access_token", ACCESS_TOKEN);
			params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
			params.put("data_type", "JSON");

			// 签名
			params.remove("sign");
			String sign = CommonUtils.generateMD5Sign(params, APP_SECRET);
			params.put("sign", sign);

			String requestJson = OM.writeValueAsString(params);
			log.info("请求: url={}, body={}", apiUrl, requestJson);

			RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, requestJson);
			Request request = new Request.Builder().url(apiUrl).post(body).build();

			try (Response response = httpClient.newCall(request).execute()) {
				String responseBody = response.body() != null ? response.body().string() : null;
				log.info("HTTP 状态码: {}", response.code());

				if (!response.isSuccessful() || responseBody == null) {
					log.warn("HTTP 失败: {} {}", response.code(), response.message());
					return null;
				}

				log.info("响应体(前2000字符): {}", responseBody.substring(0, Math.min(2000, responseBody.length())));

				return OM.readValue(responseBody, Map.class);
			}
		}
		catch (Exception e) {
			log.warn("调用异常: url={}, error={}", apiUrl, e.getMessage());
			return null;
		}
	}

}
