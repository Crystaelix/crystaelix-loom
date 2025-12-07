/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
			"constructors.txt",
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
