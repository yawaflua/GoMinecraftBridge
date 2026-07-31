package dev.yawaflua.gominecraftbridge.client.ui;

import java.util.LinkedHashSet;
import java.util.Set;

/** Commands selected on one local or remote package-management screen. */
public final class PackageScreenActions {
	private boolean rescan;
	private final Set<String> reloads = new LinkedHashSet<>();

	public boolean rescan() {
		return this.rescan;
	}

	public void rescan(boolean value) {
		this.rescan = value;
	}

	public Set<String> reloads() {
		return this.reloads;
	}
}
