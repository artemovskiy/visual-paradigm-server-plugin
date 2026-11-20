package ru.urfu.lab2;

import com.vp.plugin.diagram.IDiagramUIModel;

/** Read-only smoke test: lists the diagrams of the open project. Changes nothing. */
public class Hello {
    public static Object run(B b) throws Exception {
        StringBuilder sb = new StringBuilder("project: " + b.project.getProjectFile() + "\n");
        for (IDiagramUIModel d : b.project.toDiagramArray())
            sb.append(d.getType()).append(" | ").append(d.getParentModel() == null ? "" : d.getParentModel().getName())
              .append(" | ").append(d.getName()).append('\n');
        return sb.toString();
    }
}
