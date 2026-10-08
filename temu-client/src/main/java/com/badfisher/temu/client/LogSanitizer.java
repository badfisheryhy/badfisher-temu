package com.badfisher.temu.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Removes credentials and personal fields before payloads enter operational logs. */
public final class LogSanitizer {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final int MAX_LENGTH = 8192;

	private LogSanitizer() {
	}

	public static String sanitize(String json) {
		if (json == null) {
			return null;
		}
		try {
			JsonNode tree = MAPPER.readTree(json);
			mask(tree);
			String result = MAPPER.writeValueAsString(tree);
			return result.length() <= MAX_LENGTH ? result : "{\"payloadOmitted\":true}";
		}
		catch (Exception e) {
			return "{\"unstructuredPayloadOmitted\":true}";
		}
	}

	private static void mask(JsonNode node) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			ObjectNode object = (ObjectNode) node;
			List<String> fields = new ArrayList<>();
			object.fieldNames().forEachRemaining(fields::add);
			for (String field : fields) {
				String name = field.replace("_", "").toLowerCase(Locale.ROOT);
				if (name.contains("token") || name.contains("secret") || name.contains("password")
						|| name.contains("address") || name.contains("phone") || name.contains("mobile")
						|| name.contains("email") || name.contains("buyer") || name.contains("recipient")
						|| name.contains("receiver") || name.contains("receipt") || name.startsWith("regionname")
						|| name.equals("mail") || name.equals("postcode") || name.equals("zipcode")
						|| name.equals("firstname") || name.equals("lastname")
						|| name.equals("name") || name.equals("fullname")
						|| name.equals("appkey") || name.equals("sign")) {
					object.put(field, "***");
				}
				else {
					mask(object.get(field));
				}
			}
		}
		else if (node.isArray()) {
			Iterator<JsonNode> children = node.elements();
			while (children.hasNext()) {
				mask(children.next());
			}
		}
	}

}
