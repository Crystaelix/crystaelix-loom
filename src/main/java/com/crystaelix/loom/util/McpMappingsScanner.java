package com.crystaelix.loom.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class McpMappingsScanner {
	public static final Set<String> INTERESTING_FILENAMES = new HashSet<>(Arrays.asList(
			"joined.srg", "joined.csrg", "joined.tsrg", "packaged.srg",
			"client.srg", "server.srg",
			"fields.csv", "methods.csv", "params.csv", "packages.csv",
			"config.json",
			"joined.exc", "packaged.exc"
	));

	private final Map<String, Path> interestingFiles = new HashMap<>();

	public McpMappingsScanner(Path rootPath) throws IOException {
		Files.walk(rootPath).forEach(path -> {
			String filename = String.valueOf(path.getFileName());

			if (INTERESTING_FILENAMES.contains(filename)) {
				if (!interestingFiles.containsKey(filename) || path.getParent() != null && path.getParent().endsWith("conf")) {
					interestingFiles.put(filename.intern(), path);
				}
			}
		});
	}

	public Optional<Path> get(String name) {
		return Optional.ofNullable(interestingFiles.get(name));
	}
}
