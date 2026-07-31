package dev.yawaflua.gominecraftbridge.catalog;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Validates a downloaded archive against immutable backend release metadata. */
final class PackageIntegrityVerifier {
	String verify(CatalogVersion version, byte[] data) throws IOException {
		if (version.sizeBytes() <= 0 || version.sizeBytes() != data.length) {
			throw new IOException("Downloaded package size does not match backend metadata");
		}
		if (!version.sha256().matches("(?i)[0-9a-f]{64}")) {
			throw new IOException("Backend version does not contain a valid SHA-256 digest");
		}
		String actual = sha256(data);
		if (!actual.equalsIgnoreCase(version.sha256())) {
			throw new IOException("Downloaded package SHA-256 does not match backend metadata");
		}
		return actual;
	}

	private static String sha256(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
