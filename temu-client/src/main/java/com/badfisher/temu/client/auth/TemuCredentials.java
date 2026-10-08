package com.badfisher.temu.client.auth;

/** Platform credentials supplied by the caller; the client does not access a database. */
public interface TemuCredentials {

	String getAppId();

	String getAppKey();

	String getAppSecret();

	String getAccessToken();

	String getSellerId();

	String getAuthRegion();

	String getSite();

	Integer getType();

}
