package com.badfisher.temu.business.infrastructure.persistence;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

/** Audit timestamps are owned by the backend; no corporate user service is required. */
@Configuration
public class MybatisConfiguration {

	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
		PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
		pagination.setMaxLimit(200L);
		interceptor.addInnerInterceptor(pagination);
		return interceptor;
	}

	@Bean
	public MetaObjectHandler auditMetaObjectHandler() {
		return new MetaObjectHandler() {
			@Override
			public void insertFill(MetaObject metaObject) {
				Date now = new Date();
				strictInsertFill(metaObject, "createTime", Date.class, now);
				strictInsertFill(metaObject, "updateTime", Date.class, now);
				strictInsertFill(metaObject, "createUserId", String.class, "system");
				strictInsertFill(metaObject, "updateUserId", String.class, "system");
			}

			@Override
			public void updateFill(MetaObject metaObject) {
				setFieldValByName("updateTime", new Date(), metaObject);
				setFieldValByName("updateUserId", "system", metaObject);
			}
		};
	}

}
