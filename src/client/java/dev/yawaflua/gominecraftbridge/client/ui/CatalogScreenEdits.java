package dev.yawaflua.gominecraftbridge.client.ui;

import dev.yawaflua.gominecraftbridge.catalog.CatalogSettings;

import java.util.LinkedHashSet;
import java.util.Set;

/** Mutable form state scoped to one Cloth screen instance. */
public final class CatalogScreenEdits {
	private String backendUrl;
	private boolean automaticUpdates;
	private String searchQuery = "";
	private boolean runSearch;
	private final Set<String> installs = new LinkedHashSet<>();

	public CatalogScreenEdits(CatalogSettings settings) {
		this.backendUrl = settings.backendUrl();
		this.automaticUpdates = settings.automaticUpdates();
	}

	public String backendUrl() {
		return this.backendUrl;
	}

	public void backendUrl(String value) {
		this.backendUrl = value;
	}

	public boolean automaticUpdates() {
		return this.automaticUpdates;
	}

	public void automaticUpdates(boolean value) {
		this.automaticUpdates = value;
	}

	public String searchQuery() {
		return this.searchQuery;
	}

	public void searchQuery(String value) {
		this.searchQuery = value;
	}

	public boolean runSearch() {
		return this.runSearch;
	}

	public void runSearch(boolean value) {
		this.runSearch = value;
	}

	public Set<String> installs() {
		return this.installs;
	}
}
