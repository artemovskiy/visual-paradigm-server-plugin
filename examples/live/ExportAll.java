package ru.urfu.lab2;
import com.vp.plugin.ModelConvertionManager;
import com.vp.plugin.diagram.IDiagramUIModel;
import java.io.File;
/** Export the report's diagrams: "file|type|parent|name" lines from export.txt into the given folder. */
public class ExportAll {
    public static Object run(B b) throws Exception {
        File base = new File(System.getProperty("user.home") + "/Library/Caches/lab2-live/export.txt");
        java.util.List<String> lines = java.nio.file.Files.readAllLines(base.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        File dir = new File(lines.get(0).trim());
        dir.mkdirs();
        StringBuilder sb = new StringBuilder();
        for (String l : lines.subList(1, lines.size())) {
            if (l.trim().isEmpty()) continue;
            String[] f = l.split("\\|");
            IDiagramUIModel hit = null;
            for (IDiagramUIModel d : b.project.toDiagramArray())
                if (f[3].equals(d.getName()) && (f[1].isEmpty() || f[1].equals(d.getType()))
                        && (f[2].isEmpty() || (d.getParentModel() != null && f[2].equals(d.getParentModel().getName())))) hit = d;
            if (hit == null) { sb.append("MISSING ").append(l).append('\n'); continue; }
            b.app.getModelConvertionManager().exportDiagramAsImage(hit, new File(dir, f[0]), ModelConvertionManager.IMAGE_TYPE_PNG);
            sb.append("ok ").append(f[0]).append('\n');
        }
        return sb.toString();
    }
}
