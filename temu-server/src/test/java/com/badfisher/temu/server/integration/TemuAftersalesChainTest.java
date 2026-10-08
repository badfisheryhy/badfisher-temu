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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Temu 售后单链式调用测试 — 不依赖 Spring/DB/Redis，纯 HTTP 调用。
 * <p>
 * 验证售后三步链路：
 * <ol>
 * <li>bg.aftersales.parentaftersales.list.get — 获取父售后单列表</li>
 * <li>bg.aftersales.aftersales.list.get — 根据 parentAftersalesSn 获取子售后单列表</li>
 * <li>temu.aftersales.parentaftersales.detail.get — 根据父售后单获取售后详情</li>
 * <li>bg.aftersales.parentreturnorder.get — 获取退货物流</li>
 * </ol>
 * 步骤1获取的 parentAftersalesSn 通过 System.setProperty 传递给步骤2和步骤3。
 *
 * @author lenkor
 */
@Slf4j
@EnabledIfEnvironmentVariable(named = "TEMU_LIVE_TESTS", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemuAftersalesChainTest {

	private static final String TEMU_US_API_URL = "https://openapi-b-us.temu.com/openapi/router";

	private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

	private static final ObjectMapper OM = new ObjectMapper();

	private static final String APP_KEY = System.getenv("TEMU_TEST_APP_KEY");

	private static final String APP_SECRET = System.getenv("TEMU_TEST_APP_SECRET");

	private static final String ACCESS_TOKEN = System.getenv("TEMU_TEST_ACCESS_TOKEN");

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
		log.info("========== Temu 售后单链式调用测试启动 ==========");
	}

	// ==================== 1. 父售后单列表 ====================

	@Test
	@Order(1)
	@DisplayName("步骤1. bg.aftersales.parentaftersales.list.get - 获取父售后单列表")
	void step1_parentAftersalesList() throws Exception {
		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.aftersales.parentaftersales.list.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");

		long now = Instant.now().getEpochSecond();
		params.put("updateAtStart", now - 5 * 24 * 3600);
		params.put("updateAtEnd", now);
		params.put("pageSize", 10);
		params.put("pageNumber", 1);

		params.remove("sign");
		params.put("sign", CommonUtils.generateMD5Sign(params, APP_SECRET));

		String requestJson = OM.writeValueAsString(params);
		log.info("========== 步骤1: 获取父售后单列表 ==========");
		log.info("请求参数: {}", requestJson);

		Map<String, Object> response = callApi(requestJson);
		assertNotNull(response, "响应不能为空");

		if (response.containsKey("error_response")) {
			Map<String, Object> err = (Map<String, Object>) response.get("error_response");
			log.error("API 业务错误: code={}, msg={}", err.get("code"), err.get("msg"));
			fail("父售后单列表查询失败: " + err.get("msg"));
			return;
		}

		if (Boolean.FALSE.equals(response.get("success"))) {
			log.error("API 返回失败: errorCode={}, errorMsg={}", response.get("errorCode"), response.get("errorMsg"));
			fail("父售后单列表查询失败: " + response.get("errorMsg"));
			return;
		}

		log.info("响应顶层 keys: {}", response.keySet());

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		if (result != null) {
			log.info("result keys: {}", result.keySet());

			Object totalItemNum = result.get("totalItemNum");
			if (totalItemNum != null) {
				log.info("totalItemNum={}", totalItemNum);
			}

			List<Map<String, Object>> pageItems = extractList(result, "pageItems", "parentAftersalesList", "list");
			if (pageItems != null && !pageItems.isEmpty()) {
				log.info("本页父售后单数: {}", pageItems.size());

				Map<String, Object> first = pageItems.get(0);
				log.info("第一条父售后单 keys: {}", first.keySet());
				log.info("第一条父售后单完整内容: {}", OM.writerWithDefaultPrettyPrinter().writeValueAsString(first));

				String parentAftersalesSn = str(first.get("parentAftersalesSn"));
				if (parentAftersalesSn == null) {
					parentAftersalesSn = str(first.get("parentAfterSalesSn"));
				}
				if (parentAftersalesSn == null) {
					parentAftersalesSn = str(first.get("parentAftersalesId"));
				}

				if (parentAftersalesSn != null) {
					System.setProperty("temu.test.parentAftersalesSn", parentAftersalesSn);
					String parentOrderSn = str(first.get("parentOrderSn"));
					if (parentOrderSn != null) {
						System.setProperty("temu.test.parentOrderSn", parentOrderSn);
					}
					log.info("✓ 已保存 parentAftersalesSn={} 供步骤2和步骤3使用", parentAftersalesSn);
				}
				else {
					log.warn("未找到 parentAftersalesSn 字段, 尝试打印所有字段值:");
					for (Map.Entry<String, Object> entry : first.entrySet()) {
						log.warn("  {} = {}", entry.getKey(), entry.getValue());
					}
				}
			}
			else {
				log.warn("当前时间窗口无父售后单数据");
			}
		}
		else {
			log.warn("result 为空");
		}
	}

	// ==================== 2. 子售后单列表 ====================

	@Test
	@Order(2)
	@DisplayName("步骤2. bg.aftersales.aftersales.list.get - 根据 parentAftersalesSn 获取子售后单列表")
	void step2_aftersalesList() throws Exception {
		String parentAftersalesSn = System.getProperty("temu.test.parentAftersalesSn");
		if (parentAftersalesSn == null) {
			log.warn("跳过步骤2 - 步骤1未获取到 parentAftersalesSn（可能无售后数据）");
			return;
		}

		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.aftersales.aftersales.list.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");

		params.put("parentAfterSalesSnList", java.util.Collections.singletonList(parentAftersalesSn));
		params.put("pageSize", 50);
		params.put("pageNumber", 1);

		params.remove("sign");
		params.put("sign", CommonUtils.generateMD5Sign(params, APP_SECRET));

		String requestJson = OM.writeValueAsString(params);
		log.info("========== 步骤2: 获取子售后单列表 ==========");
		log.info("parentAftersalesSn={}", parentAftersalesSn);
		log.info("请求参数: {}", requestJson);

		Map<String, Object> response = callApi(requestJson);
		assertNotNull(response, "响应不能为空");

		if (response.containsKey("error_response")) {
			Map<String, Object> err = (Map<String, Object>) response.get("error_response");
			log.error("API 业务错误: code={}, msg={}", err.get("code"), err.get("msg"));
			fail("子售后单列表查询失败: " + err.get("msg"));
			return;
		}

		if (Boolean.FALSE.equals(response.get("success"))) {
			log.error("API 返回失败: errorCode={}, errorMsg={}", response.get("errorCode"), response.get("errorMsg"));
			fail("子售后单列表查询失败: " + response.get("errorMsg"));
			return;
		}

		log.info("响应顶层 keys: {}", response.keySet());

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		if (result != null) {
			log.info("result keys: {}", result.keySet());

			Object totalItemNum = result.get("totalItemNum");
			if (totalItemNum != null) {
				log.info("totalItemNum={}", totalItemNum);
			}

			List<Map<String, Object>> pageItems = extractList(result, "pageItems", "aftersalesList", "list");
			if (pageItems != null && !pageItems.isEmpty()) {
				log.info("本页子售后单数: {}", pageItems.size());

				for (int i = 0; i < Math.min(3, pageItems.size()); i++) {
					Map<String, Object> item = pageItems.get(i);
					log.info("子售后单[{}] keys: {}", i, item.keySet());
					log.info("子售后单[{}] 完整内容:\n{}", i, OM.writerWithDefaultPrettyPrinter().writeValueAsString(item));
				}

				Map<String, Object> first = pageItems.get(0);
				String aftersalesSn = str(first.get("aftersalesSn"));
				if (aftersalesSn == null) {
					aftersalesSn = str(first.get("subAfterSalesSn"));
				}
				if (aftersalesSn != null) {
					System.setProperty("temu.test.aftersalesSn", aftersalesSn);
					log.info("✓ 已保存 aftersalesSn={} 供后续使用", aftersalesSn);
				}
			}
			else {
				log.warn("该父售后单下无子售后单数据");
			}
		}
		else {
			log.warn("result 为空");
		}
	}

	// ==================== 3. 售后详情 ====================

	@Test
	@Order(3)
	@DisplayName("步骤3. temu.aftersales.parentaftersales.detail.get - 获取售后详情")
	void step3_aftersalesDetail() throws Exception {
		String parentAftersalesSn = System.getProperty("temu.test.parentAftersalesSn");
		if (parentAftersalesSn == null) {
			log.warn("跳过步骤3 - 步骤1未获取到 parentAftersalesSn（可能无售后数据）");
			return;
		}

		Map<String, Object> params = new HashMap<>();
		params.put("type", "temu.aftersales.parentaftersales.detail.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");

		params.put("parentAfterSalesSn", parentAftersalesSn);
		String parentOrderSn = System.getProperty("temu.test.parentOrderSn");
		if (parentOrderSn != null) {
			params.put("parentOrderSn", parentOrderSn);
		}

		params.remove("sign");
		params.put("sign", CommonUtils.generateMD5Sign(params, APP_SECRET));

		String requestJson = OM.writeValueAsString(params);
		log.info("========== 步骤3: 获取售后详情 ==========");
		log.info("parentAftersalesSn={}", parentAftersalesSn);
		log.info("请求参数: {}", requestJson);

		Map<String, Object> response = callApi(requestJson);
		assertNotNull(response, "响应不能为空");

		if (response.containsKey("error_response")) {
			Map<String, Object> err = (Map<String, Object>) response.get("error_response");
			log.error("API 业务错误: code={}, msg={}", err.get("code"), err.get("msg"));
			fail("售后详情查询失败: " + err.get("msg"));
			return;
		}

		if (Boolean.FALSE.equals(response.get("success"))) {
			log.error("API 返回失败: errorCode={}, errorMsg={}", response.get("errorCode"), response.get("errorMsg"));
			fail("售后详情查询失败: " + response.get("errorMsg"));
			return;
		}

		log.info("响应顶层 keys: {}", response.keySet());

		Map<String, Object> result = (Map<String, Object>) response.get("result");
		if (result != null) {
			log.info("result keys: {}", result.keySet());
			log.info("售后详情完整内容:\n{}", OM.writerWithDefaultPrettyPrinter().writeValueAsString(result));
		}
		else {
			log.warn("result 为空");
			log.info("完整响应:\n{}", OM.writerWithDefaultPrettyPrinter().writeValueAsString(response));
		}
	}

	@Test
	@Order(4)
	@DisplayName("步骤4. bg.aftersales.parentreturnorder.get - 获取退货物流")
	void step4_parentReturnOrder() throws Exception {
		String parentAftersalesSn = System.getProperty("temu.test.parentAftersalesSn");
		if (parentAftersalesSn == null) {
			log.warn("跳过步骤4 - 步骤1未获取到 parentAftersalesSn");
			return;
		}

		Map<String, Object> params = new HashMap<>();
		params.put("type", "bg.aftersales.parentreturnorder.get");
		params.put("app_key", APP_KEY);
		params.put("access_token", ACCESS_TOKEN);
		params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
		params.put("data_type", "JSON");
		params.put("parentAfterSalesSn", parentAftersalesSn);
		params.put("sign", CommonUtils.generateMD5Sign(params, APP_SECRET));

		Map<String, Object> response = callApi(OM.writeValueAsString(params));
		assertNotNull(response, "退货物流响应不能为空");
		if (Boolean.FALSE.equals(response.get("success"))) {
			fail("退货物流查询失败: " + response.get("errorMsg"));
		}
		log.info("退货物流完整响应:\n{}", OM.writerWithDefaultPrettyPrinter().writeValueAsString(response));
	}

	// ==================== 通用方法 ====================

	@SuppressWarnings("unchecked")
	private Map<String, Object> callApi(String requestJson) throws Exception {
		RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, requestJson);
		Request request = new Request.Builder().url(TEMU_US_API_URL).post(body).build();

		try (Response response = httpClient.newCall(request).execute()) {
			String responseBody = response.body() != null ? response.body().string() : null;
			log.info("HTTP 状态码: {}", response.code());

			if (!response.isSuccessful() || responseBody == null) {
				log.error("HTTP 失败: {} {}", response.code(), response.message());
				return null;
			}

			log.info("原始响应体: {}", responseBody);
			return OM.readValue(responseBody, Map.class);
		}
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> extractList(Map<String, Object> result, String... keys) {
		for (String key : keys) {
			Object val = result.get(key);
			if (val instanceof List) {
				return (List<Map<String, Object>>) val;
			}
		}
		return null;
	}

	private static String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}

}
