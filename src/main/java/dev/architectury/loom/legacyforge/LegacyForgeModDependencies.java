package dev.architectury.loom.legacyforge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Manifest;

import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.io.AccessTransformFormats;

import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.FileSystemUtil;
import net.fabricmc.loom.util.ZipUtils;
import net.fabricmc.mappingio.tree.MappingTreeView;

public class LegacyForgeModDependencies {
	public static void remapAts(Path jar, MappingTreeView mappings, String from, String to) throws IOException {
		byte[] manifestFile = ZipUtils.unpack(jar, "META-INF/MANIFEST.MF");
		Manifest manifest = new Manifest(new ByteArrayInputStream(manifestFile));
		String atPaths = manifest.getMainAttributes().getValue(Constants.LegacyForge.ACCESS_TRANSFORMERS_MANIFEST_KEY);

		if (atPaths != null) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar)) {
				for (String atPathStr : atPaths.split(" ")) {
					final Path atPath = fs.getPath("META-INF", atPathStr);

					if (Files.exists(atPath)) {
						AccessTransformSet ats = AccessTransformFormats.FML.read(atPath);
						ats = ats.remap(mappings, from, to);
						AccessTransformFormats.FML.write(atPath, ats);
					}
				}
			}
		}
	}
}
