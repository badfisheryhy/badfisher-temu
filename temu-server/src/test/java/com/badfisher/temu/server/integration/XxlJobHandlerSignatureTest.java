package com.badfisher.temu.server.integration;

import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XxlJobHandlerSignatureTest {

	@Test
	void allHandlersShouldMatchXxlJob250MethodSignature() throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

		List<String> invalidHandlers = new ArrayList<>();
		Set<String> handlerNames = new HashSet<>();
		RegistrationExecutor executor = new RegistrationExecutor();
		for (org.springframework.beans.factory.config.BeanDefinition beanDefinition : scanner
			.findCandidateComponents("com.badfisher.temu.scheduler.xxljob")) {
			Class<?> jobClass = ClassUtils.forName(beanDefinition.getBeanClassName(), getClass().getClassLoader());
			Object job = jobClass.getDeclaredConstructor().newInstance();
			for (Method method : jobClass.getDeclaredMethods()) {
				if (method.getAnnotation(XxlJob.class) == null) {
					continue;
				}
				assertTrue(handlerNames.add(method.getAnnotation(XxlJob.class).value()), "Handler 名称不能重复");
				boolean valid = Modifier.isPublic(method.getModifiers()) && void.class.equals(method.getReturnType())
						&& method.getParameterCount() == 0;
				if (!valid) {
					invalidHandlers.add(jobClass.getName() + "#" + method.getName());
				}
				executor.register(job, method);
				assertTrue(XxlJobExecutor.loadJobHandler(method.getAnnotation(XxlJob.class).value())
						instanceof MethodJobHandler, "Handler 必须能通过真实执行器注册");
			}
		}

		assertTrue(invalidHandlers.isEmpty(),
				"XXL-JOB 2.5.0 handler 使用 public void method()，从上下文获取参数和上报结果: " + invalidHandlers);
		assertEquals(26, handlerNames.size(), "升级不能遗漏原有调度入口");
	}

	private static class RegistrationExecutor extends XxlJobExecutor {

		void register(Object job, Method method) {
			registJobHandler(method.getAnnotation(XxlJob.class), job, method);
		}

	}

}
