package io.dreamz.lunar.bootstrap;

import io.dreamz.lunar.bootstrap.cli.Command;
import io.dreamz.lunar.bootstrap.cli.Commander;
import io.dreamz.lunar.bootstrap.cli.Flag;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class Bootstrap implements Opcodes {

    private static final AtomicBoolean success = new AtomicBoolean(false);

    public static void main(final String[] args) throws InstantiationException, IllegalAccessException, IOException, InterruptedException {

        final Commander commander = new Commander(args);
        {
            commander.withDefault("patch", "BungeeCord.jar")
                    .withDefault("server", "lunar");
        }

        // parse the flags
        commander.parse();

        // execute the methods
        commander.execute(Bootstrap.class);


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (success.get() && commander.hasFlag("script")) {
                System.out.println("JAR file patched. Starting server...");
                try {
                    new ProcessBuilder().command(commander.getValue("script")).inheritIO().start().waitFor();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }));

    }

    @Command
    public void patch(@Flag("patch") File file, @Flag("server") String server) throws IOException {
        if (file.exists()) {
            System.out.println("Found server jar...patching...");
            JarFile bungeecordJar = new JarFile(file);

            byte[] newClassFile = null;
            Enumeration<JarEntry> entries = bungeecordJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.getName().equalsIgnoreCase("net/md_5/bungee/api/ServerPing.class")) {
                    try {
                        ClassReader reader = new ClassReader(bungeecordJar.getInputStream(jarEntry));
                        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

                        ClassNode node = new ClassNode();
                        {
                            reader.accept(node, 0);
                        }


                        for (int i = 0; i < node.fields.size(); i++) {
                            if (node.fields.get(i).name.equals("lcServer")) {
                                node.fields.remove(node.fields.get(i--));
                            }
                        }
                        node.fields.add(new FieldNode(ACC_PRIVATE, "lcServer", "Ljava/lang/String;", null, null));

                        for (MethodNode mn : node.methods) {

                            if (mn.name.equalsIgnoreCase("<init>")) {


                                InsnList insnList = new InsnList();
                                {
                                    // this.lcServer = "Server Name";
                                    insnList.add(new VarInsnNode(ALOAD, 0));
                                    insnList.add(new LdcInsnNode(server));
                                    insnList.add(new FieldInsnNode(PUTFIELD, "net/md_5/bungee/api/ServerPing", "lcServer", "Ljava/lang/String;"));
                                }

                                for (int i = 0; i < mn.instructions.size(); i++) {
                                    // insert at the very end
                                    if (mn.instructions.get(i).getOpcode() == INVOKESPECIAL) {
                                        if (mn.instructions.get(i) instanceof MethodInsnNode) {
                                            if (!((MethodInsnNode) mn.instructions.get(i)).desc.equalsIgnoreCase("()V")) {
                                                // likely an constructor similar to this(x, y, z);
                                                // we want to skip these.
                                                break;
                                            }
                                        }
                                    }

                                    if (mn.instructions.get(i).getOpcode() == PUTFIELD) {
                                        if (((FieldInsnNode) mn.instructions.get(i)).name.equalsIgnoreCase("lcServer")) {
                                            for (int j = 0; j < 3; j++) {
                                                mn.instructions.remove(mn.instructions.get(i--));
                                            }
                                        }
                                    }

                                    // Inject right before the RETURN statement
                                    // We don't need to worry about anything like IRETURN. This is a void function
                                    if (mn.instructions.get(i).getOpcode() == RETURN) {
                                        mn.instructions.insertBefore(mn.instructions.get(i), insnList);
                                        break;
                                    }
                                }
                            }
                        }
                        node.accept(classWriter);

                        newClassFile = classWriter.toByteArray();

                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }


            bungeecordJar.close();

            if (newClassFile != null) {
                try {

                    File parent = new File("net/md_5/bungee/api");
                    parent.mkdirs();

                    File f = new File(parent, "ServerPing.class");
                    f.deleteOnExit();

                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        fos.write(newClassFile);
                    }

                    Process process = new ProcessBuilder("jar", "uvf", file.getPath(), f.getPath()).inheritIO().start();
                    process.waitFor();

                    process.destroy();
                    success.set(true);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println("File not found. Is there a typo?");

            System.out.println(file.getPath());
        }
    }
}