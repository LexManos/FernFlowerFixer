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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.io.ByteStreams;

public class FFFixerImpl
{
    final static Logger log = Logger.getLogger("FFFixer");
    private final List<IClassProcessor> processors = new ArrayList<IClassProcessor>();

    public FFFixerImpl()
    {
        processors.add(new InnerClassNPEFixer(this));
        processors.add(new InnerClassOrderFixer(this));
        processors.add(new VariableNumberFixer(this));
        processors.add(new EnableStackTracesInLog(this));
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

            // Add Out Util class:
            String[] extras = {
                Util.class.getCanonicalName().replace('.', '/') + ".class"
            };
            for (String name : extras)
            {
                ZipEntry newEntry = new ZipEntry(name);
                outJar.putNextEntry(newEntry);
                outJar.write(ByteStreams.toByteArray(FFFixerImpl.class.getClassLoader().getResourceAsStream(name)));
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

        //ca = new LineInjectorAdaptor(ASM4, cn);
        
        cr.accept(ca, 0);
        
        for (IClassProcessor proc : processors)
            proc.process(cn);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(writer);

        return workDone ? writer.toByteArray() : cls;
    }

    public static MethodNode getMethod(ClassNode cls, String name, String desc)
    {
        for (MethodNode method : cls.methods)
        {
            if (method.name.equals(name) && method.desc.equals(desc))
                return method;
        }
        return null;
    }
}
