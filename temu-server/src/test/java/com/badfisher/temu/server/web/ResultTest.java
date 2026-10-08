package com.badfisher.temu.server.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 单元测试 — 统一响应包装
 *
 * @author lenkor
 */
class ResultTest {

	// ==================== success() ====================

	@Test
	void shouldCreateSuccessWithData() {
		Result<String> result = Result.success("success", "hello");

		assertEquals(200, result.getCode());
		assertEquals("success", result.getMessage());
		assertEquals("hello", result.getData());
		assertTrue(result.getTimestamp() > 0);
	}

	@Test
	void shouldCreateSuccessWithMessageAndData() {
		Result<Integer> result = Result.success("操作成功", 100);

		assertEquals(200, result.getCode());
		assertEquals("操作成功", result.getMessage());
		assertEquals(100, result.getData());
		assertTrue(result.getTimestamp() > 0);
	}

	@Test
	void shouldCreateSuccessWithoutData() {
		Result<Object> result = Result.success();

		assertEquals(200, result.getCode());
		assertEquals("success", result.getMessage());
		assertNull(result.getData());
		assertTrue(result.getTimestamp() > 0);
	}

	@Test
	void shouldCreateSuccessWithMessageOnly() {
		Result<Object> result = Result.success("创建成功");

		assertEquals(200, result.getCode());
		assertEquals("创建成功", result.getMessage());
		assertNull(result.getData());
		assertTrue(result.getTimestamp() > 0);
	}

	@Test
	void shouldCreateSuccessWithNullData() {
		Result<Object> result = Result.success((Object) null);

		assertEquals(200, result.getCode());
		assertEquals("success", result.getMessage());
		assertNull(result.getData());
	}

	// ==================== fail() ====================

	@Test
	void shouldCreateFailWithCodeAndMessage() {
		Result<String> result = Result.fail(400, "参数错误");

		assertEquals(400, result.getCode());
		assertEquals("参数错误", result.getMessage());
		assertNull(result.getData());
		assertTrue(result.getTimestamp() > 0);
	}

	@Test
	void shouldCreateFailWithMessage() {
		Result<String> result = Result.fail("服务器内部错误");

		assertEquals(500, result.getCode());
		assertEquals("服务器内部错误", result.getMessage());
		assertNull(result.getData());
		assertTrue(result.getTimestamp() > 0);
	}

	@Test
	void shouldCreateFailWithCustomErrorCode() {
		Result<String> result = Result.fail(404, "资源未找到");

		assertEquals(404, result.getCode());
		assertEquals("资源未找到", result.getMessage());
	}

	// ==================== 时间戳 ====================

	@Test
	void shouldTimestampBeCurrentTime() {
		long before = System.currentTimeMillis();
		Result<Object> result = Result.success();
		long after = System.currentTimeMillis();

		assertTrue(result.getTimestamp() >= before);
		assertTrue(result.getTimestamp() <= after);
	}

	@Test
	void shouldDifferentResultsHaveDifferentTimestamps() throws InterruptedException {
		Result<Object> r1 = Result.success();
		Thread.sleep(10);
		Result<Object> r2 = Result.success();

		assertTrue(r2.getTimestamp() > r1.getTimestamp());
	}

	// ==================== 泛型 ====================

	@Test
	void shouldSupportGenericType() {
		Result<String> strResult = Result.success("success", "string");
		assertEquals("string", strResult.getData());

		Result<Integer> intResult = Result.success(42);
		assertEquals(42, intResult.getData());

		Result<Long> longResult = Result.success(99L);
		assertEquals(99L, longResult.getData());
	}

	// ==================== Serializable ====================

	@Test
	void shouldBeSerializable() {
		Result<String> result = Result.success("test");
		assertTrue(result instanceof java.io.Serializable);
	}

}
