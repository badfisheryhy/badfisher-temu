package com.badfisher.temu.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

	@Test
	void masksNestedCredentialsAndPersonalDataWithoutChangingBusinessFields() {
		String result = LogSanitizer.sanitize("{\"success\":true,\"result\":[{\"access_token\":\"secret\","
				+ "\"shippingAddress\":{\"street\":\"private\"},\"buyerName\":\"person\",\"orderId\":123}]}");
		assertThat(result).doesNotContain("secret", "private", "person");
		assertThat(result).contains("\"success\":true", "\"orderId\":123");
	}

	@Test
	void omitsUnstructuredResponsesAndOversizedPayloads() {
		assertThat(LogSanitizer.sanitize("private raw text")).doesNotContain("private raw text");
		StringBuilder large = new StringBuilder("{\"data\":\"");
		for (int i = 0; i < 9000; i++) {
			large.append('x');
		}
		large.append("\"}");
		assertThat(LogSanitizer.sanitize(large.toString())).isEqualTo("{\"payloadOmitted\":true}");
	}

	@Test
	void masksTheAddressFieldsUsedByTheBusinessModel() {
		String result = LogSanitizer.sanitize("{\"receipt_name\":\"person\",\"mail\":\"private@example.test\","
				+ "\"post_code\":\"postal\",\"regionname1\":\"region\",\"mobile\":\"number\"}");
		assertThat(result).doesNotContain("person", "private@example.test", "postal", "\"region\"", "number");
	}

}
