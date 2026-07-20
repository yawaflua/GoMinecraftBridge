package dev.yawaflua.gominecraftbridge.backend;

import dev.yawaflua.gominecraftbridge.protocol.Protocol;

import java.nio.file.Path;

public interface PluginBackend {
	int abiVersion();

	byte[] call(Protocol.Operation operation, byte[] input);

	Path origin();
}
