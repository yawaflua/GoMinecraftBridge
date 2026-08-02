package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;

import java.util.Map;

interface ClientScreenCallbacks {
	void button(ClientScreenButton button, Map<String, String> values);

	void closed(Map<String, String> values);
}
