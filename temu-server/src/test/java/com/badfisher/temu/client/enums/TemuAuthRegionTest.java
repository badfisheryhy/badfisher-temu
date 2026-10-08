package com.badfisher.temu.client.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TemuAuthRegionTest {

	@Test
	void shouldParseRegionCaseInsensitively() {
		assertEquals(TemuAuthRegion.US, TemuAuthRegion.fromCode("us"));
		assertEquals(TemuAuthRegion.GLOBAL, TemuAuthRegion.fromCode(" Global "));
		assertEquals(TemuAuthRegion.EU, TemuAuthRegion.fromCode("EU"));
		assertEquals(TemuAuthRegion.CN, TemuAuthRegion.fromCode("cn"));
		assertNull(TemuAuthRegion.fromCode("AU"));
	}

	@Test
	void shouldMigrateBusinessSiteToAuthRegion() {
		assertEquals(TemuAuthRegion.US, TemuAuthRegion.fromBusinessSite("US"));
		assertEquals(TemuAuthRegion.GLOBAL, TemuAuthRegion.fromBusinessSite("CA"));
		assertEquals(TemuAuthRegion.GLOBAL, TemuAuthRegion.fromBusinessSite("AU"));
		assertEquals(TemuAuthRegion.EU, TemuAuthRegion.fromBusinessSite("UK"));
		assertEquals(TemuAuthRegion.CN, TemuAuthRegion.fromBusinessSite("CN"));
	}

}
