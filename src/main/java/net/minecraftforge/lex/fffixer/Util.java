package net.minecraftforge.lex.fffixer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class Util
{
    public static Iterator<String> sortStrings(Iterator<String> itr)
    {
        List<String> list = new ArrayList<String>();
    
        while (itr.hasNext())
            list.add(itr.next());
    
        Collections.sort(list);
        return list.iterator();
    }

    public static void putIntercept(HashMap<Integer, Integer> map, Integer key, Integer value)
    {
        System.out.println("====================== " + key + " " + value);
        map.put(key, value);
    }
    public static Iterator<Object> sortLvs(Iterator<Object> itr)
    {
        List<Object> list = new ArrayList<Object>();
    
        while (itr.hasNext())
            list.add(itr.next());
    
        Collections.sort(list, new Comparator<Object>()
        {
            @Override
            public int compare(Object o1, Object o2)
            {
                int[] a = getInts(o1.toString());
                int[] b = getInts(o2.toString());
                if (a[0] != b[0]) return a[0] - b[0];
                return a[1] - b[1];
                
            }
            private int[] getInts(String val)
            {
                val = val.substring(1, val.length() - 1);
                String[] tmp = val.split(",");
                return new int[]
                {
                    Integer.parseInt(tmp[0]),
                    Integer.parseInt(tmp[1])
                };
            }
        });
        return list.iterator();
    }
}
