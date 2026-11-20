package ru.urfu.lab2;
import com.vp.plugin.diagram.*;
import com.vp.plugin.diagram.connector.IAssociationUIModel;
import com.vp.plugin.model.*;
public class AssocProbe {
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (IDiagramUIModel d : b.project.toDiagramArray()) {
            if (!"Key Abstractions".equals(d.getName())) continue;
            for (IDiagramElement e : d.toDiagramElementArray()) {
                if (!(e instanceof IAssociationUIModel)) continue;
                IAssociation a = (IAssociation) e.getMetaModelElement();
                IAssociationUIModel u = (IAssociationUIModel) e;
                sb.append(a.getFrom().getName()).append("->").append(a.getTo().getName())
                  .append(" roles=").append(((IAssociationEnd) a.getFromEnd()).getName()).append("/").append(((IAssociationEnd) a.getToEnd()).getName())
                  .append(" showFR=").append(u.isShowFromRoleName()).append(" pts=").append(java.util.Arrays.toString(((IConnectorUIModel) e).getPoints()).replace("java.awt.Point", "")).append('\n');
            }
        }
        return sb.toString();
    }
}
