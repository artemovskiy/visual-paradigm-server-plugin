package ru.urfu.lab2;
import com.vp.plugin.model.*;
public class Actors {
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder(b.project.getProjectFile() + "\n");
        for (String t : new String[]{"Actor", "UseCase", "System"})
            for (IModelElement e : b.project.toAllLevelModelElementArray(t))
                sb.append(t).append(' ').append(e.getName()).append(" parent=").append(e.getParent() == null ? null : e.getParent().getName()).append('\n');
        return sb.toString();
    }
}
