package ru.urfu.lab2;
import com.vp.plugin.model.*;
import com.vp.plugin.diagram.*;
public class DupProbe {
    static IModelElement[] ch(IModelElement e) { IModelElement[] a = e.toChildArray(); return a == null ? new IModelElement[0] : a; }
    static IDiagramUIModel[] sd(IModelElement e) { IDiagramUIModel[] a = e.toSubDiagramArray(); return a == null ? new IDiagramUIModel[0] : a; }
    static void dump(IModelElement e, int depth, StringBuilder sb) {
        if (depth > 3) return;
        sb.append("  ".repeat(depth)).append(e.getModelType()).append(" '").append(e.getName()).append("'");
        for (IDiagramUIModel x : sd(e)) sb.append(" [").append(x.getType()).append(": ").append(x.getName()).append(" elems=").append(x.toDiagramElementArray().length).append("]");
        sb.append('\n');
        for (IModelElement c : ch(e)) if (!"Attribute".equals(c.getModelType()) && !"Operation".equals(c.getModelType())) dump(c, depth + 1, sb);
    }
    public static Object run(B b) throws Exception {
        IModelElement am = b.find("Package", "XMLRPCInterchange");
        int diags = b.project.toDiagramArray().length;
        IModelElement d = am.duplicate();
        StringBuilder sb = new StringBuilder("diagrams " + diags + " -> " + b.project.toDiagramArray().length + "\n");
        sb.append("dup parent=").append(d.getParent() == null ? null : d.getParent().getName()).append('\n');
        dump(d, 0, sb);
        return sb.toString();
    }
}
