package com.badfisher.temu.scheduler.xxljob;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/** XXL-JOB 2.5.0 任务结果上报，补偿任务的内部结果统一转换到执行上下文。 */
@Slf4j
public final class TemuXxlJobResult {

	private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

	private TemuXxlJobResult() {
	}

	public static void run(String jobDescription, Runnable action) {
		try {
			action.run();
			XxlJobHelper.handleSuccess(jobDescription + "执行成功");
		}
		catch (Exception e) {
			log.error("{}执行失败", jobDescription, e);
			XxlJobHelper.handleFail(jobDescription + "执行失败: " + normalizeErrorMessage(e));
		}
	}

	public static void runResult(String jobDescription, Supplier<ReturnT<String>> action) {
		try {
			ReturnT<String> result = action.get();
			if (result == null) {
				throw new IllegalStateException("任务未返回执行结果");
			}
			XxlJobHelper.handleResult(result.getCode(), result.getMsg());
		}
		catch (Exception e) {
			log.error("{}执行失败", jobDescription, e);
			XxlJobHelper.handleFail(jobDescription + "执行失败: " + normalizeErrorMessage(e));
		}
	}

	public static ReturnT<String> success(String message) {
		return new ReturnT<>(ReturnT.SUCCESS_CODE, message);
	}

	public static ReturnT<String> failure(String message) {
		return new ReturnT<>(ReturnT.FAIL_CODE, message);
	}

	private static String normalizeErrorMessage(Exception e) {
		String message = e.getMessage();
		if (message == null || message.trim().isEmpty()) {
			message = e.getClass().getSimpleName();
		}
		message = message.replace('\r', ' ').replace('\n', ' ').trim();
		return message.length() <= MAX_ERROR_MESSAGE_LENGTH ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
	}

}
