package net.minecraftforge.lex.fffixer;

import static org.objectweb.asm.Opcodes.*;

import java.util.Iterator;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Fixes decompiler differences between JVM versions caused by HashSet's sorting order changing between JVM implementations.
 * Simple solution is to hijack the Iterator to make it use a properly sorted one.
 * Thanks to fry for finding this issue with class names, which then led me to look for var names.
 * 
 * Code Injected in d:
 *   var = net.minecraftfroge.lex.fffixer.Util.sortComparable(var);
 * 
 * Code Injected in de (IntPair):
 *   implements Comparable<de>
 *   public int compareTo(de o)
 *   {
 *       if (this.a != o.a) return this.a - o.a;
 *       return this.b - o.b
 *   }
 *   public int compareTo(Object o)
 *   {
 *       return compareTo((de)o);
 *   }
 *
 */
public class VariableNumberFixer implements IClassProcessor
{
    private FFFixerImpl inst;
    public VariableNumberFixer(FFFixerImpl inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ClassNode node)
    {
        if (node.name.equals("d" )) fix_d (node);
        if (node.name.equals("de")) fixIntPair(node);
    }

    private void fix_d(ClassNode node)
    {
        MethodNode mtd = FFFixerImpl.getMethod(node, "b", "(Lcu;Lq;)V");

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
                    FFFixerImpl.log.info("Injecting Var Order Fix");

                    VarInsnNode var = (VarInsnNode)insn;
                    InsnList toAdd = new InsnList();
                    toAdd.add(new VarInsnNode (ALOAD, var.var)); // var15 = fixInnerOrder(var15)
                    toAdd.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Util.class), "sortComparable", "(Ljava/util/Iterator;)Ljava/util/Iterator;", false));
                    toAdd.add(new VarInsnNode (ASTORE, var.var));

                    mtd.instructions.insert(insn, toAdd); // Inject static call
                    
                    inst.setWorkDone();
                    return;
                }                    
            }
        }
    }

    /**
     * Make IntPair (de) extend Comparable, was doing this via string manipulation before because I was tired, 
     * but converted to ASM method injection for speed by fry, thanks.
     * 
     * @param node The IntPair (de) class node
     */
    private void fixIntPair(ClassNode node)
    {
        FFFixerImpl.log.info("Making IntPair Comparable");
        node.signature = "Ljava/lang/Object;Ljava/lang/Comparable<Lde;>;";
        node.interfaces.add("java/lang/Comparable");

        MethodNode mn = new MethodNode(ACC_PUBLIC, "compareTo", "(Lde;)I", null, null);
        mn.visitCode();
        mn.visitVarInsn(ALOAD, 0);
        mn.visitFieldInsn(GETFIELD, "de", "a", "I");
        mn.visitVarInsn(ALOAD, 1);
        mn.visitFieldInsn(GETFIELD, "de", "a", "I");
        Label a_equals = new Label();
        mn.visitJumpInsn(IF_ICMPEQ, a_equals);           // if this.a == o.a goto a_euqals 
        mn.visitVarInsn(ALOAD, 0);
        mn.visitFieldInsn(GETFIELD, "de", "a", "I");
        mn.visitVarInsn(ALOAD, 1);
        mn.visitFieldInsn(GETFIELD, "de", "a", "I");
        mn.visitInsn(ISUB);
        mn.visitInsn(IRETURN);                           // return this.a - o.a 
        mn.visitLabel(a_equals);                         // a_equals
        mn.visitVarInsn(ALOAD, 0);
        mn.visitFieldInsn(GETFIELD, "de", "b", "I");
        mn.visitVarInsn(ALOAD, 1);
        mn.visitFieldInsn(GETFIELD, "de", "b", "I");
        mn.visitInsn(ISUB);
        mn.visitInsn(IRETURN);                           // return this.b - o.b
        mn.visitEnd();
        node.methods.add(mn);

        mn = new MethodNode(ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC, "compareTo", "(Ljava/lang/Object;)I", null, null);
        mn.visitCode();
        mn.visitVarInsn(ALOAD, 0);
        mn.visitVarInsn(ALOAD, 1);
        mn.visitTypeInsn(CHECKCAST, "de");
        mn.visitMethodInsn(INVOKEVIRTUAL, "de", "compareTo", "(Lde;)I", false); // Synthetic bounce of (Object) -> (IntPair)
        mn.visitInsn(IRETURN);
        mn.visitEnd();
        node.methods.add(mn);

        inst.setWorkDone();
    }
}