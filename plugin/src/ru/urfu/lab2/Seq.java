package ru.urfu.lab2;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IFramedInteractionDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.connector.IMessageUIModel;
import com.vp.plugin.model.*;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

/** Small builder for one sequence diagram. Coordinates are figure units, scaled by b.k. */
public class Seq {
    final B b;
    final IDiagramUIModel d;
    final IFrame frame;
    final Map<String, IModelElement> ll = new HashMap<String, IModelElement>();
    final Map<String, IDiagramElement> ls = new HashMap<String, IDiagramElement>();
    final Map<String, Double> cx = new HashMap<String, Double>();
    final Map<String, Double> hw = new HashMap<String, Double>();
    double bottom = 600;

    static final double HEAD_W = 130, HEAD_H = 50;

    Seq(B b, String name, IModelElement owner, double k, double bottom) {
        this.b = b;
        this.bottom = bottom;
        b.k = k;
        // remove a previous version of this diagram (and its interaction frame) so the action can be re-run
        for (IDiagramUIModel old : b.project.toDiagramArray()) {
            if (IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM.equals(old.getType()) && name.equals(old.getName())
                    && old.getParentModel() != null && owner.getId().equals(old.getParentModel().getId())) {
                IFrame f = ((IFramedInteractionDiagramUIModel) old).getRootFrame(false);
                old.delete();
                if (f != null) try { f.delete(); } catch (Throwable ignored) {}
            }
        }
        d = b.newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM, name, owner);
        b.dm.openDiagram(d);
        frame = ((IFramedInteractionDiagramUIModel) d).getRootFrame(true);
        frame.setName(name);
        b.log("seq '" + name + "' frame=" + (frame == null ? null : frame.getModelType()) + " parent=" + (frame.getParent() == null ? null : frame.getParent().getName()));
    }

    int sx(double x) { return (int) Math.round(x * b.k); }

    /** Design-model classes share names with analysis classes: resolve through this map first when set. */
    Map<String, ? extends IModelElement> classes;
    IModelElement cls(String name) {
        if (classes != null && classes.get(name) != null) return classes.get(name);
        return b.need("Class", name);
    }

    /** Found/lost message end (the black dot), as in the template: a LostFoundMessageEnd shape without a model element. */
    void gate(String key, double centerX, double y) {
        centerX += dx;
        IDiagramElement e = b.dm.createDiagramElement(d, "LostFoundMessageEnd");
        e.setBounds(sx(centerX - 7), sx(y - 10), sx(15), sx(20));
        ls.put(key, e); cx.put(key, centerX); hw.put(key, 0.0);
    }

    /** Extra lifeline stereotype by name, e.g. "interface", "subsystem proxy". */
    void stereo(String key, String st) {
        try { ll.get(key).addStereotype(st); } catch (Throwable t) { b.log("WARN stereo " + key + " " + st + ": " + t.getMessage()); }
    }

    /** Header width (figure units) that fits the underlined "name : Class" text. */
    double headW(String text) { return Math.max(110, (FM.stringWidth(text) + 24) / b.k); }

    static String typeText(Object t) {
        if (t == null) return null;
        if (t instanceof IModelElement) return ((IModelElement) t).getName();
        String s = t.toString();
        return s.isEmpty() ? null : s;
    }

    /** "op(p : T[4], q : U[]) : R[]" from the model, like the manual's figures. */
    static String sig(IOperation o) {
        StringBuilder sb = new StringBuilder(o.getName()).append('(');
        IParameter[] ps = o.toParameterArray();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(ps[i].getName());
            String t = typeText(ps[i].getType());
            if (t != null) {
                sb.append(" : ").append(t);
                if (ps[i].getTypeModifier() != null) sb.append(ps[i].getTypeModifier());
                String m = ps[i].getMultiplicity();
                if (m != null && !m.isEmpty() && !"Unspecified".equals(m) && !"1".equals(m)) sb.append('[').append(m).append(']');
            }
        }
        sb.append(')');
        String r = typeText(o.getReturnType());
        if (r != null && !"void".equals(r)) sb.append(" : ").append(r).append(o.getTypeModifier() == null ? "" : o.getTypeModifier());
        return sb.toString();
    }

    /** Object lifeline "name : Class" with the class stereotype; top = y of the header (for created objects, the create message y - HEAD_H/2). */
    /** Horizontal shift (figure units) applied to every x given to this builder. */
    double dx = 0;

    void lifeline(String key, String name, String cls, double centerX, double top, boolean multi) {
        centerX += dx;
        IInteractionLifeLine l = b.f.createInteractionLifeLine();
        l.setName(name);
        IModelElement c = cls(cls);
        l.setBaseClassifier(c);
        // lifelines need the InteractionLifeLine-based stereotype of the same name (from stereotypes.xml)
        if (classes == null && c.toStereotypeModelArray() != null) for (IStereotype st : c.toStereotypeModelArray()) {   // design lifelines: stereotypes set explicitly
            try { l.addStereotype(st.getName()); } catch (Throwable t) { b.log("WARN lifeline stereotype " + st.getName() + ": " + t.getMessage()); }
        }
        if (multi) l.setMultiObject(true);
        frame.addInteractionLifeLine(l);
        double w = headW(name + " : " + cls);
        IDiagramElement s = b.shape(d, l, centerX - w / 2, top, w, bottom - top);
        ll.put(key, l); ls.put(key, s); cx.put(key, centerX); hw.put(key, w);
    }

    /** Actor lifeline (InteractionActor, as in the template's sequence diagrams). */
    void actor(String key, String actorName, double centerX) {
        centerX += dx;
        IInteractionActor ia = b.named(b.f.createInteractionActor(), actorName);
        frame.addInteractionActor(ia);
        IDiagramElement s = b.shape(d, ia, centerX - 20, 5, 40, bottom - 5);
        ll.put(key, ia); ls.put(key, s); cx.put(key, centerX); hw.put(key, 40.0);
    }

    IOperation op(String cls, String opName) {
        IClass c = (IClass) cls(cls);
        for (IOperation o : c.toOperationArray()) if (opName.equals(o.getName())) return o;
        throw new IllegalStateException("No operation " + cls + "." + opName);
    }

    private IMessage message(String from, String to, String name, int type, IModelElement action) {
        IMessage m = b.f.createMessage();
        if (name != null) m.setName(name);
        if (ll.get(from) != null) ((IRelationship) m).setFrom(ll.get(from));   // null = found message (gate)
        if (ll.get(to) != null) ((IRelationship) m).setTo(ll.get(to));         // null = lost message
        m.setType(type);
        if (action != null) m.setActionType(action);
        return m;
    }

    static final int CALL = 0, REPLY = 1, CREATE = 2, DESTROY = 3, SELF = 4, ASYNC = 5;
    static final double ACT_W = 8;
    /** Needed: VP paints message captions only for messages attached to activations. */
    boolean withActivations = true;
    static boolean nestOperands = true;
    static int FLAG_NAME = -1, FLAG_SIG = -1, FLAG_CONN = -1;   // VP defaults: explicit "show name" hides captions after reload
    final Map<IMessage, String> texts = new HashMap<IMessage, String>();
    final java.util.List<Msg> links = new java.util.ArrayList<Msg>();
    final java.util.List<Object[]> drawn = new java.util.ArrayList<Object[]>();

    /** Messages are recorded first and drawn in done(), after the activation bars exist. */
    static class Msg {
        final IMessage m; final String from, to; final double y; final int kind; double y2;
        Msg(IMessage m, String from, String to, double y, int kind) { this.m = m; this.from = from; this.to = to; this.y = y; this.kind = kind; this.y2 = y; }
    }
    final java.util.List<Msg> msgs = new java.util.ArrayList<Msg>();
    final Map<String, double[]> span = new HashMap<String, double[]>();
    final Map<String, IActivation> act = new HashMap<String, IActivation>();
    final Map<String, IDiagramElement> actShape = new HashMap<String, IDiagramElement>();
    final Map<String, Double> destroyedAt = new HashMap<String, Double>();

    private IMessage add(IMessage m, String from, String to, double y, int kind) {
        msgs.add(new Msg(m, from, to, y, kind));
        return m;
    }

    /** Synchronous call bound to an operation of the receiver's class; label "op()" unless given. */
    IMessage call(String from, String to, String cls, String opName, double y) {
        return call(from, to, cls, opName, null, y);
    }

    IMessage call(String from, String to, String cls, String opName, String label, double y) {
        IModelElement action = null;
        String name = label != null ? label : opName, text = null;
        if (cls != null) {
            IActionTypeCall call = b.f.createActionTypeCall();
            IOperation o = op(cls, opName);
            call.setOperation(o);
            action = call;
            if (label == null) { name = null; text = sig(o); }   // VP prints the operation signature itself
        }
        IMessage m = add(message(from, to, name, IMessage.TYPE_MESSAGE, action), from, to, y, CALL);
        texts.put(m, text != null ? text : name);
        return m;
    }

    /** Asynchronous signal drawn as a slanted (duration) line from y to y2, e.g. to an external system actor. */
    IMessage async(String from, String to, String name, double y, double y2) {
        IMessage m = message(from, to, name, IMessage.TYPE_DURATION_MESSAGE, b.f.createActionTypeSend());
        m.setAsynchronous(true);
        add(m, from, to, y, ASYNC);
        msgs.get(msgs.size() - 1).y2 = y2;
        return m;
    }

    IMessage reply(String from, String to, String name, double y) {
        return add(message(from, to, name, IMessage.TYPE_MESSAGE, b.f.createActionTypeReturn()), from, to, y, REPLY);
    }

    IMessage create(String from, String to, String name, double y) {
        return add(message(from, to, name, IMessage.TYPE_CREATE_MESSAGE, b.f.createActionTypeCreate()), from, to, y, CREATE);
    }

    IMessage destroy(String from, String to, String name, double y) {
        ((IInteractionLifeLine) ll.get(to)).setStopped(true);
        destroyedAt.put(to, y);
        return add(message(from, to, name, IMessage.TYPE_MESSAGE, b.f.createActionTypeDestroy()), from, to, y, DESTROY);
    }

    IMessage self(String on, String cls, String opName, double y) {
        IActionTypeCall call = b.f.createActionTypeCall();
        call.setOperation(op(cls, opName));
        IMessage m = add(message(on, on, null, IMessage.TYPE_SELF_MESSAGE, call), on, on, y, SELF);
        texts.put(m, sig((IOperation) call.getOperation()));
        return m;
    }

    private void extend(String key, double y) {
        if (!(ll.get(key) instanceof IInteractionLifeLine)) return;   // actors get no bars
        double[] s = span.get(key);
        if (s == null) span.put(key, new double[]{y, y}); else { s[0] = Math.min(s[0], y); s[1] = Math.max(s[1], y); }
    }

    /** Activation bars (first..last message on each object lifeline), then all messages. Call once at the end. */
    void done() {
        for (Msg x : msgs) {
            if (x.kind == SELF) { extend(x.from, x.y); extend(x.from, x.y + 18); continue; }
            if (x.kind == ASYNC) { extend(x.from, x.y); extend(x.to, x.y2); continue; }
            extend(x.from, x.y);
            if (x.kind != CREATE) extend(x.to, x.y);
        }
        if (withActivations) for (Map.Entry<String, double[]> e : span.entrySet()) {
            String key = e.getKey();
            double y1 = e.getValue()[0] - 6, y2 = Math.max(e.getValue()[1] + 6, y1 + 20);
            IActivation a = b.f.createActivation();
            ((IInteractionLifeLine) ll.get(key)).addActivation(a);
            try {
                IDiagramElement s = b.shape(d, a, cx.get(key) - ACT_W / 2, y1, ACT_W, y2 - y1);
                // like the template: activation shapes are top-level with absolute bounds (nesting breaks on reload)
                s.setBounds(sx(cx.get(key) - ACT_W / 2), sx(y1), sx(ACT_W), sx(y2 - y1));
                act.put(key, a); actShape.put(key, s);
            } catch (Throwable t) { b.log("WARN activation " + key + ": " + t); }
        }
        for (Map.Entry<String, Double> e : destroyedAt.entrySet()) {   // lifeline ends with X at the destroy message
            IDiagramElement s = ls.get(e.getKey());
            s.setHeight(sx(e.getValue() + 22) - s.getY());   // room for the X below the activation
        }
        for (Msg x : msgs) draw(x);
        // createConnector resets the message->activation links: set them again once everything is drawn
        int linked = 0;
        for (Msg x : links) {
            if (act.get(x.from) != null) { x.m.setFromActivation(act.get(x.from)); linked++; }
            if (x.kind != CREATE && act.get(x.to) != null) x.m.setToActivation(act.get(x.to));
        }
        b.log("activation links: " + linked + "; check " + (links.isEmpty() ? "-" : String.valueOf(links.get(links.size() - 1).m.getFromActivation() != null)));
        // captions: work on the diagram's own connector objects (as returned by the diagram, not by createConnector)
        Map<String, Object[]> byModel = new HashMap<String, Object[]>();
        for (Object[] o : drawn) byModel.put(((IDiagramElement) o[0]).getMetaModelElement().getId(), o);
        for (IDiagramElement e : d.toDiagramElementArray()) {
            if (!(e instanceof IMessageUIModel) || e.getMetaModelElement() == null) continue;
            Object[] o = byModel.get(e.getMetaModelElement().getId());
            if (o != null) placeCaption(e, (String) o[1], ((com.vp.plugin.diagram.IConnectorUIModel) e).getPoints(), (Integer) o[3]);
        }
        b.log("seq done: " + msgs.size() + " messages, " + act.size() + " activations");
    }

    private IDiagramElement end(String key, Msg x, boolean target) {
        if (target && x.kind == CREATE) return ls.get(key);
        // like the template: the connector joins the LIFELINE shapes; the activation is linked in the model only
        if (act.get(key) != null) { if (target) x.m.setToActivation(act.get(key)); else x.m.setFromActivation(act.get(key)); }
        return ls.get(key);
    }

    static final java.awt.FontMetrics FM = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
            .getGraphics().getFontMetrics(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));

    /** VP centres captions on the line (or leaves them at 0,0); put each label above the arrow's middle, like the manual.
     *  Must run after all connectors exist: the first setter triggers VP's lazy caption layout, the second one wins. */
    private void placeCaption(IDiagramElement c, String text, Point[] pts, int kind) {
        if (text == null || text.isEmpty()) return;
        try {
            Object cap = c.getClass().getMethod("getCaptionUIModel").invoke(c);
            cap.getClass().getMethod("getX").invoke(cap);
            cap.getClass().getMethod("setY", int.class).invoke(cap, 0);
            int w = FM.stringWidth(text) + 16, h = 13, x, y;
            if (kind == SELF) { x = pts[1].x + 4; y = pts[1].y - h + 2; }
            else if (kind == ASYNC) { Point a = pts[0], z = pts[pts.length - 1]; x = Math.min(a.x, z.x) + 4; y = Math.max(a.y, z.y) + 2; }
            else {
                int x0 = Math.min(pts[0].x, pts[pts.length - 1].x), len = Math.abs(pts[0].x - pts[pts.length - 1].x);
                x = w <= len ? x0 + len / 2 - w / 2 : x0 + 2;
                y = pts[0].y - h - 2;
            }
            cap.getClass().getMethod("setBounds", int.class, int.class, int.class, int.class).invoke(cap, x, y, w, h);
            // without these two VP hides the caption once the diagram is saved / re-rendered
            cap.getClass().getMethod("setVisible", boolean.class).invoke(cap, true);
            c.setRequestResetCaption(true);
        } catch (Throwable t) { b.log("WARN caption " + text + ": " + t); }
    }

    private void draw(Msg x) {
        IDiagramElement fs = end(x.from, x, false), ts = end(x.to, x, true);
        double fx = cx.get(x.from), tx = cx.get(x.to);
        double hw = ACT_W / 2;
        Point[] pts;
        if (x.kind == SELF) {
            pts = new Point[]{new Point(sx(fx + hw), sx(x.y)), new Point(sx(fx + 35), sx(x.y)), new Point(sx(fx + 35), sx(x.y + 18)), new Point(sx(fx + hw), sx(x.y + 18))};
        } else if (x.kind == ASYNC) {
            double dir = fx < tx ? 1 : -1;
            pts = new Point[]{new Point(sx(fx + dir * hw), sx(x.y)), new Point(sx(tx - dir * hw), sx(x.y2))};
        } else if (x.kind == CREATE) {
            // the target header must sit on the message: VP attaches create messages to the header
            double headerX = ls.get(x.to).getX() / b.k;
            pts = new Point[]{new Point(sx(fx + (fx < tx ? hw : -hw)), sx(x.y)), new Point(sx(fx < tx ? headerX : headerX + this.hw.get(x.to)), sx(x.y))};
        } else {
            double dir = fx < tx ? 1 : -1;
            pts = new Point[]{new Point(sx(fx + dir * hw), sx(x.y)), new Point(sx(tx - dir * hw), sx(x.y))};
        }
        IDiagramElement c = b.dm.createConnector(d, x.m, fs, ts, pts);
        if (c == null) { b.log("FAIL message " + x.from + " -> " + x.to + " @" + x.y); return; }
        links.add(x);
        if (c instanceof IMessageUIModel) {
            IMessageUIModel mu = (IMessageUIModel) c;
            if (FLAG_NAME >= 0) mu.setShowMessageName(FLAG_NAME);
            if (FLAG_SIG >= 0) mu.setShowMessageOperationSignature(FLAG_SIG);   // the name carries the label
            if (FLAG_CONN >= 0) mu.setShowConnectorName(FLAG_CONN);
        }
        c.setRequestResetCaption(true);
        drawn.add(new Object[]{c, texts.containsKey(x.m) ? texts.get(x.m) : x.m.getName(), pts, x.kind});
    }

    /** Combined fragment; guards[i] (null for none) with operand boundaries ys[i]..ys[i+1]. */
    ICombinedFragment fragment(String operator, String[] covered, double x1, double x2, double[] ys, String[] guards, IMessage[][] msgs) {
        return fragment(null, operator, covered, x1, x2, ys, guards, msgs);
    }

    /** Operands of the last fragment, to nest fragments / refs into them or set loop bounds. */
    IInteractionOperand[] last;

    /** Fragment nested in an operand of an enclosing fragment (parent may be null). */
    ICombinedFragment fragment(IInteractionOperand parent, String operator, String[] covered, double x1, double x2, double[] ys, String[] guards, IMessage[][] msgs) {
        x1 += dx; x2 += dx;
        ICombinedFragment cf = b.f.createCombinedFragment();
        cf.setInteractionOperator(operator);
        if (covered == null) covered = ll.keySet().toArray(new String[0]);
        for (String c : covered) cf.addCoveredLifeLine(ll.get(c));
        if (parent != null) parent.addCombinedFragment(cf); else frame.addCombinedFragment(cf);
        last = new IInteractionOperand[guards.length];
        IDiagramElement cs = b.shape(d, cf, x1, ys[0], x2 - x1, ys[ys.length - 1] - ys[0]);
        for (int i = 0; i < guards.length; i++) {
            IInteractionOperand op = b.f.createInteractionOperand();
            if (guards[i] != null) {
                IInteractionConstraint g = b.f.createInteractionConstraint();
                g.setConstraint(guards[i]);
                op.setGuard(g);
            }
            if (msgs != null && i < msgs.length) for (IMessage m : msgs[i]) op.addMessage(m);
            cf.addOperand(op);
            last[i] = op;
            try {
                IDiagramElement os = b.shape(d, op, x1, ys[i] + (i == 0 ? 17 : 0), x2 - x1, ys[i + 1] - ys[i] - (i == 0 ? 17 : 0));
                if (nestOperands) cs.addChild((IShapeUIModel) os);   // template keeps operand shapes top-level
            } catch (Throwable t) { b.log("WARN operand shape: " + t); }
        }
        b.log("fragment " + operator + " shape " + B.ifaces(cs));
        return cf;
    }

    /** loop (min, max) on the last fragment's first operand. */
    void loopBounds(String min, String max) {
        // setting min/max on an attached guard NPEs inside VP: build a fresh one and attach it afterwards
        IInteractionConstraint old = last[0].getGuard();
        IInteractionConstraint g = b.f.createInteractionConstraint();
        if (old != null) g.setConstraint(old.getConstraint());
        try { g.setMinint(min); g.setMaxint(max); } catch (Throwable t) { b.log("WARN loop bounds: " + t); }
        last[0].setGuard(g);
    }

    /** "ref" interaction use pointing at another sequence diagram's frame. */
    void ref(String targetName, IModelElement targetOwner, String[] covered, double x1, double x2, double y1, double y2, IInteractionOperand into) {
        x1 += dx; x2 += dx;
        IFrame target = null;
        for (IDiagramUIModel dd : b.project.toDiagramArray())
            if (IDiagramTypeConstants.DIAGRAM_TYPE_INTERACTION_DIAGRAM.equals(dd.getType()) && targetName.equals(dd.getName()))
                target = ((IFramedInteractionDiagramUIModel) dd).getRootFrame(true);
        IInteractionOccurrence io = b.f.createInteractionOccurrence();
        if (target != null) io.setRefersTo(target);
        for (String c : covered) io.addCoveredLifeLine(ll.get(c));
        if (into != null) into.addInteractionOccurrence(io); else frame.addInteractionOccurrence(io);
        b.shape(d, io, x1, y1, x2 - x1, y2 - y1);
    }
}
