package dev.yawaflua.gominecraftbridge.client;

import dev.yawaflua.gominecraftbridge.protocol.ClientScreenButton;

import java.util.Map;

interface ClientScreenCallbacks {
	void button(ClientScreenButton button, Map<String, String> values);

	void changed(String elementId, Map<String, String> values);

	void scrolled(String direction, Map<String, String> values);

	void selected(String listId, String itemId, Map<String, String> values);

	void submitted(String inputId, Map<String, String> values);

	void closed(Map<String, String> values);
}
