package ru.urfu.lab2;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeTypeConstants;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.shape.IActivityPartitionHeaderUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2CompartmentUIModel;
import com.vp.plugin.diagram.shape.IActivitySwimlane2NewUIModel;
import com.vp.plugin.model.*;

import java.util.HashMap;
import java.util.Map;

/** Exercises 3.1–3.4: use case diagram (refined, fig. 3.2.1) and activity diagrams (fig. 3.4.1, 3.4.2). */
public class Stage1Action implements VPActionController {

    static final String SYSTEM = "Система регистрации на курсы";

    public void update(VPAction action) {}

    public void performAction(VPAction action) {
        final B b = new B();
        if (b.find("System", SYSTEM) != null) {
            b.app.getViewManager().showMessage("Stage 1 already built in this project. Close without saving and reopen myProject.vpp to rebuild.", "Lab2");
            return;
        }
        final Map<String, IModelElement> m = new HashMap<String, IModelElement>();
        b.step("use case diagram", () -> useCases(b, m));
        b.step("activity: Войти в систему", () -> login(b, m));
        b.step("activity: Зарегистрироваться на курсы", () -> register(b, m));
        b.finish("Stage 1 (requirements)");
    }

    // ---------------- 3.1–3.2 ----------------
    static final String[][] ACTORS = {
            // name, x, y (fig. 3.2.1 units)
            {"Пользователь", "5", "45"},
            {"Профессор", "75", "170"},
            {"Студент", "40", "315"},
            {"Регистратор", "40", "455"},
            {"Расчётная система", "705", "170"},
    };
    static final String[][] UCS = {
            // name, level, x, y
            {"Войти в систему", "fish", "425", "40"},
            {"Выбрать читаемые курсы", "sea", "172", "105"},
            {"Поставить оценки", "sea", "415", "140"},
            {"Зарегистрироваться на курсы", "sea", "172", "225"},
            {"Посмотреть табель", "sea", "415", "260"},
            {"Закрыть регистрацию", "sea", "415", "330"},
            {"CRUD данных о профессорах", "sea", "172", "365"},
            {"Открыть регистрацию", "sea", "415", "400"},
            {"CRUD данных о студентах", "sea", "172", "470"},
            {"CRUD данных о курсах", "sea", "415", "500"},
    };
    static final String[][] LINKS = {
            {"Пользователь", "Войти в систему"},
            {"Профессор", "Выбрать читаемые курсы"},
            {"Профессор", "Поставить оценки"},
            {"Студент", "Зарегистрироваться на курсы"},
            {"Студент", "Посмотреть табель"},
            {"Регистратор", "Закрыть регистрацию"},
            {"Регистратор", "CRUD данных о профессорах"},
            {"Регистратор", "Открыть регистрацию"},
            {"Регистратор", "CRUD данных о студентах"},
            {"Регистратор", "CRUD данных о курсах"},
            {"Закрыть регистрацию", "Расчётная система"},
    };
    static final String[] CHILDREN = {"Профессор", "Студент", "Регистратор"};

    /** Model only: system, actors, use cases, associations, generalizations. */
    private void useCases(B b, Map<String, IModelElement> m) {
        IModelElement ucModel = b.need("Model", "Use Case Model");
        ISystem sys = b.named(b.f.createSystem(), SYSTEM);
        sys.addStereotype("subject");
        b.adopt(ucModel, sys);
        m.put(SYSTEM, sys);
        for (String[] a : ACTORS) {
            IActor act = b.named(b.f.createActor(), a[0]);
            b.adopt(ucModel, act);
            m.put(a[0], act);
        }
        ((IActor) m.get("Пользователь")).setAbstract(true);
        for (String[] u : UCS) {
            IUseCase uc = b.named(b.f.createUseCase(), u[0]);
            uc.addStereotype(u[1] + " level usecase");
            b.adopt(ucModel, uc);
            m.put(u[0], uc);
        }
        for (String[] l : LINKS) b.assoc(m.get(l[0]), m.get(l[1]));
        for (String c : CHILDREN) b.gen(m.get("Пользователь"), m.get(c));
        drawMain(b, m);
    }

