package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
public class FlowCap {
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (IDiagramUIModel d : b.project.toDiagramArray()) {
            if (!"Зарегистрироваться на курсы".equals(d.getName())) continue;
            for (IDiagramElement e : d.toDiagramElementArray()) {
                if (!(e instanceof IConnectorUIModel) || e.getMetaModelElement() == null) continue;
                Object cap = e.getClass().getMethod("getCaptionUIModel").invoke(e);
                Class<?> k = cap.getClass();
                sb.append(e.getMetaModelElement().getName()).append(" conn=").append(e.getX() + "," + e.getY())
                  .append(" cap=").append(k.getMethod("getX").invoke(cap) + "," + k.getMethod("getY").invoke(cap) + " " + k.getMethod("getWidth").invoke(cap) + "x" + k.getMethod("getHeight").invoke(cap) + " vis=" + k.getMethod("isVisible").invoke(cap))
                  .append(" pts=").append(java.util.Arrays.toString(((IConnectorUIModel) e).getPoints()).replace("java.awt.Point", "")).append('\n');
            }
        }
        return sb.toString();
    }
}
