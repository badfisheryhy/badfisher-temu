package com.badfisher.temu.server.web;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一响应包装
 *
 * @author lenkor
 */
@Getter
@ApiModel("统一响应结果")
public class Result<T> implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty("状态码，200=成功")
	private int code;

	@ApiModelProperty("提示信息")
	private String message;

	@ApiModelProperty("响应数据")
	private T data;

	@ApiModelProperty("响应时间戳")
	private long timestamp;

	private Result() {
	}

	private Result(int code, String message, T data) {
		this.code = code;
		this.message = message;
		this.data = data;
		this.timestamp = System.currentTimeMillis();
	}

	public static <T> Result<T> success(T data) {
		return new Result<>(200, "success", data);
	}

	public static <T> Result<T> success(String message, T data) {
		return new Result<>(200, message, data);
	}

	public static <T> Result<T> success() {
		return new Result<>(200, "success", null);
	}

	public static <T> Result<T> success(String message) {
		return new Result<>(200, message, null);
	}

	public static <T> Result<T> fail(int code, String message) {
		return new Result<>(code, message, null);
	}

	public static <T> Result<T> fail(String message) {
		return new Result<>(500, message, null);
	}

}
