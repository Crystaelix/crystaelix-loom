package net.fabricmc.loom.configuration.providers.mappings.utils;

import java.io.IOException;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;

public class DstClassNameSkippingMappingVisitor extends ForwardingMappingVisitor {
	public DstClassNameSkippingMappingVisitor(MappingVisitor next) {
		super(next);
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
		if (targetKind == MappedElementKind.CLASS) {
			return;
		}

		super.visitDstName(targetKind, namespace, name);
	}
}
