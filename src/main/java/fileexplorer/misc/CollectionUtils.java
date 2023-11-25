package fileexplorer.misc;

import java.util.List;

public class CollectionUtils {

    public static <E> List<E> concat(List<E> l, E o) {
        l.add(o);
        return l;
    }
}

