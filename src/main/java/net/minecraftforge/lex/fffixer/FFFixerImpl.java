package net.minecraftforge.lex.fffixer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.*;

public class FFFixerImpl
{
    private final static Logger log = Logger.getLogger("FFFixer");
    private final List<IClassProcessor> processors = new ArrayList<IClassProcessor>();

    public FFFixerImpl()
    {
        processors.add(new InnerClassNPEFixer());
        processors.add(new InnerClassOrderFixer(this));
    }

    public static void process(String inFile, String outFile, String logFile) throws IOException
    {
        FFFixerImpl inst = new FFFixerImpl();

        inst.processJar(inFile, outFile);

        log.fine("Processed " + inFile);
    }

    public void processJar(String inFile, String outFile) throws IOException
    {
        ZipInputStream inJar = null;
        ZipOutputStream outJar = null;

        try
        {
            try
            {
                inJar = new ZipInputStream(new BufferedInputStream(new FileInputStream(inFile)));
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }

            try
            {
                OutputStream out = (outFile == null ? new ByteArrayOutputStream() : new FileOutputStream(outFile));
                outJar = new ZipOutputStream(new BufferedOutputStream(out));
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }

            while (true)
            {
                ZipEntry entry = inJar.getNextEntry();

                if (entry == null)
                {
                    break;
                }

                if (entry.isDirectory())
                {
                    outJar.putNextEntry(entry);
                    continue;
                }

                byte[] data = new byte[4096];
                ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();

                int len;
                do
                {
                    len = inJar.read(data);
                    if (len > 0)
                    {
                        entryBuffer.write(data, 0, len);
                    }
                } while (len != -1);

                byte[] entryData = entryBuffer.toByteArray();

                String entryName = entry.getName();

                if (entryName.endsWith(".class"))
                {
                    FFFixerImpl.log.log(Level.FINE, "Processing " + entryName);

                    entryData = this.processClass(entryData, outFile == null);

                    FFFixerImpl.log.log(Level.FINE, "Processed " + entryBuffer.size() + " -> " + entryData.length);
                }
                else
                {
                    FFFixerImpl.log.log(Level.FINE, "Copying " + entryName);
                }

                ZipEntry newEntry = new ZipEntry(entryName);
                outJar.putNextEntry(newEntry);
                outJar.write(entryData);
            }
        }
        finally
        {
            if (outJar != null)
            {
                try
                {
                    outJar.close();
                }
                catch (IOException e)
                {
                    // ignore
                }
            }

            if (inJar != null)
            {
                try
                {
                    inJar.close();
                }
                catch (IOException e)
                {
                    // ignore
                }
            }
        }
    }

    private boolean workDone = false;
    public void setWorkDone()
    {
        workDone = true;
    }

    public byte[] processClass(byte[] cls, boolean readOnly)
    {
        workDone = false;

        ClassReader cr = new ClassReader(cls);
        ClassNode cn = new ClassNode();
        
        ClassVisitor ca = cn;

        //ca = new ApplyMapClassAdapter(cn, this);
        
        cr.accept(ca, 0);
        
        for (IClassProcessor proc : processors)
            proc.process(cn);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(writer);

        return workDone ? writer.toByteArray() : cls;
    }

    private interface IClassProcessor
    {
        void process(ClassNode node);
    }

    /**
     * Fix an issue in the 'cA' class when decompiling inner classes that causes 
     * it to error with a NPE by adding the following code to the a(aK, cX, aG) function
     * directly after the first line:
     * 
     *     if ((this == null) || (this.h == null)) {
     *           return null;
     *     }
     * 
     * @author LexManos, From old research done on StackOverflow and the MCP team ages ago.
     *
     */
    private class InnerClassNPEFixer implements IClassProcessor
    {
        @Override
        public void process(ClassNode node)
        {
            if (!node.name.equals("cA")) return;
            MethodNode mtd = null;
            for (MethodNode m : node.methods)
            {
                if (m.name.equals("a") && m.desc.equals("(LaK;LcX;LaG;)LaJ;"))
                {
                    mtd = m;
                    break;
                }
            }

            InsnList toAdd = new InsnList();
            LabelNode ret = new LabelNode();
            LabelNode end = new LabelNode();
            toAdd.add(new VarInsnNode (ALOAD, 0));
            toAdd.add(new JumpInsnNode(IFNULL, ret)); // if (var0 == null)
            toAdd.add(new VarInsnNode (ALOAD, 0));
            toAdd.add(new FieldInsnNode(GETFIELD, "aK", "h", "Ljava/util/HashMap;"));
            toAdd.add(new JumpInsnNode(IFNULL, ret));//    || (var0.h == null)
            toAdd.add(new JumpInsnNode(GOTO, end));
            toAdd.add(ret);
            toAdd.add(new InsnNode(ACONST_NULL));
            toAdd.add(new InsnNode(ARETURN));        //        return null
            toAdd.add(end);

            Iterator<AbstractInsnNode> itr = mtd.instructions.iterator();
            while(itr.hasNext())
            {
                AbstractInsnNode insn = itr.next();
                if (insn instanceof VarInsnNode)
                {
                    VarInsnNode v = (VarInsnNode)insn;
                    if (v.getOpcode() == ASTORE && v.var == 0)
                    {
                        FFFixerImpl.log.info("Injecting InnerClass NPE Fix");
                        mtd.instructions.insert(insn, toAdd);
                        FFFixerImpl.this.setWorkDone();
                        return;
                    }                    
                }
            }
        }
    }

