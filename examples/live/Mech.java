package ru.urfu.lab2;
import com.vp.plugin.model.*;
public class Mech {
    static String cls(IClass c) {
        StringBuilder sb = new StringBuilder("  " + c.getName());
        IStereotype[] ss = c.toStereotypeModelArray(); if (ss != null) for (IStereotype s : ss) sb.append(" <<").append(s.getName()).append(">>");
        sb.append(" in ").append(c.getParent().getName()).append('\n');
        if (c.toAttributeArray() != null) for (IAttribute a : c.toAttributeArray()) sb.append("    -").append(a.getName()).append(':').append(Seq.typeText(a.getType())).append('\n');
        if (c.toOperationArray() != null) for (IOperation o : c.toOperationArray()) sb.append("    +").append(Seq.sig(o)).append('\n');
        if (c.toFromRelationshipArray() != null) for (ISimpleRelationship r : c.toFromRelationshipArray()) sb.append("    rel-from ").append(r.getModelType()).append(" -> ").append(r.getTo().getName()).append('\n');
        return sb.toString();
    }
    static void walk(IModelElement e, StringBuilder sb) {
        if (e instanceof IClass) sb.append(cls((IClass) e));
        IModelElement[] ch = e.toChildArray(); if (ch != null) for (IModelElement c : ch) if (c instanceof IPackage || c instanceof IClass) walk(c, sb);
    }
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder();
        walk(b.find("Package", "xmlrpc"), sb);
        walk(b.find("Package", "XMLRPCInterchange"), sb);
        return sb.toString();
    }
}
