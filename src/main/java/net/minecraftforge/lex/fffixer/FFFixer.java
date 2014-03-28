package net.minecraftforge.lex.fffixer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class FFFixer
{
    private final static Logger log = Logger.getLogger("FFFixer");
    public static final String VERSION = "FernFlowerFixer v1.0 by LexManos (Basic structure from MCInjector)";
    
    public static void main(String[] args) throws Exception
    {
        
        OptionParser parser = new OptionParser();
        parser.accepts("help").forHelp();
        parser.accepts("version").forHelp();
        parser.accepts("jarIn").withRequiredArg().required();
        parser.accepts("jarOut").withRequiredArg();
        parser.accepts("log").withRequiredArg();

        try
        {
            OptionSet options = parser.parse(args);
            if (options.has("help"))
            {
                System.out.println(VERSION);
                parser.printHelpOn(System.out);
                return;
            }
            else if (options.has("version"))
            {
                System.out.println(VERSION);
                return;
            }

            String jarIn   = (String)options.valueOf("jarIn");
            String jarOut  = (String)options.valueOf("jarOut");
            String log     = (String)options.valueOf("log");
    
            FFFixer.log.setUseParentHandlers(false);
            FFFixer.log.setLevel(Level.ALL);
    
            if (log != null)
            {
                FileHandler filehandler = new FileHandler(log);
                filehandler.setFormatter(new Formatter()
                {
                    @Override
                    public synchronized String format(LogRecord record)
                    {
                        StringBuffer sb = new StringBuffer();
                        String message = this.formatMessage(record);
                        sb.append(record.getLevel().getName());
                        sb.append(": ");
                        sb.append(message);
                        sb.append("\n");
                        if (record.getThrown() != null)
                        {
                            try
                            {
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                record.getThrown().printStackTrace(pw);
                                pw.close();
                                sb.append(sw.toString());
                            }
                            catch (Exception ex){}
                        }
                        return sb.toString();
                    }

                });
                FFFixer.log.addHandler(filehandler);
            }

            FFFixer.log.addHandler(new Handler()
            {
                @Override
                public void publish(LogRecord record)
                {
                    if (record.getLevel().intValue() < Level.INFO.intValue()) return;
                    System.out.println(String.format(record.getMessage(), record.getParameters()));
                }
                @Override public void flush() {}
                @Override public void close() throws SecurityException {}
            });
    
            log(FFFixer.VERSION);
            log("Input:          " + jarIn);
            log("Output:         " + jarOut);
            log("Log:            " + log);
    
            try
            {
                FFFixerImpl.process(jarIn, jarOut, log);
            }
            catch (Exception e)
            {
                System.err.println("ERROR: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
        catch (OptionException e)
        {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private static void log(String line)
    {
        log.info(line);
    }
}
