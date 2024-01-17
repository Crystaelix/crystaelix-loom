package dev.architectury.loom.util.legacyforge;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Transforms Forge's CoreModManager class to search the classpath for coremods.
 * For motivation, see comments at usage site.
 */
public class CoreModManagerTransformer extends ClassVisitor {
	private static final String CLASS = "net/minecraftforge/fml/relauncher/CoreModManager";
	public static final String FILE = CLASS + ".class";

	private static final String TARGET_METHOD = "discoverCoreMods";
	private static final String OUR_METHOD_NAME = "loom$injectCoremodsFromClasspath";
	private static final String OUR_METHOD_DESCRIPTOR = "(Lnet/minecraft/launchwrapper/LaunchClassLoader;)V";

	public CoreModManagerTransformer(ClassVisitor classVisitor) {
		super(Opcodes.ASM9, classVisitor);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

		// We inject a call to our method, which will discover and load coremods from the classpath, at the very start of the
		// regular discovery method.
		if (name.equals(TARGET_METHOD)) {
			methodVisitor = new InjectCallAtHead(methodVisitor);
		}

		return methodVisitor;
	}

	@Override
	public void visitEnd() {
		// We add the following method, which will find all coremods on the classpath, and load them.
		//
		//		private static void loom$injectCoremodsFromClasspath(LaunchClassLoader classLoader) throws Exception {
		//			for (URL url : classLoader.getURLs()) {
		//				if (!url.getProtocol().startsWith("file")) continue;
		//				File coreMod = new File(url.toURI().getPath());
		//				if (!coreMod.exists()) continue;
		//				Manifest manifest = null;
		//				if (coreMod.isDirectory()) {
		//					File manifestMF = new File(coreMod, "META-INF/MANIFEST.MF");
		//					if (manifestMF.exists()) {
		//						try (FileInputStream stream = new FileInputStream(manifestMF)) {
		//							manifest = new Manifest(stream);
		//						}
		//					}
		//				} else if (coreMod.getName().endsWith("jar")) {
		//					try (JarFile jar = new JarFile(coreMod)) {
		//						manifest = jar.getManifest();
		//						if (manifest != null) {
		//							String ats = manifest.getMainAttributes().getValue("FMLAT");
		//							if (ats != null && !ats.isEmpty()) {
		//								ModAccessTransformer.addJar(jar, ats);
		//							}
		//						}
		//					}
		//				}
		//				if (manifest != null) {
		//					String tweakClass = manifest.getMainAttributes().getValue("TweakClass");
		//					if (tweakClass != null) {
		//						Integer sortOrder = Ints.tryParse(Strings.nullToEmpty(manifest.getMainAttributes().getValue("TweakOrder")));
		//						sortOrder = (sortOrder == null ? Integer.valueOf(0) : sortOrder);
		//						handleCascadingTweak(coreMod, null, tweakClass, classLoader, sortOrder);
		//						continue;
		//					}
		//					String coreModClass = manifest.getMainAttributes().getValue("FMLCorePlugin");
		//					if (coreModClass != null) loadCoreMod(classLoader, coreModClass, coreMod);
		//				}
		//			}
		//		}
		//
		// Converted to ASM via the "ASM Bytecode Viewer" IntelliJ plugin:
		{
			MethodVisitor methodVisitor = super.visitMethod(ACC_PRIVATE | ACC_STATIC, OUR_METHOD_NAME, OUR_METHOD_DESCRIPTOR, null, null);
			methodVisitor.visitCode();
			Label label0 = new Label();
			Label label1 = new Label();
			Label label2 = new Label();
			methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
			Label label3 = new Label();
			Label label4 = new Label();
			methodVisitor.visitTryCatchBlock(label3, label4, label4, null);
			Label label5 = new Label();
			Label label6 = new Label();
			Label label7 = new Label();
			methodVisitor.visitTryCatchBlock(label5, label6, label7, null);
			Label label8 = new Label();
			Label label9 = new Label();
			methodVisitor.visitTryCatchBlock(label8, label9, label9, null);
			Label label10 = new Label();
			methodVisitor.visitLabel(label10);
			methodVisitor.visitLineNumber(701, label10);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "net/minecraft/launchwrapper/LaunchClassLoader", "getURLs", "()[Ljava/net/URL;", false);
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ASTORE, 4);
			methodVisitor.visitInsn(ARRAYLENGTH);
			methodVisitor.visitVarInsn(ISTORE, 3);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitVarInsn(ISTORE, 2);
			Label label11 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label11);
			Label label12 = new Label();
			methodVisitor.visitLabel(label12);
			methodVisitor.visitFrame(Opcodes.F_FULL, 5, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", Opcodes.TOP, Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/net/URL;"}, 0, new Object[] {});
			methodVisitor.visitVarInsn(ALOAD, 4);
			methodVisitor.visitVarInsn(ILOAD, 2);
			methodVisitor.visitInsn(AALOAD);
			methodVisitor.visitVarInsn(ASTORE, 1);
			Label label13 = new Label();
			methodVisitor.visitLabel(label13);
			methodVisitor.visitLineNumber(702, label13);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "getProtocol", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("file");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false);
			Label label14 = new Label();
			methodVisitor.visitJumpInsn(IFNE, label14);
			Label label15 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label15);
			methodVisitor.visitLabel(label14);
			methodVisitor.visitLineNumber(703, label14);
			methodVisitor.visitFrame(Opcodes.F_FULL, 5, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/net/URL", Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/net/URL;"}, 0, new Object[] {});
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 1);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "toURI", "()Ljava/net/URI;", false);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/net/URI", "getPath", "()Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 5);
			Label label16 = new Label();
			methodVisitor.visitLabel(label16);
			methodVisitor.visitLineNumber(704, label16);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false);
			Label label17 = new Label();
			methodVisitor.visitJumpInsn(IFNE, label17);
			methodVisitor.visitJumpInsn(GOTO, label15);
			methodVisitor.visitLabel(label17);
			methodVisitor.visitLineNumber(705, label17);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/io/File"}, 0, null);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 6);
			Label label18 = new Label();
			methodVisitor.visitLabel(label18);
			methodVisitor.visitLineNumber(706, label18);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "isDirectory", "()Z", false);
			Label label19 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label19);
			Label label20 = new Label();
			methodVisitor.visitLabel(label20);
			methodVisitor.visitLineNumber(707, label20);
			methodVisitor.visitTypeInsn(NEW, "java/io/File");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitLdcInsn("META-INF/MANIFEST.MF");
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/io/File;Ljava/lang/String;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 7);
			Label label21 = new Label();
			methodVisitor.visitLabel(label21);
			methodVisitor.visitLineNumber(708, label21);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false);
			Label label22 = new Label();
			methodVisitor.visitJumpInsn(IFEQ, label22);
			Label label23 = new Label();
			methodVisitor.visitLabel(label23);
			methodVisitor.visitLineNumber(709, label23);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitLabel(label3);
			methodVisitor.visitTypeInsn(NEW, "java/io/FileInputStream");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/FileInputStream", "<init>", "(Ljava/io/File;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 10);
			methodVisitor.visitLabel(label0);
			methodVisitor.visitLineNumber(710, label0);
			methodVisitor.visitTypeInsn(NEW, "java/util/jar/Manifest");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/jar/Manifest", "<init>", "(Ljava/io/InputStream;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 6);
			methodVisitor.visitLabel(label1);
			methodVisitor.visitLineNumber(711, label1);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitJumpInsn(IFNULL, label22);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
			methodVisitor.visitJumpInsn(GOTO, label22);
			methodVisitor.visitLabel(label2);
			methodVisitor.visitFrame(Opcodes.F_FULL, 11, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", "java/net/URL", Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/net/URL;", "java/io/File", "java/util/jar/Manifest", "java/io/File", "java/lang/Throwable", "java/lang/Throwable", "java/io/FileInputStream"}, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitVarInsn(ALOAD, 10);
			Label label24 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label24);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/FileInputStream", "close", "()V", false);
			methodVisitor.visitLabel(label24);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label4);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitVarInsn(ALOAD, 8);
			Label label25 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label25);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label26 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label26);
			methodVisitor.visitLabel(label25);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitJumpInsn(IF_ACMPEQ, label26);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitLabel(label26);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label19);
			methodVisitor.visitLineNumber(713, label19);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 3, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false);
			methodVisitor.visitLdcInsn("jar");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false);
			methodVisitor.visitJumpInsn(IFEQ, label22);
			Label label27 = new Label();
			methodVisitor.visitLabel(label27);
			methodVisitor.visitLineNumber(714, label27);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 7);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitLabel(label8);
			methodVisitor.visitTypeInsn(NEW, "java/util/jar/JarFile");
			methodVisitor.visitInsn(DUP);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/util/jar/JarFile", "<init>", "(Ljava/io/File;)V", false);
			methodVisitor.visitVarInsn(ASTORE, 9);
			methodVisitor.visitLabel(label5);
			methodVisitor.visitLineNumber(715, label5);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "getManifest", "()Ljava/util/jar/Manifest;", false);
			methodVisitor.visitVarInsn(ASTORE, 6);
			Label label28 = new Label();
			methodVisitor.visitLabel(label28);
			methodVisitor.visitLineNumber(716, label28);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitJumpInsn(IFNULL, label6);
			Label label29 = new Label();
			methodVisitor.visitLabel(label29);
			methodVisitor.visitLineNumber(717, label29);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("FMLAT");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 10);
			Label label30 = new Label();
			methodVisitor.visitLabel(label30);
			methodVisitor.visitLineNumber(718, label30);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitJumpInsn(IFNULL, label6);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
			methodVisitor.visitJumpInsn(IFNE, label6);
			Label label31 = new Label();
			methodVisitor.visitLabel(label31);
			methodVisitor.visitLineNumber(719, label31);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitVarInsn(ALOAD, 10);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/common/asm/transformers/ModAccessTransformer", "addJar", "(Ljava/util/jar/JarFile;Ljava/lang/String;)V", false);
			methodVisitor.visitLabel(label6);
			methodVisitor.visitLineNumber(722, label6);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 3, new Object[] {"java/lang/Throwable", "java/lang/Throwable", "java/util/jar/JarFile"}, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitJumpInsn(IFNULL, label22);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitJumpInsn(GOTO, label22);
			methodVisitor.visitLabel(label7);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 7);
			methodVisitor.visitVarInsn(ALOAD, 9);
			Label label32 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label32);
			methodVisitor.visitVarInsn(ALOAD, 9);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/JarFile", "close", "()V", false);
			methodVisitor.visitLabel(label32);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label9);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
			methodVisitor.visitVarInsn(ASTORE, 8);
			methodVisitor.visitVarInsn(ALOAD, 7);
			Label label33 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label33);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitVarInsn(ASTORE, 7);
			Label label34 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label34);
			methodVisitor.visitLabel(label33);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitJumpInsn(IF_ACMPEQ, label34);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V", false);
			methodVisitor.visitLabel(label34);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitInsn(ATHROW);
			methodVisitor.visitLabel(label22);
			methodVisitor.visitLineNumber(724, label22);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitJumpInsn(IFNULL, label15);
			Label label35 = new Label();
			methodVisitor.visitLabel(label35);
			methodVisitor.visitLineNumber(725, label35);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("TweakClass");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 7);
			Label label36 = new Label();
			methodVisitor.visitLabel(label36);
			methodVisitor.visitLineNumber(726, label36);
			methodVisitor.visitVarInsn(ALOAD, 7);
			Label label37 = new Label();
			methodVisitor.visitJumpInsn(IFNULL, label37);
			Label label38 = new Label();
			methodVisitor.visitLabel(label38);
			methodVisitor.visitLineNumber(727, label38);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("TweakOrder");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "com/google/common/base/Strings", "nullToEmpty", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "com/google/common/primitives/Ints", "tryParse", "(Ljava/lang/String;)Ljava/lang/Integer;", false);
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label39 = new Label();
			methodVisitor.visitLabel(label39);
			methodVisitor.visitLineNumber(728, label39);
			methodVisitor.visitVarInsn(ALOAD, 8);
			Label label40 = new Label();
			methodVisitor.visitJumpInsn(IFNONNULL, label40);
			methodVisitor.visitInsn(ICONST_0);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
			Label label41 = new Label();
			methodVisitor.visitJumpInsn(GOTO, label41);
			methodVisitor.visitLabel(label40);
			methodVisitor.visitFrame(Opcodes.F_APPEND, 2, new Object[] {"java/lang/String", "java/lang/Integer"}, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitLabel(label41);
			methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Integer"});
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label42 = new Label();
			methodVisitor.visitLabel(label42);
			methodVisitor.visitLineNumber(729, label42);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitInsn(ACONST_NULL);
			methodVisitor.visitVarInsn(ALOAD, 7);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/CoreModManager", "handleCascadingTweak", "(Ljava/io/File;Ljava/util/jar/JarFile;Ljava/lang/String;Lnet/minecraft/launchwrapper/LaunchClassLoader;Ljava/lang/Integer;)V", false);
			Label label43 = new Label();
			methodVisitor.visitLabel(label43);
			methodVisitor.visitLineNumber(730, label43);
			methodVisitor.visitJumpInsn(GOTO, label15);
			methodVisitor.visitLabel(label37);
			methodVisitor.visitLineNumber(732, label37);
			methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
			methodVisitor.visitVarInsn(ALOAD, 6);
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Manifest", "getMainAttributes", "()Ljava/util/jar/Attributes;", false);
			methodVisitor.visitLdcInsn("FMLCorePlugin");
			methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/jar/Attributes", "getValue", "(Ljava/lang/String;)Ljava/lang/String;", false);
			methodVisitor.visitVarInsn(ASTORE, 8);
			Label label44 = new Label();
			methodVisitor.visitLabel(label44);
			methodVisitor.visitLineNumber(733, label44);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitJumpInsn(IFNULL, label15);
			methodVisitor.visitVarInsn(ALOAD, 0);
			methodVisitor.visitVarInsn(ALOAD, 8);
			methodVisitor.visitVarInsn(ALOAD, 5);
			methodVisitor.visitMethodInsn(INVOKESTATIC, "net/minecraftforge/fml/relauncher/CoreModManager", "loadCoreMod", "(Lnet/minecraft/launchwrapper/LaunchClassLoader;Ljava/lang/String;Ljava/io/File;)Lnet/minecraftforge/fml/relauncher/CoreModManager$FMLPluginWrapper;", false);
			methodVisitor.visitInsn(POP);
			methodVisitor.visitLabel(label15);
			methodVisitor.visitLineNumber(701, label15);
			methodVisitor.visitFrame(Opcodes.F_FULL, 5, new Object[] {"net/minecraft/launchwrapper/LaunchClassLoader", Opcodes.TOP, Opcodes.INTEGER, Opcodes.INTEGER, "[Ljava/net/URL;"}, 0, new Object[] {});
			methodVisitor.visitIincInsn(2, 1);
			methodVisitor.visitLabel(label11);
			methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			methodVisitor.visitVarInsn(ILOAD, 2);
			methodVisitor.visitVarInsn(ILOAD, 3);
			methodVisitor.visitJumpInsn(IF_ICMPLT, label12);
			Label label45 = new Label();
			methodVisitor.visitLabel(label45);
			methodVisitor.visitLineNumber(736, label45);
			methodVisitor.visitInsn(RETURN);
			Label label46 = new Label();
			methodVisitor.visitLabel(label46);
			methodVisitor.visitLocalVariable("classLoader", "Lnet/minecraft/launchwrapper/LaunchClassLoader;", null, label10, label46, 0);
			methodVisitor.visitLocalVariable("url", "Ljava/net/URL;", null, label13, label15, 1);
			methodVisitor.visitLocalVariable("coreMod", "Ljava/io/File;", null, label16, label15, 5);
			methodVisitor.visitLocalVariable("manifest", "Ljava/util/jar/Manifest;", null, label18, label15, 6);
			methodVisitor.visitLocalVariable("manifestMF", "Ljava/io/File;", null, label21, label19, 7);
			methodVisitor.visitLocalVariable("stream", "Ljava/io/FileInputStream;", null, label0, label24, 10);
			methodVisitor.visitLocalVariable("jar", "Ljava/util/jar/JarFile;", null, label5, label32, 9);
			methodVisitor.visitLocalVariable("ats", "Ljava/lang/String;", null, label30, label6, 10);
			methodVisitor.visitLocalVariable("tweakClass", "Ljava/lang/String;", null, label36, label15, 7);
			methodVisitor.visitLocalVariable("sortOrder", "Ljava/lang/Integer;", null, label39, label37, 8);
			methodVisitor.visitLocalVariable("coreModClass", "Ljava/lang/String;", null, label44, label15, 8);
			methodVisitor.visitMaxs(5, 11);
			methodVisitor.visitEnd();
		}

		super.visitEnd();
	}

	private static class InjectCallAtHead extends MethodVisitor {
		private InjectCallAtHead(MethodVisitor methodVisitor) {
			super(Opcodes.ASM9, methodVisitor);
		}

		@Override
		public void visitCode() {
			super.visitCode();

			super.visitVarInsn(Opcodes.ALOAD, 1);
			super.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS, OUR_METHOD_NAME, OUR_METHOD_DESCRIPTOR, false);
		}
	}
}
