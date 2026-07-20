package dev.yawaflua.gominecraftbridge.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SystemCallRegistry {
	private final Map<String, SystemCallHandler> handlers = new LinkedHashMap<>();

	public synchronized void register(String name, SystemCallHandler handler) {
		validateName(name);
		Objects.requireNonNull(handler, "handler");

		if (this.handlers.putIfAbsent(name, handler) != null) {
			throw new IllegalArgumentException("System call is already registered: " + name);
		}
	}

	public synchronized Optional<SystemCallHandler> find(String name) {
		return Optional.ofNullable(this.handlers.get(name));
	}

	public synchronized Map<String, SystemCallHandler> entries() {
		return Map.copyOf(this.handlers);
	}

	private static void validateName(String name) {
		if (name == null || !name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
			throw new IllegalArgumentException("System call must be a namespaced identifier: " + name);
		}
	}
}
