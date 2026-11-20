package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
public class CapFix {
    public static Object run(B b) throws Exception {
        int n = 0;
        for (IDiagramUIModel d : b.project.toDiagramArray()) {
            if (!IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM.equals(d.getType())) continue;
            for (IDiagramElement e : d.toDiagramElementArray()) {
                if (!(e instanceof com.vp.plugin.diagram.connector.IMessageUIModel)) continue;
                Object cap = e.getClass().getMethod("getCaptionUIModel").invoke(e);
                if (!(Boolean) cap.getClass().getMethod("isVisible").invoke(cap)) { cap.getClass().getMethod("setVisible", boolean.class).invoke(cap, true); n++; } e.setRequestResetCaption(true);
            }
        }
        return "made visible: " + n;
    }
}
