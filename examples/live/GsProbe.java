package ru.urfu.lab2;
import com.vp.plugin.model.*;
public class GsProbe {
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (IModelElement e : b.project.toAllLevelModelElementArray("GeneralizationSet")) {
            IGeneralizationSet s = (IGeneralizationSet) e;
            sb.append(s.getName()).append(" parent=").append(s.getParent() == null ? null : s.getParent().getName()).append(" gens=").append(s.toGeneralizationArray() == null ? 0 : s.toGeneralizationArray().length).append('\n');
        }
        for (IModelElement e : b.project.toAllLevelModelElementArray("Generalization")) {
            IGeneralization g = (IGeneralization) e;
            if (g.getTo() != null && g.getTo().getName().endsWith("Classification"))
                sb.append(g.getFrom().getName()).append("->").append(g.getTo().getName()).append(" set=").append(g.getGeneralizationSet() == null ? null : g.getGeneralizationSet().getName()).append('\n');
        }
        return sb.toString();
    }
}
