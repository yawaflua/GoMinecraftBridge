package dev.yawaflua.gominecraftbridge.protocol;

import com.google.gson.JsonElement;

import java.util.List;

public record PluginResponse(
		String status,
		String error,
		String stack,
		JsonElement data,
		List<PluginLog> logs,
		List<ActionRequest> actions,
		List<SystemCallRequest> systemCalls,
		SnapshotSubscription snapshot
) {
	public PluginResponse {
		logs = logs == null ? List.of() : List.copyOf(logs);
		actions = actions == null ? List.of() : List.copyOf(actions);
		systemCalls = systemCalls == null ? List.of() : List.copyOf(systemCalls);
	}

	public boolean isPanic() {
		return "panic".equals(status);
	}

	public boolean isError() {
		return !"ok".equals(status);
	}
}
