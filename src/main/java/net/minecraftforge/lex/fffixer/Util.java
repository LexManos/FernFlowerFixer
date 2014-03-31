package net.minecraftforge.lex.fffixer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class Util
{
    public static interface Indexed
    {
        public int getIndex();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Iterator sortIndexed(Iterator itr)
    {
        List list = new ArrayList();
        List<Indexed> def_dec = new ArrayList<Indexed>();
        int first = -1;

        while(itr.hasNext())
        {
            Object i = itr.next();
            //Split off any default variable declarations and sort them.
            if (i instanceof Indexed && ((Indexed)i).getIndex() >= 0)
            {
                if (first == -1) first = list.size();
                def_dec.add((Indexed)i);
            }
            else
            {
                list.add(i);
            }
        }

        if (def_dec.size() > 0)
        {
            Collections.sort(def_dec, new Comparator<Indexed>()
            {
                @Override
                public int compare(Indexed o1, Indexed o2)
                {
                    return o1.getIndex() - o2.getIndex();
                }
            });
            list.addAll(first, def_dec);
        }

        return list.iterator();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T extends Comparable> Iterator<T> sortComparable(Iterator<T> itr)
    {
        List<T> list = new ArrayList<T>();
    
        while (itr.hasNext())
            list.add(itr.next());
    
        Collections.sort(list);
        return list.iterator();
    }
}
