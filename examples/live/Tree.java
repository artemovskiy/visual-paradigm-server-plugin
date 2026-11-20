package ru.urfu.lab2;
import com.vp.plugin.model.*;
import com.vp.plugin.diagram.*;
public class Tree {
    static void walk(IModelElement e, int depth, int max, StringBuilder sb) {
        if (depth > max) return;
        if (e == null) return; String t = String.valueOf(e.getModelType());
        if (t.equals("Attribute") || t.equals("Operation") || t.equals("Parameter") || t.startsWith("Message") || t.equals("InteractionLifeLine") || t.equals("EnumerationLiteral")) return;
        sb.append("  ".repeat(depth)).append(t).append(" '").append(e.getName()).append("'");
        String st = ""; IStereotype[] sts = e.toStereotypeModelArray(); if (sts != null) for (IStereotype s : sts) if (s != null) st += "<<" + s.getName() + ">>";
        sb.append(st);
        IDiagramUIModel[] ds = e.toSubDiagramArray();
        if (ds != null && ds.length > 0) { sb.append("  diagrams:"); for (IDiagramUIModel d : ds) sb.append(" [").append(String.valueOf(d.getType()).replace("Diagram", "")).append(": ").append(d.getName()).append("]"); }
        sb.append('\n');
        IModelElement[] ch = e.toChildArray(); if (ch != null) for (IModelElement c : ch) walk(c, depth + 1, max, sb);
    }
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (IModelElement r : b.project.toModelElementArray()) walk(r, 0, 6, sb);
        java.nio.file.Files.write(java.nio.file.Paths.get(System.getProperty("user.home"), "Library/Caches/lab2-live/tree.txt"), sb.toString().getBytes("UTF-8"));
        return "lines " + sb.toString().split("\n").length;
    }
}
