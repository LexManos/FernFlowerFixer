package net.minecraftforge.lex.fffixer;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import java.util.Iterator;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.sun.xml.internal.ws.org.objectweb.asm.Type;


/**
 * Fixes decompiler differences between JVM versions caused by HashSet's sorting order changing between JVM implementations.
 * Simple solution is to hijack the Iterator to make it use a properly sorted one.
 * Thanks to fry for finding this issue with class names, which then led me to look for var names.
 * 
 * Code Injected:
 *   var = net.minecraftfroge.lex.fffixer.Util.sortLvs(var);
 * 
 * @author LexManos
 *
 */
public class InnerClassOrderFixer implements IClassProcessor
{
    private FFFixerImpl inst;
    public InnerClassOrderFixer(FFFixerImpl inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ClassNode node)
    {
        if (!node.name.equals("d")) return;

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
                    toAdd.add(new MethodInsnNode(INVOKESTATIC, Type.getInternalName(Util.class), "sortLvs", "(Ljava/util/Iterator;)Ljava/util/Iterator;", false));
                    toAdd.add(new VarInsnNode (ASTORE, var.var));

                    mtd.instructions.insert(insn, toAdd); // Inject static call
                    
                    inst.setWorkDone();
                    return;
                }                    
            }
        }
    }
}