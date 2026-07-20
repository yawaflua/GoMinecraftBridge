package dev.yawaflua.gominecraftbridge.backend;

public final class PluginInvocationException extends RuntimeException {
	public PluginInvocationException(String message) {
		super(message);
	}

	public PluginInvocationException(String message, Throwable cause) {
		super(message, cause);
	}
}
