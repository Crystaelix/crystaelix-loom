package dev.architectury.loom.legacyforge;

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
import org.apache.commons.compress.java.util.jar.Pack200;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class LegacyPatchConverter {
	public static byte[] convert(Logger logger, byte[] legacyPatches, String prefix) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (
				ByteArrayInputStream in = new ByteArrayInputStream(unpack200Lzma(legacyPatches));
				JarInputStream jarIn = new JarInputStream(in);
				PushbackInputStream pushbackIn = new PushbackInputStream(jarIn);
				DataInputStream dataIn = new DataInputStream(pushbackIn);
				LZMACompressorOutputStream lzmaOut = new LZMACompressorOutputStream(out);
				JarOutputStream jarOut = new JarOutputStream(lzmaOut);
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

	private static byte[] unpack200Lzma(byte[] bytes) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (
				ByteArrayInputStream in = new ByteArrayInputStream(bytes);
				LZMACompressorInputStream lzmaIn = new LZMACompressorInputStream(in);
				JarOutputStream jarOut = new JarOutputStream(out)
		) {
			Pack200.newUnpacker().unpack(lzmaIn, jarOut);
		}

		return out.toByteArray();
	}
}
