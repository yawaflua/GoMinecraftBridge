package dev.yawaflua.gominecraftbridge.client;

import com.terraformersmc.modmenu.api.UpdateChecker;
import com.terraformersmc.modmenu.api.UpdateInfo;
import com.terraformersmc.modmenu.util.mod.Mod;
import com.terraformersmc.modmenu.util.mod.fabric.FabricIconHandler;
import dev.yawaflua.gominecraftbridge.protocol.PluginMetadata;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/** Adapts native plugin metadata to the model rendered by Mod Menu. */
final class NativeModMenuEntry implements Mod {
	private static final String UNKNOWN_ICON = "assets/modmenu/unknown_icon.png";

	private final PluginMetadata metadata;
	private UpdateChecker updateChecker;
	private UpdateInfo updateInfo;
	private boolean childHasUpdate;

	NativeModMenuEntry(PluginMetadata metadata) {
		this.metadata = Objects.requireNonNull(metadata, "metadata");
	}

	@Override
	public String getId() {
		return this.metadata.id();
	}

	@Override
	public String getName() {
		return this.metadata.name();
	}

	@Override
	public DynamicTexture getIcon(FabricIconHandler iconHandler, int size) {
		var modMenu = FabricLoader.getInstance().getModContainer("modmenu")
				.orElseThrow(() -> new IllegalStateException("Mod Menu container is unavailable"));
		return Objects.requireNonNull(iconHandler.createIcon(modMenu, UNKNOWN_ICON));
	}

	@Override
	public String getDescription() {
		String description = this.metadata.description();
		String header = "Native Go plugin • " + this.metadata.environment().name().toLowerCase();
		return description == null || description.isBlank() ? header : header + "\n\n" + description;
	}

	@Override
	public String getVersion() {
		return this.metadata.version();
	}

	@Override
	public String getPrefixedVersion() {
		return this.metadata.version();
	}

	@Override
	public List<String> getAuthors() {
		return this.metadata.authors();
	}

	// Required by Mod Menu 18; harmless as an additional public method on Mod Menu 11.
	public ContactInformation getContact(String key) {
		return ContactInformation.EMPTY;
	}

	@Override
	public Map<String, Collection<String>> getContributors() {
		return Map.of();
	}

	@Override
	public SortedMap<String, Set<String>> getCredits() {
		return new TreeMap<>();
	}

	@Override
	public Set<Badge> getBadges() {
		return Set.of(Badge.CLIENT);
	}

	@Override
	public String getWebsite() {
		return this.metadata.website() == null ? "" : this.metadata.website();
	}

	@Override
	public String getIssueTracker() {
		return "";
	}

	@Override
	public String getSource() {
		return "";
	}

	@Override
	public String getParent() {
		return null;
	}

	@Override
	public Set<String> getLicense() {
		return Set.of();
	}

	@Override
	public Map<String, String> getLinks() {
		return getWebsite().isBlank() ? Map.of() : Map.of("homepage", getWebsite());
	}

	@Override
	public boolean isReal() {
		return true;
	}

	@Override
	public boolean allowsUpdateChecks() {
		return false;
	}

	@Override
	public UpdateChecker getUpdateChecker() {
		return this.updateChecker;
	}

	@Override
	public void setUpdateChecker(UpdateChecker updateChecker) {
		this.updateChecker = updateChecker;
	}

	@Override
	public UpdateInfo getUpdateInfo() {
		return this.updateInfo;
	}

	@Override
	public void setUpdateInfo(UpdateInfo updateInfo) {
		this.updateInfo = updateInfo;
	}

	@Override
	public void setChildHasUpdate() {
		this.childHasUpdate = true;
	}

	@Override
	public boolean getChildHasUpdate() {
		return this.childHasUpdate;
	}

	@Override
	public boolean isHidden() {
		return false;
	}
}
