/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.configuration.mods;

import dev.architectury.at.AccessChange;
import dev.architectury.at.AccessTransform;
import dev.architectury.at.AccessTransformSet;
import dev.architectury.at.ModifierChange;
import org.cadixdev.bombe.type.signature.MethodSignature;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class AccessTransformerClassVisitor extends ClassVisitor {
	private final AccessTransformSet accessTransformSet;
	private String className;
	private int classAccess;

	AccessTransformerClassVisitor(int api, ClassVisitor classVisitor, AccessTransformSet accessTransformSet) {
		super(api, classVisitor);
		this.accessTransformSet = accessTransformSet;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		classAccess = access;

		super.visit(
				version,
				modifyAccess(access, getClassAccess()),
				name,
				signature,
				superName,
				interfaces
		);
	}

	@Override
	public void visitInnerClass(String name, String outerName, String innerName, int access) {
		super.visitInnerClass(
				name,
				outerName,
				innerName,
				modifyAccess(access, getClassAccess())
		);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		return super.visitField(
				modifyAccess(access, getFieldAccess(name)),
				name,
				descriptor,
				signature,
				value
		);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return new AccessTransformSetMethodVisitor(super.visitMethod(
				modifyAccess(access, getMethodAccess(name, descriptor)),
				name,
				descriptor,
				signature,
				exceptions
		));
	}

	private class AccessTransformSetMethodVisitor extends MethodVisitor {
		AccessTransformSetMethodVisitor(MethodVisitor methodVisitor) {
			super(AccessTransformerClassVisitor.this.api, methodVisitor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if (opcode == Opcodes.INVOKESPECIAL && isTargetMethod(owner, name, descriptor)) {
				opcode = Opcodes.INVOKEVIRTUAL;
			}

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			for (int i = 0; i < bootstrapMethodArguments.length; i++) {
				if (bootstrapMethodArguments[i] instanceof Handle handle) {
					if (handle.getTag() == Opcodes.H_INVOKESPECIAL && isTargetMethod(handle.getOwner(), handle.getName(), handle.getDesc())) {
						bootstrapMethodArguments[i] = new Handle(Opcodes.H_INVOKEVIRTUAL, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
					}
				}
			}

			super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
		}

		private boolean isTargetMethod(String owner, String name, String descriptor) {
			return owner.equals(className) && !name.equals("<init>") && getMethodAccess(name, descriptor).getAccess() != AccessChange.NONE;
		}
	}

	private AccessTransform getClassAccess() {
		return accessTransformSet.getClass(className).map(c -> c.get()).orElse(AccessTransform.EMPTY);
	}

	private AccessTransform getFieldAccess(String name) {
		AccessTransform allFields = accessTransformSet.getClass(className).map(c -> c.allFields()).orElse(AccessTransform.EMPTY);
		AccessTransform field = accessTransformSet.getClass(className).map(c -> c.getField(name)).orElse(AccessTransform.EMPTY);
		return allFields.merge(field);
	}

	private AccessTransform getMethodAccess(String name, String descriptor) {
		MethodSignature methodSignature = MethodSignature.of(name, descriptor);
		AccessTransform allMethods = accessTransformSet.getClass(className).map(c -> c.allMethods()).orElse(AccessTransform.EMPTY);
		AccessTransform method = accessTransformSet.getClass(className).map(c -> c.getMethod(methodSignature)).orElse(AccessTransform.EMPTY);
		return allMethods.merge(method);
	}

	private static int modifyAccess(int access, AccessTransform accessTransform) {
		AccessChange accessChange = accessTransform.getAccess();
		ModifierChange modifierChange = accessTransform.getFinal();

		if (accessChange != AccessChange.NONE) {
			access = (access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | accessChange.getModifier();
		}

		if (modifierChange == ModifierChange.REMOVE) {
			access &= ~Opcodes.ACC_FINAL;
		}

		if (modifierChange == ModifierChange.REMOVE) {
			access |= Opcodes.ACC_FINAL;
		}

		return access;
	}
}
