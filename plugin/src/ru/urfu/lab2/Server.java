package ru.urfu.lab2;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.ModelConvertionManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRelationship;

import javax.swing.SwingUtilities;
import java.io.*;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Local control server (127.0.0.1, token-protected, minimal HTTP over a bare ServerSocket).
 * Port and token go to vp_server.json (workspace and ~/.vp_lab2_server.json). All VP API calls run on the EDT.
 */
public class Server {
    static final String WORKSPACE = System.getProperty("user.home")
            + "/Documents/урфу/учеба/3 курс 1 сем/5 сем/Архитектура ПО/Лаба 2/workspace";
    static final long EDT_TIMEOUT_S = 180;

    static volatile ServerSocket ss;
    static String token;
    static File pluginDir;

    static synchronized void start(File dir) {
        if (ss != null) return;
        pluginDir = dir;
        try {
            byte[] r = new byte[16];
            new SecureRandom().nextBytes(r);
            StringBuilder t = new StringBuilder();
            for (byte x : r) t.append(String.format("%02x", x));
            token = t.toString();
            ss = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
            String json = "{\"port\": " + ss.getLocalPort() + ", \"token\": \"" + token + "\", \"pluginDir\": " + q(String.valueOf(dir)) + "}\n";
            for (File f : new File[]{new File(WORKSPACE, "vp_server.json"), new File(System.getProperty("user.home"), ".vp_lab2_server.json")}) {
                try {
                    if (!f.getParentFile().isDirectory()) continue;
                    try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { w.write(json); }
                    f.setReadable(false, false); f.setReadable(true, true);
                } catch (Exception ignored) {}
            }
            Thread pop = new Thread(Server::dismissStartupPopups, "lab2-popups");
            pop.setDaemon(true);
            pop.start();
            Thread th = new Thread(Server::loop, "lab2-server");
            th.setDaemon(true);
            th.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /** For the first 2 minutes: "Remind me later" on the activation nag, close "What's New". */
    private static void dismissStartupPopups() {
        long end = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(1000);
                clickInDialog("not been activated", "Remind me later");
                for (java.awt.Window w : java.awt.Window.getWindows())
                    if (w.isShowing() && w instanceof java.awt.Dialog && "What's New".equals(((java.awt.Dialog) w).getTitle()))
                        SwingUtilities.invokeLater(w::dispose);
                    else if (w.isShowing() && w instanceof java.awt.Frame && "What's New".equals(((java.awt.Frame) w).getTitle()))
                        SwingUtilities.invokeLater(w::dispose);
            } catch (Throwable ignored) {}
        }
    }

    static synchronized void stop() {
        try { if (ss != null) ss.close(); } catch (IOException ignored) {}
        ss = null;
    }

    private static void loop() {
        while (ss != null && !ss.isClosed()) {
            try {
                final Socket s = ss.accept();
                Thread h = new Thread(() -> handle(s), "lab2-req");
                h.setDaemon(true);
                h.start();
            } catch (IOException e) { if (ss == null || ss.isClosed()) return; }
        }
    }

    // ------------------------------------------------------------------ HTTP

    static class Req { String method, path; Map<String, String> q = new HashMap<>(), h = new HashMap<>(); byte[] body = new byte[0]; }
    static class Resp {
        int code = 200; String type = "application/json; charset=utf-8"; byte[] body;
        Resp(String s) { body = s.getBytes(StandardCharsets.UTF_8); }
        Resp(int code, String s) { this(s); this.code = code; }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1 && c != '\n') if (c != '\r') b.write(c);
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(600_000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            String first = line(in);
            if (first == null) return;
            Req r = new Req();
            String[] p = first.split(" ");
            r.method = p[0];
            String target = p.length > 1 ? p[1] : "/";
            int qi = target.indexOf('?');
            r.path = qi < 0 ? target : target.substring(0, qi);
            if (qi >= 0) for (String kv : target.substring(qi + 1).split("&")) {
                if (kv.isEmpty()) continue;
                int e = kv.indexOf('=');
                String k = URLDecoder.decode(e < 0 ? kv : kv.substring(0, e), "UTF-8");
                String v = e < 0 ? "" : URLDecoder.decode(kv.substring(e + 1), "UTF-8");
                r.q.put(k, v);
            }
            String l;
            while ((l = line(in)) != null && !l.isEmpty()) {
                int c = l.indexOf(':');
                if (c > 0) r.h.put(l.substring(0, c).trim().toLowerCase(), l.substring(c + 1).trim());
            }
            int len = Integer.parseInt(r.h.getOrDefault("content-length", "0"));
            if (len > 0) {
                r.body = new byte[len];
                int off = 0;
                while (off < len) { int n = in.read(r.body, off, len - off); if (n < 0) break; off += n; }
            }
            Resp resp;
            String tok = r.h.containsKey("x-token") ? r.h.get("x-token") : r.q.get("token");
            if (!token.equals(tok)) resp = new Resp(403, "{\"error\": \"bad token\"}");
            else {
                try { resp = route(r); }
                catch (Throwable t) { resp = new Resp(500, "{\"error\": " + q(stack(t)) + "}"); }
            }
            OutputStream out = sock.getOutputStream();
            out.write(("HTTP/1.1 " + resp.code + " X\r\nContent-Type: " + resp.type + "\r\nContent-Length: " + resp.body.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(resp.body);
            out.flush();
        } catch (Throwable ignored) {}
    }

    /** Run on the Swing EDT and wait (bounded, so a modal dialog can't hang the server forever). */
    static <T> T edt(Callable<T> c) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return c.call();
        FutureTask<T> ft = new FutureTask<>(c);
        SwingUtilities.invokeLater(ft);
        try { return ft.get(EDT_TIMEOUT_S, TimeUnit.SECONDS); }
        catch (TimeoutException te) { throw new IllegalStateException("EDT call still running after " + EDT_TIMEOUT_S + "s (a modal dialog in VP?)"); }
        catch (java.util.concurrent.ExecutionException ee) { throw (ee.getCause() instanceof Exception) ? (Exception) ee.getCause() : ee; }
    }

