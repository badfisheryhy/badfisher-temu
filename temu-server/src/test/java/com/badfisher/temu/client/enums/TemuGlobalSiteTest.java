package com.badfisher.temu.client.enums;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TemuGlobalSite} 单元测试。
 *
 * @author lenkor
 */
public class TemuGlobalSiteTest {

	@Test
	public void shouldResolveBySiteId() {
		assertEquals(TemuGlobalSite.US, TemuGlobalSite.fromSiteId(100));
		assertEquals(TemuGlobalSite.CA, TemuGlobalSite.fromSiteId(101));
		assertEquals(TemuGlobalSite.UK, TemuGlobalSite.fromSiteId(102));
		assertEquals(TemuGlobalSite.AU, TemuGlobalSite.fromSiteId(103));
		assertEquals(TemuGlobalSite.NZ, TemuGlobalSite.fromSiteId(104));
		assertEquals(TemuGlobalSite.BG, TemuGlobalSite.fromSiteId(141));
		assertEquals(TemuGlobalSite.XK, TemuGlobalSite.fromSiteId(161));
		assertEquals(TemuGlobalSite.LI, TemuGlobalSite.fromSiteId(197));
		assertNull(TemuGlobalSite.fromSiteId(999));
	}

	@Test
	public void shouldResolveCodeIgnoringCase() {
		assertEquals(TemuGlobalSite.US, TemuGlobalSite.fromCode("us"));
		assertEquals(TemuGlobalSite.CA, TemuGlobalSite.fromCode(" CA "));
		assertEquals(TemuGlobalSite.UK, TemuGlobalSite.fromCode("Uk"));
		assertEquals(TemuGlobalSite.AU, TemuGlobalSite.fromCode("AU"));
		assertEquals(TemuGlobalSite.DE, TemuGlobalSite.fromCode("de"));
		assertEquals(TemuGlobalSite.LI, TemuGlobalSite.fromCode(" LI "));
		assertNull(TemuGlobalSite.fromCode(null));
	}

	@Test
	public void shouldResolveBySiteName() {
		assertEquals(TemuGlobalSite.CA, TemuGlobalSite.fromSiteName("加拿大站"));
		assertEquals(TemuGlobalSite.GR, TemuGlobalSite.fromSiteName("希腊"));
		assertEquals(TemuGlobalSite.TT, TemuGlobalSite.fromSiteName("特立尼达和多巴哥站"));
		assertNull(TemuGlobalSite.fromSiteName("中国站"));
	}

	@Test
	public void shouldMaintainOpenStatus() {
		assertTrue(TemuGlobalSite.CA.isOpen());
		assertTrue(TemuGlobalSite.LI.isOpen());
		assertFalse(TemuGlobalSite.KR.isOpen());
		assertFalse(TemuGlobalSite.XK.isOpen());
	}

	@Test
	public void siteMetadataShouldBeCompleteAndUnique() {
		TemuGlobalSite[] sites = TemuGlobalSite.values();
		assertEquals(96, sites.length);

		Set<Integer> siteIds = new HashSet<>();
		Set<String> codes = new HashSet<>();
		Set<String> names = new HashSet<>();
		for (TemuGlobalSite site : sites) {
			assertTrue(siteIds.add(site.getSiteId()), "重复siteId: " + site.getSiteId());
			assertTrue(codes.add(site.getCode()), "重复code: " + site.getCode());
			assertTrue(names.add(site.getSiteName()), "重复siteName: " + site.getSiteName());
		}
	}

}