    /**
     * Fixes decompiler differences between JVM versions caused by HashSet's sorting order changing between JVM implementations.
     * Simple solution is to hijack the Iterator to make it use a properly sorted one.
     * Thanks to fry for finding this issue and pointing me in the right direction.
     * 
     * Code Injected:
     *   var15 = fixInnerOrder(var15);
     * 
     * New Method:
     * 
     * public static Iterator<String> fixInnerOrder(Iterator<String> itr)
     * {
     *     List<String> list = new ArrayList<String>();
     *      
     *     while (itr.hasNext())
     *         list.add(itr.next());
     *                
     *     Collections.sort(list);
     *     return list.iterator();
     * }
     * 
     * @author LexManos
     *
     */
    private static class InnerClassOrderFixer implements IClassProcessor
    {
        private FFFixerImpl inst;
        private InnerClassOrderFixer(FFFixerImpl inst)
        {
            this.inst = inst;
        }

        @Override
        public void process(ClassNode node)
        {
            if (!node.name.equals("cG")) return;
            MethodNode mtd = null;
            for (MethodNode m : node.methods)
            {
                if (m.name.equals("<init>") && m.desc.equals("(Li;)V"))
                {
                    mtd = m;
                    break;
                }
            }

            InsnList toAdd = new InsnList();
            toAdd.add(new VarInsnNode (ALOAD, 15)); // var15 = fixInnerOrder(var15)
            toAdd.add(new MethodInsnNode(INVOKESTATIC, "cG", "fixInnerOrder", "(Ljava/util/Iterator;)Ljava/util/Iterator;"));
            toAdd.add(new VarInsnNode (ASTORE, 15));

            Iterator<AbstractInsnNode> itr = mtd.instructions.iterator();
            while(itr.hasNext())
            {
                AbstractInsnNode insn = itr.next();
                if (insn instanceof MethodInsnNode)
                {
                    MethodInsnNode v = (MethodInsnNode)insn;
                    if (v.getOpcode() == INVOKEVIRTUAL && (v.owner + "/" + v.name + v.desc).equals("java/util/HashSet/iterator()Ljava/util/Iterator;"))
                    {
                        insn = itr.next(); //Pop off the next which is ASTORE 15
                        FFFixerImpl.log.info("Injecting InnerClass Order Fix");
                        mtd.instructions.insert(insn, toAdd); // Inject static call

                        /* Add this function in:
                           public static Iterator<String> fixInnerOrder(Iterator<String> itr)
                           {
                               List<String> list = new ArrayList<String>();
                           
                               while (itr.hasNext())
                                   list.add(itr.next());
                           
                               Collections.sort(list);
                               return list.iterator();
                           }
                         */
                        MethodNode fixer = new MethodNode(ACC_PRIVATE | ACC_STATIC, "fixInnerOrder", "(Ljava/util/Iterator;)Ljava/util/Iterator;", null, null);
                        LabelNode loop = new LabelNode();
                        LabelNode body = new LabelNode();
                        add(fixer.instructions,
                           new TypeInsnNode(NEW, "java/util/ArrayList"),
                           new InsnNode(DUP),
                           new MethodInsnNode(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V"),
                           new VarInsnNode(ASTORE, 1),
                           new JumpInsnNode(GOTO, loop),
                           body,
                           new VarInsnNode(ALOAD, 1),
                           new VarInsnNode(ALOAD, 0),
                           new MethodInsnNode(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;"),
                           new TypeInsnNode(CHECKCAST, "java/lang/String"),
                           new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z"),
                           new InsnNode(POP),
                           loop,
                           new VarInsnNode(ALOAD, 0),
                           new MethodInsnNode(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z"),
                           new JumpInsnNode(IFNE, body),
                           new VarInsnNode(ALOAD, 1),
                           new MethodInsnNode(INVOKESTATIC, "java/util/Collections", "sort", "(Ljava/util/List;)V"),
                           new VarInsnNode(ALOAD, 1),
                           new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;"),
                           new InsnNode(ARETURN)                           
                        );

                        node.methods.add(fixer);

                        inst.setWorkDone();
                        return;
                    }                    
                }
            }
        }

        private void add(InsnList list, AbstractInsnNode... insns)
        {
            for (AbstractInsnNode n : insns)
                list.add(n);
        }
    }
}
