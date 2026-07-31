package dev.yawaflua.gominecraftbridge.catalog;

import java.util.List;

/** Summary of one automatic update pass. */
public record CatalogUpdateRun(int checked, int installed, List<String> failures) {
	public CatalogUpdateRun {
		failures = failures == null ? List.of() : List.copyOf(failures);
	}
}
