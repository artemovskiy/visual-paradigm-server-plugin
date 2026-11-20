package ru.urfu.lab2;

import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.model.*;
import com.vp.plugin.model.factory.IModelElementFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Shared helpers: lookup, shapes, connectors, logging. */
public class B {
    public final ApplicationManager app = ApplicationManager.instance();
    public final IProject project = app.getProjectManager().getProject();
    public final DiagramManager dm = app.getDiagramManager();
    public final IModelElementFactory f = IModelElementFactory.instance();
    private final StringBuilder log = new StringBuilder();
    private int errors = 0;
    /** Scale applied to all coordinates taken from the manual's figures. */
    public double k = 1.0;

    public interface Step { void run() throws Exception; }

    public void step(String name, Step s) {
        try { s.run(); log("OK   " + name); }
        catch (Throwable t) {
            errors++;
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            log("FAIL " + name + "\n" + sw);
        }
    }

    public void log(String s) { log.append(s).append('\n'); }

    public void finish(String stage) {
        File pf = project.getProjectFile();
        File dir = pf != null ? pf.getParentFile() : new File(System.getProperty("user.home"));
        File out = new File(dir, "vp_lab2.log");
        try {
            OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out, true), "UTF-8");
            try { w.write("===== " + stage + " =====\n" + log + "\n"); } finally { w.close(); }
        } catch (Exception ignored) {}
        app.getViewManager().showMessage(stage + ": " + (errors == 0 ? "OK" : errors + " errors") + ". Log: " + out, "Lab2");
    }

    // ---------- lookup ----------
    public IModelElement find(String type, String name) {
        for (IModelElement e : project.toAllLevelModelElementArray(type))
            if (name.equals(e.getName())) return e;
        return null;
    }

    public IModelElement need(String type, String name) {
        IModelElement e = find(type, name);
        if (e == null) throw new IllegalStateException("Not found: " + type + " '" + name + "'");
        return e;
    }

    public IDiagramUIModel diagram(String type, String name, IModelElement parent) {
        for (IDiagramUIModel d : project.toDiagramArray()) {
            if (!type.equals(d.getType()) || !name.equals(d.getName())) continue;
            if (parent == null || (d.getParentModel() != null && parent.getId().equals(d.getParentModel().getId()))) return d;
        }
        return null;
    }

    public IDiagramUIModel newDiagram(String type, String name, IModelElement parent) {
        IDiagramUIModel d = (IDiagramUIModel) dm.createDiagram(type);
        d.setName(name);
        if (parent != null) parent.addSubDiagram(d);
        return d;
    }

    // ---------- model ----------
    /** Put child under parent in the model tree; ignore if VP refuses this pairing. */
    public void adopt(IModelElement parent, IModelElement child) {
        if (parent == null || child == null) return;
        try { parent.addChild(child); }
        catch (Throwable t) { log("WARN adopt " + child.getName() + " -> " + parent.getName() + ": " + t); }
    }

    public <T extends IModelElement> T named(T e, String name) { e.setName(name); return e; }

    // ---------- diagram ----------
    public IDiagramElement shape(IDiagramUIModel d, IModelElement e, double x, double y, double w, double h) {
        IDiagramElement s = dm.createDiagramElement(d, e);
        if (s == null) throw new IllegalStateException("VP returned no shape for " + e.getModelType() + " '" + e.getName() + "'");
        s.setBounds((int) Math.round(x * k), (int) Math.round(y * k), (int) Math.round(w * k), (int) Math.round(h * k));
        s.setRequestResetCaption(true);
        return s;
    }

    public IDiagramElement shapeOfType(IDiagramUIModel d, String shapeType, IModelElement e, double x, double y, double w, double h) {
        IDiagramElement s = dm.createDiagramElement(d, shapeType);
        if (s == null) throw new IllegalStateException("VP returned no shape of type " + shapeType);
        if (e != null) s.setMetaModelElement(e);
        s.setBounds((int) Math.round(x * k), (int) Math.round(y * k), (int) Math.round(w * k), (int) Math.round(h * k));
        s.setRequestResetCaption(true);
        return s;
    }

    public static String ifaces(Object o) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass())
            for (Class<?> i : c.getInterfaces()) if (i.getName().startsWith("com.vp.plugin")) out.add(i.getSimpleName());
        return out.toString();
    }

    /** Shape placed inside a container shape (state in a composite state, node in a loop node...). */
    public IDiagramElement inner(IDiagramUIModel d, IDiagramElement container, IModelElement e, double x, double y, double w, double h) {
        IDiagramElement s = shape(d, e, x, y, w, h);
        try { inside(container, s); } catch (Throwable t) { log("WARN inner " + e.getModelType() + ": " + t); }
        return s;
    }

    public void inside(IDiagramElement container, IDiagramElement child) {
        try { container.addChild((IShapeUIModel) child); }
        catch (Throwable t) { log("WARN inside: " + t); }
    }

    public IDiagramElement connect(IDiagramUIModel d, IModelElement rel, IDiagramElement from, IDiagramElement to) {
        IDiagramElement c = dm.createConnector(d, rel, from, to, null);
        if (c == null) throw new IllegalStateException("VP returned no connector for " + rel.getModelType()
                + " " + (from.getMetaModelElement() == null ? "?" : from.getMetaModelElement().getName())
                + " -> " + (to.getMetaModelElement() == null ? "?" : to.getMetaModelElement().getName()));
        c.setRequestResetCaption(true);
        return c;
    }

    /** Like connect(), but a failure is logged and the build continues. */
    public IDiagramElement tryConnect(IDiagramUIModel d, IModelElement rel, IDiagramElement from, IDiagramElement to) {
        try { return connect(d, rel, from, to); }
        catch (Throwable t) { errors++; log("FAIL connect: " + t.getMessage()); return null; }
    }

    // ---------- relationships ----------
    public IAssociation assoc(IModelElement from, IModelElement to) {
        IAssociation a = f.createAssociation();
        a.setFrom(from);
        a.setTo(to);
        ((IAssociationEnd) a.getFromEnd()).setNavigable(IAssociationEnd.NAVIGABLE_NAV_UNSPECIFIED);
        ((IAssociationEnd) a.getToEnd()).setNavigable(IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE);
        return a;
    }

    /** Generalization: general (parent) is 'from', specific (child) is 'to' in VP API. */
    public IGeneralization gen(IModelElement general, IModelElement specific) {
        IGeneralization g = f.createGeneralization();
        g.setFrom(general);
        g.setTo(specific);
        return g;
    }

    public IControlFlow flow(IModelElement from, IModelElement to, String guard) {
        IControlFlow c = f.createControlFlow();
        c.setFrom(from);
        c.setTo(to);
        if (guard != null) c.setGuard(guard);
        return c;
    }
}
