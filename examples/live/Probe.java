package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
import java.lang.reflect.*;
public class Probe {
    public static Object run(B b) throws Exception {
        IDiagramUIModel d = null;
        for (IDiagramUIModel x : b.project.toDiagramArray()) if ("Login Basic Flow".equals(x.getName())) d = x;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (IDiagramElement e : d.toDiagramElementArray()) {
            if (!(e instanceof IConnectorUIModel)) continue;
            Object cap = e.getClass().getMethod("getCaptionUIModel").invoke(e);
            if (false) { for (Method m : cap.getClass().getMethods()) if (m.getDeclaringClass() != Object.class) sb.append("  ").append(m).append('\n'); first = false; }
            Class<?> c = cap.getClass();
            sb.append(e.getMetaModelElement().getName()).append(" conn=").append(e.getX()+","+e.getY()+","+e.getWidth()+","+e.getHeight()).append(" pts=").append(java.util.Arrays.toString(((IConnectorUIModel) e).getPoints())).append(" cap=").append(c.getMethod("getX").invoke(cap)+","+c.getMethod("getY").invoke(cap)+","+c.getMethod("getWidth").invoke(cap)+","+c.getMethod("getHeight").invoke(cap)+" side="+c.getMethod("getSide").invoke(cap)+" vis="+c.getMethod("isVisible").invoke(cap)).append(" pt=").append(((IConnectorUIModel) e).getPaintThroughLabel()).append(" or=").append(((IConnectorUIModel) e).getConnectorLabelOrientation()).append('\n');
        }
        return sb.toString();
    }
}
