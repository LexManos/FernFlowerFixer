package net.minecraftforge.lex.fffixer;

import org.objectweb.asm.tree.ClassNode;

public interface IClassProcessor
{
    void process(ClassNode node);
}