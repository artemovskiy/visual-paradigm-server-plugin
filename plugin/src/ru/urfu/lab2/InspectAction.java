package ru.urfu.lab2;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationship;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Dumps the active diagram's shapes/connectors with all simple getters, plus relationships of their models. */
public class InspectAction implements VPActionController {
    public void update(VPAction action) {}

    public void performAction(VPAction action) {
        B b = new B();
        IDiagramUIModel d = b.dm.getActiveDiagram();
        if (d == null) { b.app.getViewManager().showMessage("Open a diagram first", "Lab2"); return; }
        b.log("DIAGRAM " + d.getType() + " | " + d.getName());
        Set<String> seen = new HashSet<String>();
        for (IDiagramElement e : d.toDiagramElementArray()) dump(b, e, 0, seen);
        b.finish("Inspect " + d.getName());
    }

    private void dump(B b, IDiagramElement e, int depth, Set<String> seen) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < depth; i++) s.append("    ");
        String pad = s.toString();
        IModelElement m = e.getMetaModelElement();
        s.append("* ").append(ifaces(e.getClass()));
        if (m != null) {
            s.append(" | model=").append(m.getModelType()).append(" '").append(m.getName()).append("' id=").append(m.getId());
            if (m instanceof IRelationship) {
                IRelationship r = (IRelationship) m;
                s.append(" from='").append(r.getFrom() == null ? null : r.getFrom().getName())
                 .append("' to='").append(r.getTo() == null ? null : r.getTo().getName()).append("'");
            }
        }
        b.log(s.toString());
        b.log(pad + "    " + getters(e));
        if (!seen.add(System.identityHashCode(e) + "")) return;
        try {
            for (Object c : (Object[]) e.getClass().getMethod("toChildArray").invoke(e))
                dump(b, (IDiagramElement) c, depth + 1, seen);
        } catch (Throwable ignored) {}
    }

    private static String ifaces(Class<?> c) {
        Set<String> out = new java.util.LinkedHashSet<String>();
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            for (Class<?> i : k.getInterfaces()) if (i.getName().startsWith("com.vp.plugin")) out.add(i.getSimpleName());
        return out.toString();
    }

    private static String getters(Object o) {
        StringBuilder sb = new StringBuilder();
        Method[] ms = o.getClass().getMethods();
        Arrays.sort(ms, (a, z) -> a.getName().compareTo(z.getName()));
        for (Method mt : ms) {
            String n = mt.getName();
            if (mt.getParameterTypes().length != 0 || !(n.startsWith("get") || n.startsWith("is"))) continue;
            Class<?> r = mt.getReturnType();
            if (!(r.isPrimitive() || r == String.class || r == String[].class) || r == void.class) continue;
            try {
                Object v = mt.invoke(o);
                if (v == null) continue;
                if (v instanceof String[]) v = Arrays.toString((String[]) v);
                if ("".equals(v) || Boolean.FALSE.equals(v)) continue;
                sb.append(n).append('=').append(v).append("; ");
            } catch (Throwable ignored) {}
        }
        return sb.toString();
    }
}
