package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
import com.vp.plugin.model.*;
public class Cap2 {
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String name : new String[]{"Login Basic Flow"}) {
            IDiagramUIModel d = null;
            for (IDiagramUIModel x : b.project.toDiagramArray()) if (name.equals(x.getName())) d = x;
            int acts = 0;
            for (IDiagramElement e : d.toDiagramElementArray()) if ("Activation".equals(e.getShapeType())) acts++;
            sb.append("== ").append(name).append(" activations=").append(acts).append('\n');
            int n = 0;
            for (IDiagramElement e : d.toDiagramElementArray()) {
                if (!(e instanceof IConnectorUIModel) || n++ > 2) continue;
                Object cap = e.getClass().getMethod("getCaptionUIModel").invoke(e);
                Class<?> c = cap.getClass();
                IMessage m = (IMessage) e.getMetaModelElement();
                sb.append(m.getName()).append(" cap=").append(c.getMethod("getX").invoke(cap) + "," + c.getMethod("getY").invoke(cap) + " w=" + c.getMethod("getWidth").invoke(cap) + " vis=" + c.getMethod("isVisible").invoke(cap))
                  .append(" fromAct=").append(m.getFromActivation() != null).append(" toAct=").append(m.getToActivation() != null)
                  .append(" rr=").append(e.isRequestResetCaption()).append(" sn=").append(((com.vp.plugin.diagram.connector.IMessageUIModel) e).getShowMessageName()).append(" scn=").append(((IConnectorUIModel) e).getShowConnectorName()).append(" from=").append(((IConnectorUIModel) e).getFromShape().getShapeType()).append('\n');
            }
        }
        return sb.toString();
    }
}