    /** Collect the stage-1 use case model elements that already exist in the project. */
    static Map<String, IModelElement> existing(B b) {
        Map<String, IModelElement> m = new HashMap<String, IModelElement>();
        m.put(SYSTEM, b.need("System", SYSTEM));
        for (String[] a : ACTORS) m.put(a[0], b.need("Actor", a[0]));
        for (String[] u : UCS) m.put(u[0], b.need("UseCase", u[0]));
        return m;
    }

    private static IRelationship rel(B b, String type, IModelElement from, IModelElement to) {
        for (IModelElement e : b.project.toAllLevelModelElementArray(type)) {
            IRelationship r = (IRelationship) e;
            if (r.getFrom() != null && r.getTo() != null
                    && r.getFrom().getId().equals(from.getId()) && r.getTo().getId().equals(to.getId())) return r;
        }
        throw new IllegalStateException("No " + type + " " + from.getName() + " -> " + to.getName());
    }

    /** Separate step: add the three generalizations to the already drawn Main (shape type first, model bound after). */
    static void drawGeneralizations(B b, Map<String, IModelElement> m) {
        IModelElement ucModel = b.need("Model", "Use Case Model");
        IDiagramUIModel d = b.diagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM, "Main", ucModel);
        if (d == null) throw new IllegalStateException("Main not found");
        b.dm.openDiagram(d);
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        for (IDiagramElement e : d.toDiagramElementArray()) {
            IModelElement me = e.getMetaModelElement();
            if (me != null && me instanceof IActor) s.put(me.getName(), e);
        }
        IShapeUIModel ps = (IShapeUIModel) s.get("Пользователь");
        b.k = 1.3;
        for (String child : CHILDREN) {
            IRelationship g = rel(b, "Generalization", m.get("Пользователь"), m.get(child));
            IShapeUIModel cs = (IShapeUIModel) s.get(child);
            IDiagramElement c = b.dm.createConnector(d, IShapeTypeConstants.SHAPE_TYPE_GENERALIZATION, ps, cs, null);
            if (c == null) { b.log("FAIL generalization " + child + ": no connector"); continue; }
            c.setMetaModelElement(g);
            c.setRequestResetCaption(true);
            route(b, (com.vp.plugin.diagram.IConnectorUIModel) c, GEN_ROUTES.get(child));
            b.log("generalization " + child + " connector=" + c.getX() + "," + c.getY() + " " + c.getWidth() + "x" + c.getHeight());
        }
    }

    /** Three separate vertical generalization lines into Пользователь (fig. 3.2.1); from = general. */
    static final Map<String, double[]> GEN_ROUTES = new HashMap<String, double[]>();
    static {
        GEN_ROUTES.put("Профессор", new double[]{30, 105, 30, 195, 75, 195});
        GEN_ROUTES.put("Студент", new double[]{20, 105, 20, 345, 40, 345});
        GEN_ROUTES.put("Регистратор", new double[]{10, 105, 10, 485, 40, 485});
    }

    static final Map<String, double[]> ROUTES = new HashMap<String, double[]>();
    static {
        ROUTES.put("Пользователь>Войти в систему", new double[]{35, 74, 425, 74});
        ROUTES.put("Профессор>Выбрать читаемые курсы", new double[]{105, 200, 200, 200, 200, 166});
        ROUTES.put("Профессор>Поставить оценки", new double[]{105, 214, 440, 214, 440, 200});
        ROUTES.put("Студент>Зарегистрироваться на курсы", new double[]{70, 325, 85, 325, 85, 259, 172, 259});
        ROUTES.put("Студент>Посмотреть табель", new double[]{70, 335, 395, 335, 395, 294, 415, 294});
        ROUTES.put("Регистратор>Закрыть регистрацию", new double[]{70, 462, 110, 462, 110, 364, 415, 364});
        ROUTES.put("Регистратор>CRUD данных о профессорах", new double[]{70, 468, 125, 468, 125, 399, 172, 399});
        ROUTES.put("Регистратор>Открыть регистрацию", new double[]{70, 474, 140, 474, 140, 438, 418, 438});
        ROUTES.put("Регистратор>CRUD данных о студентах", new double[]{70, 490, 150, 490, 150, 504, 172, 504});
        ROUTES.put("Регистратор>CRUD данных о курсах", new double[]{55, 515, 55, 585, 532, 585, 532, 568});
        ROUTES.put("Закрыть регистрацию>Расчётная система", new double[]{650, 364, 720, 364, 720, 230});
    }

    static void route(B b, com.vp.plugin.diagram.IConnectorUIModel c, double[] xy) {
        c.clearPoints();
        for (int i = 0; i + 1 < xy.length; i += 2) c.addPoint(new java.awt.Point((int) Math.round(xy[i] * b.k), (int) Math.round(xy[i + 1] * b.k)));
    }

    /** Diagram only: (re)creates "Main" and draws the existing model elements on it (fig. 3.2.1). */
    static void drawMain(B b, Map<String, IModelElement> m) {
        IModelElement ucModel = b.need("Model", "Use Case Model");
        for (IDiagramUIModel old : b.project.toDiagramArray()) {
            if (IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM.equals(old.getType()) && "Main".equals(old.getName())
                    && old.getParentModel() != null && ucModel.getId().equals(old.getParentModel().getId())) {
                old.delete();
            }
        }
        IDiagramUIModel d = b.newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM, "Main", ucModel);
        b.dm.openDiagram(d);   // draw on an open canvas: VP lays out captions/connectors correctly then
        b.k = 1.3;

        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        IDiagramElement sysS = b.shape(d, m.get(SYSTEM), 150, 5, 515, 580);
        for (String[] a : ACTORS)
            s.put(a[0], b.shape(d, m.get(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), 30, 60));
        for (String[] u : UCS) {
            IDiagramElement us = b.shape(d, m.get(u[0]), Double.parseDouble(u[2]), Double.parseDouble(u[3]), 235, 68);
            b.inside(sysS, us);
            s.put(u[0], us);
        }
        for (String[] l : LINKS) {
            IDiagramElement c = b.tryConnect(d, rel(b, "Association", m.get(l[0]), m.get(l[1])), s.get(l[0]), s.get(l[1]));
            double[] r = ROUTES.get(l[0] + ">" + l[1]);
            if (c instanceof com.vp.plugin.diagram.IConnectorUIModel && r != null) route(b, (com.vp.plugin.diagram.IConnectorUIModel) c, r);
        }
        for (IDiagramElement e : s.values())
            if (e instanceof IShapeUIModel) b.log("shape '" + e.getMetaModelElement().getName() + "' at " + e.getX() + "," + e.getY());
    }

    // ---------------- 3.4 helpers ----------------
    private static class Act {
        /** Vertical offset (figure units) so the diagram frame label does not cover partition headers. */
        static final double OY = 22;
        final B b; final IDiagramUIModel d; final IModelElement owner; final IDiagramElement lane;
        final IActivityPartition[] parts;
        final IDiagramElement[] cells;
        final Map<String, IModelElement> m = new HashMap<String, IModelElement>();
        final Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();

        /** widths: width of each vertical partition (figure units); h: total height. */
        Act(B b, IModelElement owner, String name, String[] partNames, IModelElement[] represents, double[] widths, double h) {
            this.b = b; this.owner = owner;
            d = b.newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ACTIVITY_DIAGRAM, name, owner);
            IActivitySwimlane2 sl = b.f.createActivitySwimlane2();
            sl.setName("");
            b.adopt(owner, sl);
            parts = new IActivityPartition[partNames.length];
            cells = new IDiagramElement[partNames.length];
            double w = 0;
            for (int i = 0; i < partNames.length; i++) {
                parts[i] = b.named(b.f.createActivityPartition(), partNames[i]);
                parts[i].setRepresents(represents[i]);
                sl.addVerticalPartition(parts[i]);
                w += widths[i];
            }
            lane = b.shape(d, sl, 5, 5 + OY, w, h);
            b.log("swimlane shape " + B.ifaces(lane));
            try { lanes(widths, h); }
            catch (Throwable t) { b.log("WARN lanes: " + t); }
        }

        private void lanes(double[] widths, double h) {
            if (!(lane instanceof IActivitySwimlane2NewUIModel)) { b.log("WARN lanes: not a NewUIModel"); return; }
            IActivitySwimlane2NewUIModel sw = (IActivitySwimlane2NewUIModel) lane;
            String[] pIds = new String[parts.length];
            String[] cIds = new String[parts.length];
            double x = 5;
            for (int i = 0; i < parts.length; i++) {
                pIds[i] = parts[i].getId();
                IDiagramElement hdr = b.shapeOfType(d, IShapeTypeConstants.SHAPE_TYPE_ACTIVITY_PARTITION_HEADER, parts[i], x, 5 + OY, widths[i], 20);
                ((IActivityPartitionHeaderUIModel) hdr).setSwimlane(sw);
                ((IActivityPartitionHeaderUIModel) hdr).setHorizontal(false);
                sw.addChild((IShapeUIModel) hdr);
                // VP links partitions and cells by the HEADER SHAPE id, not the partition model id
                pIds[i] = hdr.getId();
                IDiagramElement cell = b.shapeOfType(d, IShapeTypeConstants.SHAPE_TYPE_ACTIVITY_SWIMLANE2_COMPARTMENT, null, x, 25 + OY, widths[i], h - 20);
                ((IActivitySwimlane2CompartmentUIModel) cell).setVerticalPartitionId(hdr.getId());
                sw.addChild((IShapeUIModel) cell);
                cells[i] = cell;
                cIds[i] = cell.getId();
                x += widths[i];
            }
            sw.setVerticalPartitionIds(pIds);
            sw.setCompartmentIds(cIds);
        }

        IModelElement node(String key, IModelElement e, int part, double x, double y, double w, double h) {
            b.adopt(owner, e);
            try { parts[part].addContainedElement(e); } catch (Throwable t) { b.log("WARN partition " + key + ": " + t); }
            IDiagramElement sh = b.shape(d, e, x, y + OY, w, h);
            b.inside(cells[part] != null ? cells[part] : lane, sh);
            m.put(key, e); s.put(key, sh);
            return e;
        }

        IModelElement action(String name, int part, double x, double y, double w, double h) {
            return node(name, b.named(b.f.createActivityAction(), name), part, x, y, w, h);
        }

        /** Action of type Call Behavior Action that invokes the given activity. */
        IModelElement call(String name, IActivity behavior, int part, double x, double y, double w, double h) {
            IActivityAction a = b.named(b.f.createActivityAction(), name);
            ICallBehaviorAction t = b.f.createCallBehaviorAction();
            t.setBehavior(behavior);
            a.setActionType(t);
            return node(name, a, part, x, y, w, h);
        }

        /** xy: optional route as x1,y1,x2,y2,... in figure coordinates (first/last = attach points). */
        final Map<String, IDiagramElement> flowTo = new HashMap<String, IDiagramElement>();

        /** Put the caption of the flow ending at 'to' at figure point (x, y) (top-left), after VP's lazy layout. */
        void caption(String to, double x, double y) {
            IDiagramElement c = flowTo.get(to);
            if (c == null) return;
            for (IDiagramElement e : d.toDiagramElementArray()) if (e.getId().equals(c.getId())) c = e;
            try {
                Object cap = c.getClass().getMethod("getCaptionUIModel").invoke(c);
                Class<?> k = cap.getClass();
                int w = (Integer) k.getMethod("getWidth").invoke(cap), h = (Integer) k.getMethod("getHeight").invoke(cap);
                k.getMethod("getX").invoke(cap);
                k.getMethod("setY", int.class).invoke(cap, 0);
                k.getMethod("setBounds", int.class, int.class, int.class, int.class).invoke(cap,
                        (int) Math.round(x * b.k), (int) Math.round((y + OY) * b.k), Math.max(w, 230), Math.max(h, 14));
                k.getMethod("setVisible", boolean.class).invoke(cap, true);
                c.setRequestResetCaption(true);
            } catch (Throwable t) { b.log("WARN caption " + to + ": " + t); }
        }

        void flow(String from, String to, String guard, double... xy) {
            IControlFlow c = b.flow(m.get(from), m.get(to), guard);
            if (xy.length < 4) { b.tryConnect(d, c, s.get(from), s.get(to)); return; }
            java.awt.Point[] pts = new java.awt.Point[xy.length / 2];
            for (int i = 0; i < pts.length; i++)
                pts[i] = new java.awt.Point((int) Math.round(xy[2 * i] * b.k), (int) Math.round((xy[2 * i + 1] + OY) * b.k));
            IDiagramElement con = b.dm.createConnector(d, c, s.get(from), s.get(to), pts);
            if (con == null) b.log("FAIL routed flow " + from + " -> " + to);
            else { con.setRequestResetCaption(true); if (guard != null) flowTo.put(to, con); }
        }
    }

    // ---------------- fig. 3.4.1 ----------------
    private void login(B b, Map<String, IModelElement> m) {
        b.k = 1.2;
        Act a = new Act(b, m.get("Войти в систему"), "Войти в систему",
                new String[]{"Пользователь", SYSTEM},
                new IModelElement[]{m.get("Пользователь"), m.get(SYSTEM)}, new double[]{270, 505}, 405);
        a.node("start", b.f.createInitialNode(), 0, 20, 58, 25, 25);
        a.action("запустить приложение", 0, 80, 40, 185, 60);
        a.action("запросить имя и пароль", 1, 300, 40, 195, 60);
        a.action("ввести имя и пароль", 0, 80, 140, 185, 60);
        a.action("проверить имя и пароль", 1, 300, 140, 195, 60);
        a.node("d1", b.f.createDecisionNode(), 1, 615, 198, 25, 36);
        a.action("сообщить об ошибке и завершить работу приложения", 1, 300, 262, 250, 62);
        a.action("определить тип пользователя и вывести главное меню", 1, 518, 340, 255, 60);
        a.node("неуспех", b.named(b.f.createActivityFinalNode(), "неуспех"), 1, 373, 353, 28, 28);
        a.node("успех", b.named(b.f.createActivityFinalNode(), "успех"), 1, 462, 353, 28, 28);

        a.flow("start", "запустить приложение", null);
        a.flow("запустить приложение", "запросить имя и пароль", null);
        a.flow("запросить имя и пароль", "ввести имя и пароль", null, 397, 100, 397, 120, 172, 120, 172, 140);
        a.flow("ввести имя и пароль", "проверить имя и пароль", null);
        a.flow("проверить имя и пароль", "d1", null, 495, 170, 627, 170, 627, 198);
        a.flow("d1", "сообщить об ошибке и завершить работу приложения", "else", 615, 216, 425, 216, 425, 262);
        a.flow("d1", "определить тип пользователя и вывести главное меню", "имя и пароль верны", 627, 234, 627, 340);
        a.flow("сообщить об ошибке и завершить работу приложения", "неуспех", null);
        a.flow("определить тип пользователя и вывести главное меню", "успех", null);
        b.dm.openDiagram(a.d);
    }

    // ---------------- fig. 3.4.2 ----------------
    private void register(B b, Map<String, IModelElement> m) {
        b.k = 1.4;
        IModelElement uc = m.get("Зарегистрироваться на курсы");
        Act a = new Act(b, uc, "Зарегистрироваться на курсы",
                new String[]{"Студент", SYSTEM},
                new IModelElement[]{m.get("Студент"), m.get(SYSTEM)}, new double[]{202, 383}, 428);
        a.node("start", b.f.createInitialNode(), 0, 42, 58, 22, 22);
        a.action("Запросить регистрацию", 0, 72, 45, 92, 45);
        a.action("Проверить состояние регистрации", 1, 255, 35, 135, 55);
        a.node("d1", b.f.createDecisionNode(), 1, 318, 98, 22, 32);
        a.node("m1", b.f.createMergeNode(), 1, 232, 150, 22, 32);
        a.action("Сообщить об ошибке", 1, 283, 145, 92, 45);
        a.node("неуспех", b.named(b.f.createActivityFinalNode(), "неуспех"), 1, 395, 153, 26, 26);
        a.action("Запросить действие", 1, 265, 200, 125, 45);
        a.action("Выбрать действие", 0, 72, 190, 92, 42);
        a.node("d2", b.f.createDecisionNode(), 0, 45, 300, 22, 40);

        String[][] calls = {
                {"Создать учебный план", "233", "262", "создание"},
                {"Обновить учебный план", "380", "297", "обновление"},
                {"Удалить учебный план", "233", "335", "удаление"},
                {"Утвердить учебный план", "380", "368", "утверждение"},
        };
        for (String[] c : calls) {
            IActivity behavior = b.named(b.f.createActivity(), c[0]);
            b.adopt(uc, behavior);
            a.call(c[0], behavior, 1, Double.parseDouble(c[1]), Double.parseDouble(c[2]), 150, 32);
        }
        a.node("успех", b.named(b.f.createActivityFinalNode(), "успех"), 1, 540, 345, 26, 26);

        a.flow("start", "Запросить регистрацию", null);
        a.flow("Запросить регистрацию", "Проверить состояние регистрации", null);
        a.flow("Проверить состояние регистрации", "d1", null);
        a.flow("d1", "m1", "регистрация закрыта", 318, 114, 243, 114, 243, 150);
        a.flow("d1", "Запросить действие", "else", 340, 114, 420, 114, 420, 222, 390, 222);
        a.flow("m1", "Сообщить об ошибке", null, 254, 166, 283, 166);
        a.flow("Сообщить об ошибке", "неуспех", null);
        a.flow("Запросить действие", "Выбрать действие", null, 265, 222, 164, 222);
        a.flow("Выбрать действие", "d2", null, 118, 232, 118, 280, 56, 280, 56, 300);
        // d2 -> call behavior actions (routes keep guard labels apart, like fig. 3.4.2)
        a.flow("d2", calls[0][0], "выбрано создание и оно возможно", 67, 320, 85, 320, 85, 278, 233, 278);
        a.flow("d2", calls[1][0], "выбрано обновление и оно возможно", 67, 320, 380, 320);
        a.flow("d2", calls[2][0], "выбрано удаление и оно возможно", 67, 320, 85, 320, 85, 351, 233, 351);
        a.flow("d2", calls[3][0], "выбрано утверждение и оно возможно", 56, 340, 56, 384, 380, 384);
        a.flow(calls[0][0], "успех", null, 383, 278, 553, 278, 553, 345);
        a.flow(calls[1][0], "успех", null, 530, 313, 548, 313, 548, 346);
        a.flow(calls[2][0], "успех", null, 383, 351, 540, 351);
        a.flow(calls[3][0], "успех", null, 530, 384, 553, 384, 553, 371);
        a.flow("d2", "m1", "else", 45, 320, 15, 320, 15, 166, 232, 166);
        b.dm.openDiagram(a.d);
        // guard labels right above their own segments; after openDiagram, which re-lays captions out
        a.caption(calls[0][0], 90, 264);
        a.caption(calls[1][0], 90, 306);
        a.caption(calls[2][0], 90, 337);
        a.caption(calls[3][0], 62, 370);
    }
}