    // ------------------------------------------------------------------ routes

    private static Resp route(Req r) throws Exception {
        final ApplicationManager app = ApplicationManager.instance();
        switch (r.path) {
            case "/status":
                return new Resp(edt(() -> {
                    IProject p = app.getProjectManager().getProject();
                    IDiagramUIModel a = app.getDiagramManager().getActiveDiagram();
                    return "{\"project\": " + q(p == null ? null : p.getName())
                            + ", \"file\": " + q(p == null || p.getProjectFile() == null ? null : p.getProjectFile().getPath())
                            + ", \"modified\": " + (p != null && p.isModified())
                            + ", \"activeDiagram\": " + q(a == null ? null : a.getName())
                            + ", \"diagrams\": " + (p == null ? 0 : p.toDiagramArray().length) + "}";
                }));
            case "/diagrams":
                return new Resp(edt(() -> {
                    StringBuilder sb = new StringBuilder("[");
                    for (IDiagramUIModel d : app.getProjectManager().getProject().toDiagramArray()) {
                        if (sb.length() > 1) sb.append(",\n ");
                        sb.append("{\"name\": ").append(q(d.getName())).append(", \"type\": ").append(q(d.getType()))
                          .append(", \"parent\": ").append(q(d.getParentModel() == null ? null : d.getParentModel().getName()))
                          .append(", \"id\": ").append(q(d.getId())).append("}");
                    }
                    return sb.append("]").toString();
                }));
            case "/open": {
                final File f = new File(r.q.getOrDefault("path", ""));
                if (!f.isFile()) return new Resp(400, "{\"error\": \"no such file\"}");
                return new Resp(edt(() -> "{\"ok\": " + app.getProjectManager().openProject(f) + "}"));
            }
            case "/save":
                return new Resp(edt(() -> "{\"ok\": " + app.getProjectManager().saveProject() + "}"));
            case "/revert": {
                // re-open the file; VP asks "Do you want to save the project?" -> we press "No" from inside the JVM
                final File f = edt(() -> app.getProjectManager().getProject().getProjectFile());
                if (f == null) return new Resp(400, "{\"error\": \"project has no file\"}");
                FutureTask<Boolean> ft = new FutureTask<>(() -> app.getProjectManager().openProject(f));
                SwingUtilities.invokeLater(ft);
                String pressed = null;
                long end = System.currentTimeMillis() + 60_000;
                while (!ft.isDone() && System.currentTimeMillis() < end) {
                    Thread.sleep(200);
                    if (pressed == null) pressed = clickInDialog("save the project", "No");
                }
                if (!ft.isDone()) return new Resp(500, "{\"error\": \"revert still running after 60s\", \"dialogs\": " + dialogs() + "}");
                return new Resp("{\"ok\": " + ft.get() + ", \"pressed\": " + q(pressed) + ", \"file\": " + q(f.getPath()) + "}");
            }
            case "/dialogs":
                return new Resp(dialogs());
            case "/click": {
                String pr = clickInDialog(r.q.getOrDefault("text", ""), r.q.getOrDefault("button", ""));
                return new Resp("{\"pressed\": " + q(pr) + "}");
            }
            case "/show":
                return new Resp(edt(() -> { app.getDiagramManager().openDiagram(diagram(r.q.get("diagram"))); return "{\"ok\": true}"; }));
            case "/inspect":
                return new Resp(edt(() -> inspect(diagram(r.q.get("diagram")))));
            case "/export": {
                final File out = new File(r.q.getOrDefault("out", "/tmp/vp_export.png"));
                return new Resp(edt(() -> {
                    IDiagramUIModel d = diagram(r.q.get("diagram"));
                    app.getModelConvertionManager().exportDiagramAsImage(d, out, ModelConvertionManager.IMAGE_TYPE_PNG);
                    return "{\"ok\": " + out.isFile() + ", \"out\": " + q(out.getPath()) + ", \"diagram\": " + q(d.getName()) + "}";
                }));
            }
            case "/run": {
                String src = new String(r.body, StandardCharsets.UTF_8);
                String srcDir = r.h.get("x-src");
                return new Resp(LiveRunner.run(src, srcDir == null || srcDir.isEmpty() ? null : new File(srcDir), pluginDir));
            }
            default:
                return new Resp(404, "{\"error\": \"unknown path " + r.path + "\", \"paths\": [\"/status\",\"/diagrams\",\"/open?path=\",\"/save\",\"/revert\",\"/show?diagram=\",\"/inspect?diagram=\",\"/export?diagram=&out=\",\"/run\",\"/dialogs\",\"/click?text=&button=\"]}");
        }
    }

