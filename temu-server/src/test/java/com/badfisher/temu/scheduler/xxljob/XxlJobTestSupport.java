package com.badfisher.temu.scheduler.xxljob;

import com.xxl.job.core.context.XxlJobContext;

/** 使用真实执行上下文调用 Handler，并在测试结束后恢复线程状态。 */
public final class XxlJobTestSupport {

	private XxlJobTestSupport() {
	}

	public static XxlJobContext executeJob(String parameter, Runnable handler) {
		XxlJobContext previous = XxlJobContext.getXxlJobContext();
		XxlJobContext context = new XxlJobContext(1L, parameter, null, 0, 1);

		try {
			XxlJobContext.setXxlJobContext(context);
			handler.run();
			return context;
		}
		finally {
			XxlJobContext.setXxlJobContext(previous);
		}
	}

}
