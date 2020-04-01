package org.gradle.profiler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConfigUtil {

	public static Map<String, String> map(Config config, String key, Map<String, String> defaultValues) {
		if (config.hasPath(key)) {
			Map<String, String> props = new LinkedHashMap<>();
			for (Map.Entry<String, ConfigValue> entry : config.getObject(key).entrySet()) {
				props.put(entry.getKey(), entry.getValue().unwrapped().toString());
			}
			return props;
		} else {
			return defaultValues;
		}
	}

	public static Integer optionalInteger(Config config, String key) {
		if (config.hasPath(key)) {
			return Integer.valueOf(config.getString(key));
		} else {
			return null;
		}
	}

	public static <T extends Enum<T>> T enumValue(Config config, String key, Class<T> type, T defaultValue) {
		if (config.hasPath(key)) {
			return config.getEnum(type, key);
		} else {
			return defaultValue;
		}
	}

	public static String string(Config config, String key) {
		if (config.hasPath(key)) {
			return config.getString(key);
		} else {
            throw new IllegalArgumentException("Key '" + key + "' is missing.");
		}
	}

	public static String string(Config config, String key, String defaultValue) {
		if (config.hasPath(key)) {
			return config.getString(key);
		} else {
			return defaultValue;
		}
	}

	public static List<String> strings(Config config, String key) {
		return strings(config, key, Collections.emptyList());
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

    public static boolean bool(Config config, String key, boolean defaultValue) {
        if (config.hasPath(key)) {
            return config.getBoolean(key);
        } else {
            return defaultValue;
        }
    }
}