    static IDiagramUIModel diagram(String name) {
        ApplicationManager app = ApplicationManager.instance();
        if (name == null || name.isEmpty()) {
            IDiagramUIModel a = app.getDiagramManager().getActiveDiagram();
            if (a == null) throw new IllegalStateException("no active diagram");
            return a;
        }
        String parent = null;
        int sl = name.indexOf('/');
        if (sl > 0) { parent = name.substring(0, sl); name = name.substring(sl + 1); }
        for (IDiagramUIModel d : app.getProjectManager().getProject().toDiagramArray())
            if (name.equals(d.getName()) && (parent == null || (d.getParentModel() != null && parent.equals(d.getParentModel().getName()))))
                return d;
        throw new IllegalStateException("no diagram '" + name + "'");
    }

    // ------------------------------------------------------------------ dialogs (found and answered from inside the JVM)

    /** Visible dialogs/windows other than the main frame, with their texts and buttons. */
    static String dialogs() {
        StringBuilder sb = new StringBuilder("[");
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (!w.isShowing() || w instanceof java.awt.Frame && ((java.awt.Frame) w).getTitle().contains("Visual Paradigm Community")) continue;
            List<String> texts = new ArrayList<>(), buttons = new ArrayList<>();
            walk(w, texts, buttons, null, null);
            if (sb.length() > 1) sb.append(", ");
            String title = w instanceof java.awt.Dialog ? ((java.awt.Dialog) w).getTitle() : w instanceof java.awt.Frame ? ((java.awt.Frame) w).getTitle() : "";
            sb.append("{\"title\": ").append(q(title)).append(", \"texts\": ").append(list(texts)).append(", \"buttons\": ").append(list(buttons)).append("}");
        }
        return sb.append("]").toString();
    }

    /** Press button {@code button} in the first visible window whose texts/title contain {@code text}; returns what was pressed. */
    static String clickInDialog(String text, String button) {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (!w.isShowing()) continue;
            List<String> texts = new ArrayList<>(), buttons = new ArrayList<>();
            walk(w, texts, buttons, null, null);
            String title = w instanceof java.awt.Dialog ? ((java.awt.Dialog) w).getTitle() : "";
            boolean match = text.isEmpty() || (title != null && title.contains(text));
            for (String t : texts) if (t.contains(text)) match = true;
            if (!match) continue;
            final javax.swing.AbstractButton[] hit = new javax.swing.AbstractButton[1];
            walk(w, null, null, button, hit);
            if (hit[0] != null) {
                final javax.swing.AbstractButton btn = hit[0];
                SwingUtilities.invokeLater(btn::doClick);
                return title + " / " + button;
            }
        }
        return null;
    }

    private static void walk(java.awt.Component c, List<String> texts, List<String> buttons, String want, javax.swing.AbstractButton[] hit) {
        if (c instanceof javax.swing.AbstractButton) {
            String t = ((javax.swing.AbstractButton) c).getText();
            if (t != null && !t.isEmpty()) {
                if (buttons != null) buttons.add(t);
                if (want != null && t.equals(want) && hit[0] == null && c.isShowing()) hit[0] = (javax.swing.AbstractButton) c;
            }
        } else if (texts != null) {
            String t = null;
            if (c instanceof javax.swing.JLabel) t = ((javax.swing.JLabel) c).getText();
            else if (c instanceof javax.swing.text.JTextComponent) t = ((javax.swing.text.JTextComponent) c).getText();
            if (t != null && !t.trim().isEmpty() && t.length() < 2000) texts.add(t.trim());
        }
        if (c instanceof javax.swing.JOptionPane && texts != null) {
            Object m = ((javax.swing.JOptionPane) c).getMessage();
            if (m instanceof String) texts.add((String) m);
        }
        if (c instanceof java.awt.Container)
            for (java.awt.Component k : ((java.awt.Container) c).getComponents()) walk(k, texts, buttons, want, hit);
    }

    static String list(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (String x : l) { if (sb.length() > 1) sb.append(", "); sb.append(q(x)); }
        return sb.append("]").toString();
    }

    // ------------------------------------------------------------------ inspect

    static String inspect(IDiagramUIModel d) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"diagram\": ").append(q(d.getName())).append(", \"type\": ").append(q(d.getType())).append(", \"elements\": [\n");
        Set<IDiagramElement> children = new HashSet<>();
        IDiagramElement[] all = d.toDiagramElementArray();
        for (IDiagramElement e : all) for (IDiagramElement c : kids(e)) children.add(c);
        boolean first = true;
        for (IDiagramElement e : all) {
            if (children.contains(e)) continue;
            if (!first) sb.append(",\n");
            first = false;
            el(sb, e, 1, new HashSet<>());
        }
        return sb.append("\n]}").toString();
    }

    static IDiagramElement[] kids(IDiagramElement e) {
        try {
            Object[] a = (Object[]) e.getClass().getMethod("toChildArray").invoke(e);
            IDiagramElement[] out = new IDiagramElement[a.length];
            for (int i = 0; i < a.length; i++) out[i] = (IDiagramElement) a[i];
            return out;
        } catch (Throwable t) { return new IDiagramElement[0]; }
    }

    static void el(StringBuilder sb, IDiagramElement e, int depth, Set<IDiagramElement> seen) {
        String pad = String.join("", Collections.nCopies(depth, "  "));
        sb.append(pad).append("{\"shape\": ").append(q(e.getShapeType())).append(", \"id\": ").append(q(e.getId()))
          .append(", \"x\": ").append(e.getX()).append(", \"y\": ").append(e.getY())
          .append(", \"w\": ").append(e.getWidth()).append(", \"h\": ").append(e.getHeight());
        IModelElement m = e.getMetaModelElement();
        if (m != null) {
            sb.append(", \"model\": {\"type\": ").append(q(m.getModelType())).append(", \"name\": ").append(q(m.getName())).append(", \"id\": ").append(q(m.getId()));
            if (m instanceof IRelationship) {
                IRelationship rl = (IRelationship) m;
                sb.append(", \"from\": ").append(q(rl.getFrom() == null ? null : rl.getFrom().getName()))
                  .append(", \"to\": ").append(q(rl.getTo() == null ? null : rl.getTo().getName()));
            }
            sb.append("}");
        }
        sb.append(", \"props\": ").append(getters(e));
        IDiagramElement[] ks = kids(e);
        if (ks.length > 0 && seen.add(e)) {
            sb.append(", \"children\": [\n");
            for (int i = 0; i < ks.length; i++) { if (i > 0) sb.append(",\n"); el(sb, ks[i], depth + 1, seen); }
            sb.append("]");
        }
        sb.append("}");
    }

    static String getters(Object o) {
        StringBuilder sb = new StringBuilder("{");
        Method[] ms = o.getClass().getMethods();
        Arrays.sort(ms, Comparator.comparing(Method::getName));
        for (Method mt : ms) {
            String n = mt.getName();
            if (mt.getParameterTypes().length != 0 || !(n.startsWith("get") || n.startsWith("is"))) continue;
            if (n.equals("getX") || n.equals("getY") || n.equals("getWidth") || n.equals("getHeight") || n.equals("getId") || n.equals("getShapeType")) continue;
            Class<?> rt = mt.getReturnType();
            if (!(rt.isPrimitive() || rt == String.class) || rt == void.class) continue;
            try {
                Object v = mt.invoke(o);
                if (v == null || "".equals(v) || Boolean.FALSE.equals(v)) continue;
                if (sb.length() > 1) sb.append(", ");
                sb.append(q(n)).append(": ").append(v instanceof String ? q((String) v) : String.valueOf(v));
            } catch (Throwable ignored) {}
        }
        return sb.append("}").toString();
    }

    // ------------------------------------------------------------------ util

    static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
