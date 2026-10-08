package com.badfisher.temu.business.sync.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.sync.model.SyncTask;
import com.badfisher.temu.business.sync.enums.TemuPullType;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractTemuPullJobTest {

	@Test
	void accountQueryShouldUseAuthRegionCode() {
		MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
		assistant.setCurrentNamespace("test");
		TableInfoHelper.initTableInfo(assistant, TemuAccessToken.class);
		TestPullJob job = new TestPullJob();

		LambdaQueryWrapper<TemuAccessToken> query = job.authAccountQuery(TemuAuthRegion.GLOBAL);
		query.getSqlSegment();

		assertTrue(query.getParamNameValuePairs().containsValue("GLOBAL"));
		assertFalse(query.getParamNameValuePairs().containsValue(TemuAuthRegion.GLOBAL));
	}

	private static final class TestPullJob extends AbstractTemuPullJob {

		@Override
		protected TemuPullType pullType() {
			return TemuPullType.ORDER;
		}

		@Override
		protected void doPull(TemuAccessToken account, SyncTask task, TemuAuthRegion authRegion) {
			// 查询条件测试不执行实际拉取。
		}

	}

}
