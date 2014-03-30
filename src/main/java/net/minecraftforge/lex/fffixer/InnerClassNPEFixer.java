package net.minecraftforge.lex.fffixer;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.IFNULL;

import java.util.Iterator;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;


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
public class InnerClassNPEFixer implements IClassProcessor
{
    private FFFixerImpl inst;
    public InnerClassNPEFixer(FFFixerImpl inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ClassNode node)
    {
        if (!node.name.equals("cA")) return;

        MethodNode mtd = FFFixerImpl.getMethod(node, "a", "(LaK;LcX;LaG;)LaJ;");

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
                    inst.setWorkDone();
                    return;
                }                    
            }
        }
    }
}

