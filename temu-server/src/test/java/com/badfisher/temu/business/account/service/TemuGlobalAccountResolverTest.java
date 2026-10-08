package com.badfisher.temu.business.account.service;

import com.badfisher.temu.business.account.model.TemuAccessToken;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemuGlobalAccountResolverTest {

	private final TemuGlobalAccountResolver resolver = new TemuGlobalAccountResolver();

	@Test
	void shouldReturnConfiguredRegionalAccount() {
		TemuAccessToken requestAccount = requestAccount();
		TemuAccessToken caAccount = requestAccount();
		caAccount.setChannelId(1610);
		caAccount.setSite("CA");
		Map<Integer, TemuAccessToken> siteAccountMap = new HashMap<>();
		siteAccountMap.put(101, caAccount);

		TemuAccessToken resolved = resolver.resolve(101, siteAccountMap, requestAccount, "PO-1");

		assertSame(caAccount, resolved);
	}

	@Test
	void missingRegionalMappingShouldFailBeforePersistence() {
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> resolver.resolve(101, Collections.emptyMap(), requestAccount(), "PO-UNMAPPED"));
		org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("PO-UNMAPPED"));
		org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("site=ca"));
	}

	@Test
	void missingRegionalMappingShouldAlsoRejectAftersalesOwnership() {
		assertThrows(IllegalStateException.class,
				() -> resolver.resolveBySite("CA", Collections.emptyMap(), requestAccount(), "AS-1"));
	}

	@Test
	void configuredAccountWithoutChannelShouldBeRejected() {
		TemuAccessToken account = requestAccount();
		account.setChannelId(null);
		assertThrows(IllegalStateException.class,
				() -> resolver.resolve(101, Collections.singletonMap(101, account), requestAccount(), "PO-1"));
	}

	@Test
	void unknownSiteIdShouldStillFailBecauseSiteCannotBeDetermined() {
		assertThrows(IllegalStateException.class,
				() -> resolver.resolve(999, Collections.emptyMap(), requestAccount(), "PO-UNKNOWN"));
	}

	private static TemuAccessToken requestAccount() {
		TemuAccessToken account = new TemuAccessToken();
		account.setId(32L);
		account.setAppId("DEMO-APP-B");
		account.setChannelId(1582);
		account.setSellerId("900000000000001");
		account.setSite("AU");
		account.setAuthRegion("GLOBAL");
		account.setType(1);
		account.setAppKey("APP-KEY");
		account.setAppSecret("APP-SECRET");
		account.setAccessToken("ACCESS-TOKEN");
		account.setDelFlag(0);
		return account;
	}

}
