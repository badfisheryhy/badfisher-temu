package com.badfisher.temu.business.sync.execution;

/** 执行身份或租约不可用时中止业务处理；不得按平台临时错误重试。 */
public class TaskLeaseLostException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	public TaskLeaseLostException(String message) {
		super(message);
	}

	public TaskLeaseLostException(String message, Throwable cause) {
		super(message, cause);
	}

}
