package net.minecraftforge.lex.fffixer;

import static org.objectweb.asm.Opcodes.ICONST_1;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

// Just enables StackTraces in the Default logger.
public class EnableStackTracesInLog implements IClassProcessor
{
    private FFFixerImpl inst;
    public EnableStackTracesInLog(FFFixerImpl inst)
    {
        this.inst = inst;
    }

    @Override
    public void process(ClassNode node)
    {
        if (!node.name.equals("at")) return;
        MethodNode mtd = FFFixerImpl.getMethod(node, "getShowStacktrace", "()Z");
        mtd.instructions.set(mtd.instructions.getFirst(), new InsnNode(ICONST_1));
        inst.setWorkDone();
        FFFixerImpl.log.info("Enabeling printing stack traces in StreamLogger");
    }
}