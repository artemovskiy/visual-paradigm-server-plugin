package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
import com.vp.plugin.model.*;
/** Second pass: link each message to the activation of its sender/receiver lifeline that covers the message's y. */
public class Relink {
    public static Object run(B b) throws Exception {
        int set = 0, kept = 0;
        for (IDiagramUIModel d : b.project.toDiagramArray()) {
            if (!IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM.equals(d.getType())) continue;
            java.util.List<IDiagramElement> acts = new java.util.ArrayList<IDiagramElement>();
            for (IDiagramElement e : d.toDiagramElementArray()) if ("Activation".equals(e.getShapeType())) acts.add(e);
            for (IDiagramElement e : d.toDiagramElementArray()) {
                if (!(e instanceof IConnectorUIModel) || !(e.getMetaModelElement() instanceof IMessage)) continue;
                IMessage m = (IMessage) e.getMetaModelElement();
                if (m.getFromActivation() != null) { kept++; continue; }
                java.awt.Point[] p = ((IConnectorUIModel) e).getPoints();
                IActivation fa = find(acts, p[0]), ta = m.getType() == IMessage.TYPE_CREATE_MESSAGE ? null : find(acts, p[p.length - 1]);
                if (fa != null) { m.setFromActivation(fa); set++; }
                if (ta != null) m.setToActivation(ta);
            }
        }
        return "set " + set + ", already " + kept;
    }
    static IActivation find(java.util.List<IDiagramElement> acts, java.awt.Point p) {
        for (IDiagramElement a : acts)
            if (Math.abs(a.getX() + a.getWidth() / 2 - p.x) <= 8 && p.y >= a.getY() - 2 && p.y <= a.getY() + a.getHeight() + 2)
                return (IActivation) a.getMetaModelElement();
        return null;
    }
}
