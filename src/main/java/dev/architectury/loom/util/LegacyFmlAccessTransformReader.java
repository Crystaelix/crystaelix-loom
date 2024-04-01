package dev.architectury.loom.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Pattern;

import dev.architectury.at.AccessChange;
import dev.architectury.at.AccessTransform;
import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.ModifierChange;
import org.cadixdev.bombe.type.signature.MethodSignature;

public class LegacyFmlAccessTransformReader {
	private static final char COMMENT_PREFIX = '#';
	private static final char WILDCARD = '*';
	private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

	public static void read(BufferedReader reader, AccessTransformSet set) throws IOException {
		String line;

		while ((line = reader.readLine()) != null) {
			line = substringBefore(line, COMMENT_PREFIX).trim();

			if (line.isEmpty()) {
				continue;
			}

			String[] parts = SPACE_PATTERN.split(line);

			if (parts.length != 2 && parts.length != 3) {
				continue;
			}

			AccessTransform transform = parseAccessTransform(parts[0]);

			AccessTransformSet.Class classSet = set.getOrCreateClass(parts[1]);

			if (parts.length == 2) {
				classSet.merge(transform);
			} else {
				String name = parts[2];
				int methodIndex = name.indexOf('(');

				if (name.charAt(0) == WILDCARD) {
					if (methodIndex != -1) {
						classSet.mergeAllMethods(transform);
					} else {
						classSet.mergeAllFields(transform);
					}
				} else if (methodIndex >= 0) {
					if (name.endsWith(")")) {
						classSet.mergeAllMethods(transform);
					} else {
						classSet.mergeMethod(MethodSignature.of(name.substring(0, methodIndex), name.substring(methodIndex)), transform);
					}
				} else {
					classSet.mergeField(name, transform);
				}
			}
		}
	}

	private static AccessTransform parseAccessTransform(String access) {
		int last = access.length() - 1;

		if (last < 2) {
			return AccessTransform.EMPTY;
		}

		ModifierChange finalChange;

		if (access.charAt(last) == 'f') {
			finalChange = parseFinalModifier(access.charAt(--last));
			access = access.substring(0, last);
		} else {
			finalChange = ModifierChange.NONE;
		}

		return AccessTransform.of(parseAccess(access), finalChange);
	}

	private static AccessChange parseAccess(String access) {
		return switch (access) {
		case "public" -> AccessChange.PUBLIC;
		case "protected" -> AccessChange.PROTECTED;
		case "default" -> AccessChange.PACKAGE_PRIVATE;
		case "private" -> AccessChange.PRIVATE;
		case "" -> AccessChange.NONE;
		default -> AccessChange.NONE;
		};
	}

	private static ModifierChange parseFinalModifier(char m) {
		return switch (m) {
		case '-' -> ModifierChange.REMOVE;
		case '+' -> ModifierChange.ADD;
		default -> ModifierChange.NONE;
		};
	}

	private static String substringBefore(String s, char c) {
		int pos = s.indexOf(c);
		return pos >= 0 ? s.substring(0, pos) : s;
	}

	private LegacyFmlAccessTransformReader() {
	}
}
