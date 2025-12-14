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

package com.crystaelix.loom.legacyforge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;

public class LegacyPatchConverter {
	public static byte[] convert(Logger logger, byte[] legacyPatches, String prefix) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (
				JarInputStream jarIn = new JarInputStream(
						new Pack200CompressorInputStream(
								new LZMACompressorInputStream(
										new ByteArrayInputStream(legacyPatches)
								)
						)
				);
				PushbackInputStream pushbackIn = new PushbackInputStream(jarIn);
				DataInputStream dataIn = new DataInputStream(pushbackIn);

				JarOutputStream jarOut = new JarOutputStream(
						new LZMACompressorOutputStream(out)
				);
				DataOutputStream dataOut = new DataOutputStream(jarOut)
		) {
			for (JarEntry entry; (entry = jarIn.getNextJarEntry()) != null;) {
				String name = entry.getName();

				if (!name.startsWith(prefix)) {
					continue;
				}

				jarOut.putNextEntry(new JarEntry(name.substring(prefix.length())));

				// Check version, long class name unlikely
				int version = pushbackIn.read();
				pushbackIn.unread(version);

				if (version != 1) {
					// Convert from legacy format to modern (v1) format
					dataOut.writeByte(1); // version
					dataIn.readUTF(); // unused patch name (presumably always the same as the obf class name)
					dataOut.writeUTF(dataIn.readUTF().replace('.', '/')); // obf class name
					dataOut.writeUTF(dataIn.readUTF().replace('.', '/')); // srg class name
				}

				IOUtils.copy(pushbackIn, dataOut);
				jarOut.closeEntry();
			}
		}

		return out.toByteArray();
	}
}
