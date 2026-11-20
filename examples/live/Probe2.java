package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
import java.lang.reflect.*;
public class Probe2 {
    public static Object run(B b) throws Exception {
        IDiagramUIModel d = null;
        for (IDiagramUIModel x : b.project.toDiagramArray()) if ("Login Basic Flow".equals(x.getName())) d = x;
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (IDiagramElement e : d.toDiagramElementArray()) {
            String t = e.getShapeType();
            if (!(t.equals("CombinedFragment") || t.equals("InteractionOperand") || t.equals("InteractionLifeLine")) || !seen.add(t)) continue;
            sb.append("== ").append(t).append(' ').append(e.getClass().getName()).append('\n');
            for (Method m : e.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                if (m.getDeclaringClass().getName().startsWith("v.") && (n.contains("operand") || n.contains("separ") || n.contains("divid") || n.contains("destroy") || n.contains("stop") || n.contains("cross") || n.contains("end")))
                    sb.append("  ").append(m).append('\n');
            }
        }
        return sb.toString();
    }
}
