package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
public class Cap {
    public static Object run(B b) throws Exception {
        b.step("Login Basic Flow", () -> Stage2SeqAction.loginBasicFlow(b));
        IDiagramUIModel d = null;
        for (IDiagramUIModel x : b.project.toDiagramArray()) if ("Login Basic Flow".equals(x.getName())) d = x;
        StringBuilder sb = new StringBuilder();
        for (IDiagramElement e : d.toDiagramElementArray()) {
            if (!(e instanceof IConnectorUIModel)) continue;
            Object cap = e.getClass().getMethod("getCaptionUIModel").invoke(e);
            Class<?> c = cap.getClass();
            int x = (Integer) c.getMethod("getX").invoke(cap), y = (Integer) c.getMethod("getY").invoke(cap);
            sb.append(e.getMetaModelElement().getName()).append(" before ").append(x + "," + y);
            c.getMethod("setY", int.class).invoke(cap, 0);   // triggers VP's lazy layout
            java.awt.Point[] p = ((IConnectorUIModel) e).getPoints();
            String t = e.getMetaModelElement().getName();
            int w = Seq.FM.stringWidth(t) + 6;
            c.getMethod("setBounds", int.class, int.class, int.class, int.class).invoke(cap, (p[0].x + p[p.length-1].x) / 2 - w / 2, p[0].y - 15, w, 13);
            sb.append(" after ").append(c.getMethod("getX").invoke(cap) + "," + c.getMethod("getY").invoke(cap)).append('\n');
        }
        return sb.toString();
    }
}
