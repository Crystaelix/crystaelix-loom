package dev.architectury.loom.forge;

public class ForgeVersion {
	private final String group;
	private final String name;
	private final String combined;
	private final String minecraftVersion;
	private final String forgeVersion;
	private final int majorVersion;
	private final int buildNumber;

	public ForgeVersion(String group, String name, String combined) {
		this.group = group;
		this.name = name;
		this.combined = combined;

		if (combined == null) {
			this.minecraftVersion = "NO_VERSION";
			this.forgeVersion = "NO_VERSION";
			this.majorVersion = -1;
			this.buildNumber = -1;
			return;
		}

		int hyphenIndex = combined.indexOf('-');
		String forge;

		if (hyphenIndex != -1) {
			this.minecraftVersion = combined.substring(0, hyphenIndex);
			forge = combined.substring(hyphenIndex + 1);
		} else {
			this.minecraftVersion = "NO_VERSION";
			forge = combined;
		}

		hyphenIndex = forge.indexOf('-');

		if (hyphenIndex != -1) {
			this.forgeVersion = forge.substring(0, hyphenIndex);
		} else {
			this.forgeVersion = forge;
		}

		int dotIndex = forgeVersion.indexOf('.');
		int major;

		try {
			if (dotIndex >= 0) {
				major = Integer.parseInt(forgeVersion.substring(0, dotIndex));
			} else {
				major = Integer.parseInt(forgeVersion);
			}
		} catch (NumberFormatException e) {
			major = -1;
		}

		this.majorVersion = major;

		dotIndex = forgeVersion.lastIndexOf('.');
		int build;

		try {
			if (dotIndex >= 0) {
				build = Integer.parseInt(forgeVersion.substring(dotIndex + 1));
			} else {
				build = -1;
			}
		} catch (NumberFormatException e) {
			build = -1;
		}

		this.buildNumber = build;
	}

	public String getCombined() {
		return combined;
	}

	public String getMinecraftVersion() {
		return minecraftVersion;
	}

	public String getForgeVersion() {
		return forgeVersion;
	}

	public int getMajorVersion() {
		return majorVersion;
	}

	public int getBuildNumber() {
		return buildNumber;
	}

	/**
	 * @return if this Forge version uses the userdev3 classifier for userdev.
	 */
	public boolean userdev3() {
		return "net.minecraftforge".equals(group) && "forge".equals(name)
				&& majorVersion == 14 && buildNumber > 2847;
	}

	/**
	 * @return if this Forge version has Minecraft version mod directories.
	 */
	public boolean versionModDirs() {
		return majorVersion < 14 || majorVersion == 14 && buildNumber < 2656;
	}

	/**
	 * @return if this Forge version has FML in the cpw.mods package.
	 */
	public boolean cpwFml() {
		return majorVersion < 11;
	}
}
