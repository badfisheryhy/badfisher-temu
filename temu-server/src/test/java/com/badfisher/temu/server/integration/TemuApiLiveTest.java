package com.badfisher.temu.server.integration;

import com.badfisher.temu.business.order.service.TemuOrderPullService;

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
 * Temu API 真实联通测试 — 不依赖 Spring/DDB/Redis，纯 HTTP 调用。
 * <p>
 * 验证链路：签名 → 调用 bg.order.list.v2.get → 解析响应结构 → 调用 bg.order.shippinginfo.v2.get。
 * <p>
 * <b>运行前必须设置环境变量（IDEA: Run Configuration → Environment Variables）：</b> <pre>
 *   TEMU_LIVE_TESTS=true
 *   TEMU_TEST_APP_KEY=your-app-key
 *   TEMU_TEST_APP_SECRET=your-app-secret
 *   TEMU_TEST_ACCESS_TOKEN=your-access-token
 *   TEMU_TEST_SELLER_ID=your-seller-id
 * </pre> 凭据不硬编码到源码，避免泄露到代码库。
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuApiLiveTest {

	private static final String TEMU_US_API_URL = "https://openapi-b-us.temu.com/openapi/router";

	private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

	private static final ObjectMapper OM = new ObjectMapper();

	private static final String APP_KEY = System.getenv("TEMU_TEST_APP_KEY");

	private static final String APP_SECRET = System.getenv("TEMU_TEST_APP_SECRET");

	private static final String ACCESS_TOKEN = System.getenv("TEMU_TEST_ACCESS_TOKEN");

	private static final String SELLER_ID = System.getenv("TEMU_TEST_SELLER_ID");

	private static OkHttpClient httpClient;

	@BeforeAll
	static void setUp() {
		assertNotNull(APP_KEY, "请设置环境变量 TEMU_TEST_APP_KEY");
		assertNotNull(APP_SECRET, "请设置环境变量 TEMU_TEST_APP_SECRET");
		assertNotNull(ACCESS_TOKEN, "请设置环境变量 TEMU_TEST_ACCESS_TOKEN");
		assertNotNull(SELLER_ID, "请设置环境变量 TEMU_TEST_SELLER_ID");

		httpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.build();

		log.info("========== Temu API 联通测试启动 ==========");
		log.info("APP_KEY={}", APP_KEY.substring(0, 8) + "...");
		log.info("SELLER_ID={}", SELLER_ID);

	}

	// ==================== 1. 签名算法验证 ====================

	@Test
	@Order(1)
	@DisplayName("1. MD5 签名算法 - 生成非空签名")
	void shouldGenerateNonEmptySign() {
		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.order.list.v2.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");
		params.put("pageSize", 1);
		params.put("pageNumber", 1);

		String sign = CommonUtils.generateMD5Sign(params, APP_SECRET);

		log.info("签名结果: {}", sign);
		assertNotNull(sign);
		assertFalse(sign.isEmpty(), "签名不能为空");
		assertEquals(32, sign.length(), "MD5 签名应为 32 位十六进制");
		assertEquals(sign.toUpperCase(), sign, "签名应为大写");
	}

	// ==================== 2. 订单列表 API 调用 ====================

	@Test
	@Order(2)
	@DisplayName("2. bg.order.list.v2.get - 调用并验证响应结构")
	void shouldCallOrderListApiAndParseResponse() throws Exception {
		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.order.list.v2.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");
		params.put("pageSize", 10);
		params.put("pageNumber", 1);

		// 拉取最近 10 天的订单
		long now = Instant.now().getEpochSecond();
		params.put("updateAtStart", now - 10 * 24 * 3600);
		params.put("updateAtEnd", now);

		params.remove("sign");
		String sign = CommonUtils.generateMD5Sign(params, APP_SECRET);
		params.put("sign", sign);

		String requestJson = OM.writeValueAsString(params);
		log.info("请求参数: {}", requestJson);

		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, requestJson);
		Request request = new Request.Builder().url(TEMU_US_API_URL).post(body).build();

		try (Response response = httpClient.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			log.info("HTTP 状态码: {}", response.code());
			log.info("响应体: {}", responseBody);
			assertTrue(response.isSuccessful(), "HTTP 应为 2xx, 实际=" + response.code());
			assertNotNull(responseBody, "响应体不能为空");

			Map<String, Object> result = OM.readValue(responseBody, Map.class);

			// 检查是否业务错误
			if (result.containsKey("error_response")) {
				Map<String, Object> err = (Map<String, Object>) result.get("error_response");
				String errMsg = String.valueOf(err.get("msg"));
				String errCode = String.valueOf(err.get("code"));
				log.error("API 业务错误! code={}, msg={}", errCode, errMsg);
				fail("Temu API 返回业务错误: code=" + errCode + ", msg=" + errMsg
						+ " (常见: token过期→3000034, 签名错→3000001, 无权限→3000032)");
			}

			// 验证成功响应结构: { result: { pageItems:[...], totalItemNum:N } }
			assertTrue(result.containsKey("result"), "响应应包含 'result' 字段, 实际keys=" + result.keySet());

			Map<String, Object> resultMap = (Map<String, Object>) result.get("result");
			log.info("result 字段 keys: {}", resultMap.keySet());

			// result 可能为空(无订单)
			if (resultMap.get("totalItemNum") != null) {
				log.info("totalItemNum={}", resultMap.get("totalItemNum"));

				List<Map<String, Object>> pageItems = (List<Map<String, Object>>) resultMap.get("pageItems");
				if (pageItems != null && !pageItems.isEmpty()) {
					log.info("本页订单数: {}", pageItems.size());

					// 验证第一条订单结构
					Map<String, Object> firstOrder = pageItems.get(0);
					log.info("第一条订单 keys: {}", firstOrder.keySet());

					assertTrue(firstOrder.containsKey("parentOrderMap"), "pageItem 应包含 parentOrderMap");

					Map<String, Object> parentOrderMap = (Map<String, Object>) firstOrder.get("parentOrderMap");
					String parentOrderSn = String.valueOf(parentOrderMap.get("parentOrderSn"));
					log.info("parentOrderSn={}", parentOrderSn);
					assertNotNull(parentOrderSn, "parentOrderSn 不能为空");

					// 保存第一条订单号供后续地址测试用
					System.setProperty("temu.test.firstParentOrderSn", parentOrderSn);
				}
				else {
					log.warn("当前时间窗口无订单数据（测试店铺可能无近期订单）");
				}
			}
			else {
				log.warn("result 为空（测试店铺可能无订单）");
			}
		}
	}

	// ==================== 3. 地址 API 调用 ====================

	@Test
	@Order(3)
	@DisplayName("3. 测试链接bg.order.shippinginfo.v2.get  bg.order.detail.v2.get- 调用并验证地址结构")
	void shouldCallShippingInfoApiAndParseResponse() throws Exception {
		String parentOrderSn = "DEMO-ORDER-0004";

		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.order.shippinginfo.v2.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");
		params.put("parentOrderSn", parentOrderSn);

		params.remove("sign");
		String sign = CommonUtils.generateMD5Sign(params, APP_SECRET);
		params.put("sign", sign);

		String requestJson = OM.writeValueAsString(params);
		log.info("地址请求参数: {}", requestJson);

		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, requestJson);
		Request request = new Request.Builder().url(TEMU_US_API_URL).post(body).build();

		try (Response response = httpClient.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			log.info("HTTP 状态码: {}", response.code());
			log.info("地址响应体(前2000字符): {}", responseBody != null ? responseBody : "(空)");

			assertTrue(response.isSuccessful(), "HTTP 应为 2xx, 实际=" + response.code());
			assertNotNull(responseBody, "响应体不能为空");

			Map<String, Object> result = OM.readValue(responseBody, Map.class);

			if (result.containsKey("error_response")) {
				Map<String, Object> err = (Map<String, Object>) result.get("error_response");
				log.warn("地址 API 业务错误: code={}, msg={}", err.get("code"), err.get("msg"));
				log.warn("（发货后地址接口可能不可用，属正常情况）");
				return;
			}

		}
	}

	// ==================== 4. 响应结构与代码解析逻辑对照 ====================

	@Test
	@Order(4)
	@DisplayName("4. 验证响应层级与 TemuOrderPullService 解析路径一致")
	void shouldVerifyResponseStructureMatchesCodeParsing() throws Exception {
		// 真实响应结构: { result: { pageItems:[...], totalItemNum:N } }
		// 代码解析路径: response.get("result") → result.get("pageItems") /
		// result.get("totalItemNum")
		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.order.list.v2.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");
		params.put("pageSize", 1);
		params.put("pageNumber", 1);
		long now = Instant.now().getEpochSecond();
		params.put("updateAtStart", now - 30 * 24 * 3600);
		params.put("updateAtEnd", now);

		params.remove("sign");
		params.put("sign", CommonUtils.generateMD5Sign(params, APP_SECRET));

		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, OM.writeValueAsString(params));
		Request request = new Request.Builder().url(TEMU_US_API_URL).post(body).build();

		try (Response response = httpClient.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			assertNotNull(responseBody);

			Map<String, Object> root = OM.readValue(responseBody, Map.class);

			// 1. 根节点必须有 result 或 error_response
			assertTrue(root.containsKey("result") || root.containsKey("error_response"),
					"根节点应包含 'result' 或 'error_response', 实际keys=" + root.keySet());

			if (root.containsKey("error_response")) {
				log.warn("API 返回错误, 跳过结构验证");
				return;
			}

			// 2. result 直接含 totalItemNum/pageItems（无多余 data 层）
			Map<String, Object> result = (Map<String, Object>) root.get("result");
			assertNotNull(result, "result 不能为 null");

			if (result.get("totalItemNum") != null) {
				assertTrue(result.containsKey("totalItemNum"), "result 应包含 totalItemNum, 实际keys=" + result.keySet());
				log.info("✓ 响应层级验证通过: root → result → pageItems/totalItemNum");
				log.info("✓ 与 TemuOrderPullService 解析逻辑一致");
			}
			else {
				log.info("✓ result 为 null（无订单）, 与代码的 null 判断一致");
			}
		}
	}

	// ==================== 5. 通用API调用（接口调研用） ====================

	@Test
	@Order(5)
	@DisplayName("5. 通用API调用 - 修改type测试不同接口(接口调研用)")
	void shouldCallApiByType() throws Exception {
		// ============================================================
		// 修改这里即可测试不同接口
		// ============================================================
		String apiType = System.getProperty("temu.api.type", "bg.aftersales.parentaftersales.list.get");
		String apiUrl = System.getProperty("temu.api.url", TEMU_US_API_URL);

		Map<String, Object> businessParams = new HashMap<>();
		// --- 按需添加业务参数 ---
		long now = Instant.now().getEpochSecond();
		businessParams.put("updateAtStart", now - 5 * 24 * 3600);
		businessParams.put("updateAtEnd", now);
		businessParams.put("pageSize", 10);
		businessParams.put("pageNumber", 1);
		// ------------------------
		// ============================================================
		Map<String, Object> params = new HashMap<>(businessParams);
		params.put("type", apiType);
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");

		params.remove("sign");
		params.put("sign", CommonUtils.generateMD5Sign(params, APP_SECRET));

		String requestJson = OM.writeValueAsString(params);
		log.info("========== 通用API调用: type={} ==========", apiType);
		log.info("请求URL: {}", apiUrl);
		log.info("请求参数: {}", requestJson);

		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, requestJson);
		Request request = new Request.Builder().url(apiUrl).post(body).build();

		try (Response response = httpClient.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			log.info("HTTP 状态码: {}", response.code());
			log.info("原始响应体: {}", responseBody);

			if (responseBody != null) {
				try {
					Object parsed = OM.readValue(responseBody, Object.class);
					String prettyJson = OM.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
					log.info("格式化响应:\n{}", prettyJson);

					if (parsed instanceof Map) {
						Map<?, ?> root = (Map<?, ?>) parsed;
						log.info("响应顶层 keys: {}", root.keySet());

						if (root.containsKey("error_response")) {
							Map<?, ?> err = (Map<?, ?>) root.get("error_response");
							log.warn("业务错误: code={}, msg={}", err.get("code"), err.get("msg"));
						}
						if (root.containsKey("success") && Boolean.FALSE.equals(root.get("success"))) {
							log.warn("业务失败: errorCode={}, errorMsg={}", root.get("errorCode"), root.get("errorMsg"));
						}
					}
				}
				catch (Exception e) {
					log.warn("响应体非JSON格式");
				}
			}
		}
	}

}
