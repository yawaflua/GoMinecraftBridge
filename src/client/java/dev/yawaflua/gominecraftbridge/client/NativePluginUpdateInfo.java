package dev.yawaflua.gominecraftbridge.client;

import com.terraformersmc.modmenu.api.UpdateChannel;
import com.terraformersmc.modmenu.api.UpdateInfo;


public record NativePluginUpdateInfo(
		String downloadLink,
		UpdateChannel updateChannel
) implements UpdateInfo {
	public NativePluginUpdateInfo {
		downloadLink = downloadLink == null ? "" : downloadLink;
		updateChannel = updateChannel == null ? UpdateChannel.RELEASE : updateChannel;
	}

	@Override
	public boolean isUpdateAvailable() {
		return true;
	}

	@Override
	public String getDownloadLink() {
		return this.downloadLink;
	}

	@Override
	public UpdateChannel getUpdateChannel() {
		return this.updateChannel;
	}
}
