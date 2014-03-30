package net.minecraftforge.lex.fffixer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Util
{
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
