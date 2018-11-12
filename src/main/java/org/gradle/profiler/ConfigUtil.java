package org.gradle.profiler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigUtil {

	public static Map<String, String> map(Config config, String key, Map<String, String> defaultValues) {
		if (config.hasPath(key)) {
			Map<String, String> props = new LinkedHashMap<>();
			for (Map.Entry<String, ConfigValue> entry : config.getConfig(key).entrySet()) {
				props.put(entry.getKey(), entry.getValue().unwrapped().toString());
			}
			return props;
		} else {
			return defaultValues;
		}
	}

	public static Invoker invoker(Config config, String key, Invoker defaultValue) {
		if (config.hasPath(key)) {
			String value = config.getAnyRef(key).toString();
			if (value.equals("no-daemon")) {
				return Invoker.NoDaemon;
			}
			if (value.equals("cli")) {
				return Invoker.Cli;
			}
			if (value.equals("tooling-api")) {
				return Invoker.ToolingApi;
			}
			throw new IllegalArgumentException("Unexpected value for '" + key + "' provided: " + value);
		} else {
			return defaultValue;
		}
	}

	public static int integer(Config config, String key, int defaultValue) {
		if (config.hasPath(key)) {
			return Integer.valueOf(config.getString(key));
		} else {
			return defaultValue;
		}
	}

	public static <T extends Enum<T>> T enumValue(Config config, String key, Class<T> type, T defaultValue) {
		if (config.hasPath(key)) {
			return config.getEnum(type, key);
		} else {
			return defaultValue;
		}
	}

	public static String string(Config config, String key, String defaultValue) {
		if (config.hasPath(key)) {
			return config.getString(key);
		} else {
			return defaultValue;
		}
	}

	public static List<String> strings(Config config, String key, List<String> defaults) {
		if (config.hasPath(key)) {
			Object value = config.getAnyRef(key);
			if (value instanceof List) {
				List<?> list = (List) value;
				return list.stream().map(Object::toString).collect(Collectors.toList());
			} else if (value.toString().length() > 0) {
				return Collections.singletonList(value.toString());
			}
		}
		return defaults;
	}
}
