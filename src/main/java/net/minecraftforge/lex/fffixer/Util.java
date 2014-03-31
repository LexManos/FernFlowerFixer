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

        while(itr.hasNext())
            list.add(itr.next());

        Collections.sort(list, new Comparator() {
            @Override
            public int compare(Object o1, Object o2)
            {
                if(o1 instanceof Indexed) {
                    if(o2 instanceof Indexed)
                        return ((Indexed)o1).getIndex() - ((Indexed)o2).getIndex();
                    else return -1;
                }
                else if(o2 instanceof Indexed) return 1;
                else return 0;
            }
        });
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
