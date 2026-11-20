package ru.urfu.lab2;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.shape.IClassUIModel;
import com.vp.plugin.model.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exercises 4.1–4.4 (analysis model): Use Case Realizations package, collaborations + composite structure
 * diagram (fig. 4.1.2), Key Abstractions (4.1.3), analysis classes with operations, VOPC diagrams
 * (4.2.3, 4.3.6, 4.4.3). Sequence diagrams are built by Stage2SeqAction.
 */
public class Stage2Action implements VPActionController {

    public void update(VPAction action) {}

    public void performAction(VPAction action) {
        final B b = new B();
        if (b.find("Collaboration", "RegisterForCourses") != null) {
            b.app.getViewManager().showMessage("Stage 2 already built in this project.", "Lab2");
            return;
        }
        final Stage2Action s = this;
        b.step("classes", () -> s.classes(b));
        b.step("associations", () -> s.associations(b));
        b.step("realizations package + collaborations", () -> s.realizations(b));
        b.step("diagram Key Abstractions", () -> s.keyAbstractions(b));
        b.step("diagram VOPC Login", () -> s.vopcLogin(b));
        b.step("diagram VOPC RegisterForCourses", () -> s.vopcRegister(b));
        b.step("diagram VOPC CloseRegistration", () -> s.vopcClose(b));
        b.finish("Stage 2 (analysis: classes)");
    }

    // ===================================================== classes
    final Map<String, IClass> cls = new LinkedHashMap<String, IClass>();
    IModelElement analysis;

    /** name, stereotype, members... ("-attr:type[mult]{id}", "/derived:type", "+op(p:T[m]):Ret[]"). */
    static final String[][] CLASSES = {
            {"DayOfWeek", "enumeration", "SU", "MO", "TU", "WE", "TH", "FR", "SA"},
            {"UserType", "enumeration", "STUDENT", "PROFESSOR", "REGISTRAR"},
            {"Course", "entity", "-code:string{id}", "-description:string", "-duration:byte"},
            {"CourseOffering", "entity", "-number:long", "-day:DayOfWeek", "-pair:byte", "-semester:byte", "/numStudents:byte",
                    "+removeSchedule(sch:Schedule)", "+toJSON():string"},
            {"Professor", "entity", "-name:string{id}", "-phones:string[0..2]", "-academicDegree"},
            {"Schedule", "entity", "-semester:byte", "+delete()", "+getCourseOfferingList():CourseOffering[]"},
            {"User", "entity", "-login:string{id}", "-passwordHash:long[2]", "-type:UserType",
                    "+checkPassword(pswrd:string):boolean", "+getType():UserType"},
            {"Student", "entity", "-address:string", "-id:long{id}", "-name:string", "-phones:string[0..2]",
                    "+createSchedule(semester:byte,primary:CourseOffering[4],alt:CourseOffering[2])",
                    "+deleteSchedule(semester:byte)", "+getAddress():string", "+getName():string",
                    "+getScheduleCourses(semester:byte)", "+hasScheduleForSemester(semester:byte):boolean"},
            {"FullTimeStudent", "entity", "-graduateDate:Date"},
            {"PartTimeStudent", "entity", "-maxNumCourses:byte"},
            {"LoginForm", "boundary", "+login()", "+displayError()", "+close()"},
            {"MainMenuForm", "boundary", "+displayError()", "+open(user:User)", "+registerForCourses()"},
            {"RegistrationForm", "boundary", "+confirmDeleteSchedule()", "+createSchedule(semester:byte)",
                    "+deleteSchedule(semester:byte)", "+displayBlankSchedule()", "+displayCourseString()",
                    "+displayMessage(msg:string)", "+requestDeleteScheduleConfirmation()", "+selectAltCourse(nums:long[2])",
                    "+selectPrimaryCourse(nums:long[4])", "+submitSelectedCourses(semester:byte,primary:long[4],alt:long[2])"},
            {"RegistrarForm", "boundary"},
            {"BillingSystem", "boundary", "+finishTransferData()",
                    "+sendStudentData(name:string,address:string,cOff:CourseOffering[])", "+startTransferData()"},
            {"LoginController", "control", "+login()"},
            {"DataAccess", "control", "+getCourseOfferingList(semester:byte):CourseOffering[]", "+getStudentByUser(u:User):Student",
                    "+getStudentList(semester:byte):Student[]", "+getUserByLogin(l:string):User",
                    "+updateCourseOfferings(cOffs:CourseOffering[]):boolean", "+updateStudent(st:Student):boolean"},
            {"RegistrationController", "control", "+confirmDeleteSchedule()", "+createSchedule(semester:byte)",
                    "+deleteSchedule(semester:byte)", "+getCourseOfferingList(semester:byte):CourseOffering[]",
                    "+isRegistrationOpened(semester:byte):boolean", "+registerForCourses(u:User)",
                    "+submitSelectedCourses(semester:byte,primary:CourseOffering[4],alt:CourseOffering[2])"},
            {"CloseRegistrationController", "control"},
    };

    void classes(B b) {
        analysis = b.need("Model", "Analysis Model");
        for (String[] c : CLASSES) {                       // pass 1: create all, so types can reference each other
            IClass k = b.named(b.f.createClass(), c[0]);
            k.addStereotype(c[1]);
            b.adopt(analysis, k);
            cls.put(c[0], k);
        }
        for (String[] c : CLASSES) {                       // pass 2: members
            IClass k = cls.get(c[0]);
            for (int i = 2; i < c.length; i++) {
                String m = c[i];
                if ("enumeration".equals(c[1])) {
                    IEnumerationLiteral lit = k.createEnumerationLiteral();
                    lit.setName(m);
                } else if (m.startsWith("+")) {
                    operation(k, m.substring(1));
                } else {
                    attribute(k, m);
                }
            }
        }
        cls.get("Student").setAbstract(true);
        b.log("classes: " + cls.size());
    }

    private void attribute(IClass k, String spec) {
        boolean derived = spec.startsWith("/");
        String s = spec.substring(1);
        boolean id = s.endsWith("{id}");
        if (id) s = s.substring(0, s.length() - 4);
        IAttribute a = k.createAttribute();
        a.setVisibility(IAttribute.VISIBILITY_PRIVATE);
        typed(s, a, null);
        if (derived) a.setDerived(true);
        if (id) a.setIsID(true);
    }

    private void operation(IClass k, String spec) {
        IOperation o = k.createOperation();
        o.setVisibility(IOperation.VISIBILITY_PUBLIC);
        int lp = spec.indexOf('('), rp = spec.lastIndexOf(')');
        o.setName(spec.substring(0, lp));
        String params = spec.substring(lp + 1, rp).trim();
        if (!params.isEmpty()) {
            for (String p : params.split(",")) {
                IParameter par = b0().f.createParameter();
                typed(p.trim(), null, par);
                o.addParameter(par);
            }
        }
        String ret = spec.substring(rp + 1);
        if (ret.startsWith(":")) {
            String t = ret.substring(1);
            if (t.endsWith("[]")) { o.setTypeModifier("[]"); t = t.substring(0, t.length() - 2); }
            IClass tc = cls.get(t);
            if (tc != null) o.setReturnType(tc); else o.setReturnType(t);
        }
    }

    private B bRef;
    private B b0() { if (bRef == null) bRef = new B(); return bRef; }

    /** "name:Type[mult]" / "name:Type[]" / "name" into an attribute or a parameter. */
    private void typed(String s, IAttribute a, IParameter p) {
        String name = s, type = null, mult = null, mod = null;
        int c = s.indexOf(':');
        if (c >= 0) { name = s.substring(0, c); type = s.substring(c + 1); }
        if (type != null && type.endsWith("]")) {
            int lb = type.indexOf('[');
            String inside = type.substring(lb + 1, type.length() - 1);
            type = type.substring(0, lb);
            if (inside.isEmpty()) mod = "[]"; else mult = inside;
        }
        IClass tc = type == null ? null : cls.get(type);
        if (a != null) {
            // VP shows "{unique}" for the default isUnique=true; the manual's figures don't
            try {
                IMultiplicity md = a.getMultiplicityDetail();
                if (md == null) { md = b0().f.createMultiplicity(); a.setMultiplicityDetail(md); }
                md.setUnique(false);
            } catch (Throwable t) { b0().log("WARN unique " + name + ": " + t); }
            a.setName(name);
            if (tc != null) a.setType(tc); else if (type != null) a.setType(type);
            if (mult != null) a.setMultiplicity(mult);
            if (mod != null) a.setTypeModifier(mod);
        } else {
            p.setName(name);
            if (tc != null) p.setType(tc); else if (type != null) p.setType(type);
            // class shapes don't print parameter multiplicities: carry "[4]" as the type modifier instead
            if (mult != null) p.setTypeModifier("[" + mult + "]");
            else if (mod != null) p.setTypeModifier(mod);
        }
    }

    // ===================================================== associations
    final Map<String, IModelElement> rel = new HashMap<String, IModelElement>();

    /** key, from, fromMult, fromRole, to, toMult, toRole, nav(none|to|both), composite-at-from(y/n). */
    static final String[][] ASSOCS = {
            {"K1", "Course", "*", "prerequisitesOf", "Course", "*", "hasPrerequisites", "none", "n"},
            {"K2", "Course", "1", "", "CourseOffering", "*", "", "none", "n"},
            {"K3", "CourseOffering", "*", "", "Professor", "0..1", "", "none", "n"},
            {"K4", "CourseOffering", "0..4", "primary", "Schedule", "*", "", "none", "n"},
            {"K5", "CourseOffering", "0..2", "alternative", "Schedule", "*", "", "none", "n"},
            {"K6", "User", "1", "", "Professor", "0..1", "", "none", "y"},
            {"K7", "User", "1", "", "Student", "0..1", "", "none", "y"},
            {"K8", "Student", "1", "", "Schedule", "*", "", "none", "y"},
            {"L1", "LoginForm", "", "", "LoginController", "", "", "both", "n"},
            {"L2", "LoginController", "", "", "DataAccess", "", "", "to", "n"},
            {"L3", "LoginController", "", "", "User", "", "", "to", "n"},
            {"L4", "LoginController", "", "", "MainMenuForm", "", "", "to", "n"},
            {"L5", "MainMenuForm", "", "", "User", "", "", "to", "n"},
            {"L6", "DataAccess", "", "", "User", "", "", "to", "n"},
            {"R1", "MainMenuForm", "1", "", "RegistrationForm", "0..1", "", "to", "n"},
            {"R2", "MainMenuForm", "1", "", "RegistrationController", "0..1", "", "to", "n"},
            {"R3", "RegistrationForm", "1", "", "RegistrationController", "1", "", "to", "n"},
            {"R4", "RegistrationForm", "0..1", "", "CourseOffering", "*", "", "to", "n"},
            {"R5", "RegistrationController", "0..1", "", "CourseOffering", "*", "", "to", "n"},
            {"R6", "RegistrationController", "0..1", "", "Student", "0..1", "/curStud", "to", "n"},
            {"R7", "RegistrationController", "0..1", "", "DataAccess", "0..1", "", "to", "n"},
            {"R8", "DataAccess", "", "", "Student", "", "", "to", "n"},
            {"C1", "MainMenuForm", "1", "", "RegistrarForm", "0..1", "", "to", "n"},
            {"C2", "MainMenuForm", "1", "", "CloseRegistrationController", "0..1", "", "to", "n"},
            {"C3", "CloseRegistrationController", "0..1", "", "BillingSystem", "1", "", "to", "n"},
            {"C4", "CloseRegistrationController", "0..1", "", "DataAccess", "1", "", "to", "n"},
            {"C5", "CloseRegistrationController", "0..1", "", "Student", "*", "", "to", "n"},
            {"C7", "BillingSystem", "0..1", "", "CourseOffering", "*", "", "to", "n"},
    };

    void associations(B b) {
        for (String[] a : ASSOCS) {
            IAssociation as = b.f.createAssociation();
            as.setFrom(cls.get(a[1]));
            as.setTo(cls.get(a[4]));
            IAssociationEnd fe = (IAssociationEnd) as.getFromEnd(), te = (IAssociationEnd) as.getToEnd();
            if (!a[2].isEmpty()) fe.setMultiplicity(a[2]);
            if (!a[3].isEmpty()) fe.setName(a[3]);
            if (!a[5].isEmpty()) te.setMultiplicity(a[5]);
            if (!a[6].isEmpty()) {   // "/role" = derived end
                te.setName(a[6].replace("/", ""));
                if (a[6].startsWith("/")) te.setDerived(true);
            }
            int un = IAssociationEnd.NAVIGABLE_NAV_UNSPECIFIED, nv = IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE;
            fe.setNavigable("both".equals(a[7]) ? nv : un);
            te.setNavigable("none".equals(a[7]) ? un : nv);
            if ("y".equals(a[8])) fe.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_COMPOSITED);
            rel.put(a[0], as);
        }
        IDependency d1 = b.f.createDependency();
        d1.setFrom(cls.get("CourseOffering")); d1.setTo(cls.get("DayOfWeek"));
        rel.put("D1", d1);
        IDependency d2 = b.f.createDependency();
        d2.setFrom(cls.get("User")); d2.setTo(cls.get("UserType"));
        rel.put("D2", d2);

        IGeneralizationSet set = b.f.createGeneralizationSet();
        set.setName("{disjoint, complete}");
        set.setDisjoint(true);
        set.setCovering(true);
        b.adopt(analysis, set);
        for (String sub : new String[]{"FullTimeStudent", "PartTimeStudent"}) {
            IGeneralization g = b.gen(cls.get("Student"), cls.get(sub));
            g.setGeneralizationSet(set);
            set.addGeneralization(g);
            rel.put("G-" + sub, g);
        }
    }

    // ===================================================== 4.1: realizations
    final Map<String, ICollaboration> collab = new HashMap<String, ICollaboration>();
    IModelElement ucrPackage;

    void realizations(B b) {
        IPackage p = b.named(b.f.createPackage(), "Use Case Realizations");
        b.adopt(analysis, p);
        ucrPackage = p;
        String[][] map = {
                {"Login", "Войти в систему"},
                {"RegisterForCourses", "Зарегистрироваться на курсы"},
                {"CloseRegistration", "Закрыть регистрацию"}};
        for (String[] r : map) {
            ICollaboration c = b.named(b.f.createCollaboration(), r[0]);
            c.addStereotype("usecase realization");
            b.adopt(p, c);
            collab.put(r[0], c);
            IRealization re = b.f.createRealization();
            // as for generalizations, VP wants the supplier (use case) as from: the triangle then sits at the use case
            re.setFrom(b.need("UseCase", r[1]));
            re.setTo(c);
            rel.put("Z-" + r[0], re);
        }
        // fig. 4.1.2
        IDiagramUIModel d = b.newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMPOSITE_STRUCTURE_DIAGRAM, "Use Case Realizations", p);
        b.dm.openDiagram(d);
        b.k = 1.2;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        s.put("Зарегистрироваться на курсы", b.shape(d, b.need("UseCase", "Зарегистрироваться на курсы"), 5, 5, 270, 80));
        s.put("Войти в систему", b.shape(d, b.need("UseCase", "Войти в систему"), 220, 85, 310, 85));
        s.put("Закрыть регистрацию", b.shape(d, b.need("UseCase", "Закрыть регистрацию"), 480, 5, 270, 80));
        s.put("RegisterForCourses", b.shape(d, collab.get("RegisterForCourses"), 40, 140, 225, 70));
        s.put("Login", b.shape(d, collab.get("Login"), 265, 240, 225, 65));
        s.put("CloseRegistration", b.shape(d, collab.get("CloseRegistration"), 495, 140, 225, 70));
        for (String[] r : map) b.tryConnect(d, rel.get("Z-" + r[0]), s.get(r[1]), s.get(r[0]));   // from = use case (supplier)
    }

    // ===================================================== class diagrams
    private IDiagramUIModel classDiagram(B b, String name, IModelElement parent, boolean replace) {
        if (replace) {
            for (IDiagramUIModel old : b.project.toDiagramArray())
                if (IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM.equals(old.getType()) && name.equals(old.getName())
                        && old.getParentModel() != null && parent.getId().equals(old.getParentModel().getId()))
                    old.delete();
        }
        IDiagramUIModel d = b.newDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, name, parent);
        b.dm.openDiagram(d);
        return d;
    }

    /** cells: {className, x, y} in figure units; links: relation keys to draw between placed classes. */
    private void draw(B b, IDiagramUIModel d, double k, String[][] cells, String[] links) {
        b.k = k;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        for (String[] c : cells) {
            IDiagramElement e = b.shape(d, cls.get(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]), 150, 60);
            if (e instanceof IClassUIModel) {
                IClassUIModel cu = (IClassUIModel) e;
                cu.setDisplayAsRobustnessAnalysisIcon(false);
                cu.setShowClassMemberMultiplicity(true);      // string[0..2], long[4]
                cu.setShowAttributesPropertyModifiers(1);     // {id}
                cu.setSuppressImplied1MultiplicityForAttribute(1);   // no "[1]"
                cu.setShowClassMemberConstraints(false);             // no "{unique}"
            }
            com.vp.plugin.diagram.IShapeUIModel su = (com.vp.plugin.diagram.IShapeUIModel) e;
            try { su.fitSize(); } catch (Throwable t) { su.setRequestFitSize(true); }
            s.put(c[0], e);
        }
        separate(cells, s);
        for (String key : links) {
            IRelationship r = (IRelationship) rel.get(key);
            IDiagramElement from = s.get(r.getFrom().getName()), to = s.get(r.getTo().getName());
            if (from == null || to == null) { b.log("WARN skip " + key + ": end not on diagram " + d.getName()); continue; }
            if (from == to) {   // reflexive association: loop above the class
                int x = from.getX(), y = from.getY(), w = from.getWidth();
                java.awt.Point[] pts = {
                        new java.awt.Point(x + w / 5, y), new java.awt.Point(x + w / 5, y - 45),
                        new java.awt.Point(x + w + 60, y - 45), new java.awt.Point(x + w + 60, y + 20), new java.awt.Point(x + w, y + 20)};
                IDiagramElement con = b.dm.createConnector(d, r, from, to, pts);
                if (con instanceof com.vp.plugin.diagram.connector.IAssociationUIModel) {
                    ((com.vp.plugin.diagram.connector.IAssociationUIModel) con).setShowFromRoleName(true);
                    ((com.vp.plugin.diagram.connector.IAssociationUIModel) con).setShowToRoleName(true);
                }
                b.log("reflexive " + key + " -> " + (con == null ? "FAIL" : con.getX() + "," + con.getY() + " " + con.getWidth() + "x" + con.getHeight()));
                continue;
            }
            IDiagramElement con = b.tryConnect(d, r, from, to);
            Double lane = LANE.get(key);
            if (lane != null && con instanceof com.vp.plugin.diagram.IConnectorUIModel) route((com.vp.plugin.diagram.IConnectorUIModel) con, from, to, lane);
            if ("L4".equals(key) && con instanceof com.vp.plugin.diagram.IConnectorUIModel) {   // LoginController -> MainMenuForm around User (fig. 4.2.3)
                com.vp.plugin.diagram.IConnectorUIModel cc = (com.vp.plugin.diagram.IConnectorUIModel) con;
                int x0 = from.getX() + 15, ym = to.getY() - 25, x1 = to.getX() + to.getWidth() * 4 / 5;
                cc.clearPoints();
                cc.addPoint(new java.awt.Point(x0, from.getY() + from.getHeight()));
                cc.addPoint(new java.awt.Point(x0, ym));
                cc.addPoint(new java.awt.Point(x1, ym));
                cc.addPoint(new java.awt.Point(x1, to.getY()));
            }
            if (con instanceof com.vp.plugin.diagram.connector.IAssociationUIModel) {
                com.vp.plugin.diagram.connector.IAssociationUIModel au = (com.vp.plugin.diagram.connector.IAssociationUIModel) con;
                au.setShowFromRoleName(true);
                au.setShowToRoleName(true);
                au.setShowFromMultiplicity(true);
                au.setShowToMultiplicity(true);
                au.setSuppressImplied1MultiplicityForAssociationEnd(2);   // keep the "1" ends
                IAssociation am = (IAssociation) r;
                if (((IAssociationEnd) am.getFromEnd()).getNavigable() == IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE
                        && ((IAssociationEnd) am.getToEnd()).getNavigable() == IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE)
                    au.setShowNavigationArrows(com.vp.plugin.diagram.connector.IAssociationUIModel.SHOW_NAVIGATION_ARROWS_SHOW_ALL_ARROWS_AND_CROSSES);   // <--> as in fig. 4.2.3
            }
            if (con != null) con.setRequestResetCaption(true);
        }
    }

    static final Map<String, Integer> PARALLEL = new HashMap<String, Integer>();
    /** Associations routed by hand: value = lane position (fraction of the shared side / of the source width). */
    static final Map<String, Double> LANE = new HashMap<String, Double>();
    static { PARALLEL.put("K4", -14); PARALLEL.put("K5", 14);
             LANE.put("K4", 0.18); LANE.put("K5", 0.82); LANE.put("R1", 0.6); LANE.put("R2", 0.6); LANE.put("R3", 0.88); }

    /** Orthogonal route for one of two parallel associations: 'first' uses the 1/3 lanes, the other the 2/3 lanes. */
    private void route(com.vp.plugin.diagram.IConnectorUIModel c, IDiagramElement f, IDiagramElement t, double q) {
        int fBottom = f.getY() + f.getHeight(), tBottom = t.getY() + t.getHeight();
        java.util.List<java.awt.Point> p = new java.util.ArrayList<java.awt.Point>();
        if (t.getY() > fBottom || tBottom < f.getY()) {            // vertical first, then horizontal into the side of t
            boolean down = t.getY() > fBottom;
            int fx = f.getX() + (int) (f.getWidth() * q);
            int ty = t.getY() + (int) (t.getHeight() * (down ? q : 1 - q));
            boolean right = t.getX() > fx;
            p.add(new java.awt.Point(fx, down ? fBottom : f.getY()));
            p.add(new java.awt.Point(fx, ty));
            p.add(new java.awt.Point(right ? t.getX() : t.getX() + t.getWidth(), ty));
        } else {                                                   // side by side: two horizontal lanes
            int top = Math.max(f.getY(), t.getY()), bot = Math.min(fBottom, tBottom);
            int y = top + (int) ((bot - top) * q);
            boolean right = t.getX() > f.getX();
            p.add(new java.awt.Point(right ? f.getX() + f.getWidth() : f.getX(), y));
            p.add(new java.awt.Point(right ? t.getX() : t.getX() + t.getWidth(), y));
        }
        c.clearPoints();
        for (java.awt.Point x : p) c.addPoint(x);
    }

    /** fitSize() makes shapes wider than the figure's; push shapes right/down so nothing overlaps (gap 30 px). */
    private void separate(String[][] cells, Map<String, IDiagramElement> s) {
        java.util.List<IDiagramElement> placed = new java.util.ArrayList<IDiagramElement>();
        for (String[] c : cells) {
            IDiagramElement e = s.get(c[0]);
            boolean moved = true;
            for (int guard = 0; moved && guard < 20; guard++) {
                moved = false;
                for (IDiagramElement o : placed) {
                    boolean xo = e.getX() < o.getX() + o.getWidth() + 12 && o.getX() < e.getX() + e.getWidth() + 12;
                    boolean yo = e.getY() < o.getY() + o.getHeight() + 12 && o.getY() < e.getY() + e.getHeight() + 12;
                    if (xo && yo) {
                        // same row (tops close) -> move right, otherwise move down
                        if (Math.abs(e.getY() - o.getY()) < 40) e.setX(o.getX() + o.getWidth() + 30);
                        else e.setY(o.getY() + o.getHeight() + 30);
                        moved = true;
                    }
                }
            }
            placed.add(e);
        }
    }

    void keyAbstractions(B b) {
        IDiagramUIModel d = classDiagram(b, "Key Abstractions", analysis, true);
        draw(b, d, 1.3, new String[][]{
                {"Course", "5", "40"}, {"UserType", "195", "40"}, {"User", "348", "40"},
                {"CourseOffering", "5", "180"}, {"Professor", "195", "180"}, {"DayOfWeek", "5", "330"},
                {"Schedule", "195", "415"}, {"Student", "375", "285"}},
                new String[]{"K1", "K2", "K3", "K4", "K5", "K6", "K7", "K8", "D1", "D2"});
        for (IDiagramElement e : d.toDiagramElementArray())
            if (e instanceof IClassUIModel && e.getMetaModelElement() instanceof IClass)
                ((IClassUIModel) e).setShowOperationType(IClassUIModel.OP_SHOW_TYPE_NONE);   // fig. 4.1.3 has no operations
    }

    void vopcLogin(B b) {
        IDiagramUIModel d = classDiagram(b, "VOPC Login", collab.get("Login"), false);
        draw(b, d, 1.2, new String[][]{
                {"LoginForm", "5", "5"}, {"LoginController", "270", "5"}, {"DataAccess", "515", "5"},
                {"MainMenuForm", "5", "175"}, {"User", "260", "150"}},
                new String[]{"L1", "L2", "L3", "L4", "L5", "L6"});
    }

    void vopcRegister(B b) {
        IDiagramUIModel d = classDiagram(b, "VOPC RegisterForCourses", collab.get("RegisterForCourses"), false);
        draw(b, d, 1.1, new String[][]{
                {"RegistrationForm", "5", "5"}, {"MainMenuForm", "285", "5"}, {"RegistrationController", "485", "5"},
                {"DataAccess", "742", "5"}, {"CourseOffering", "5", "300"}, {"Schedule", "260", "345"},
                {"Student", "515", "250"}, {"FullTimeStudent", "515", "500"}, {"PartTimeStudent", "915", "500"}},
                new String[]{"R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "K4", "K5", "K8", "G-FullTimeStudent", "G-PartTimeStudent"});
    }

    void vopcClose(B b) {
        IDiagramUIModel d = classDiagram(b, "VOPC CloseRegistration", collab.get("CloseRegistration"), false);
        draw(b, d, 1.1, new String[][]{
                {"RegistrarForm", "5", "5"}, {"MainMenuForm", "255", "5"}, {"BillingSystem", "455", "5"},
                {"DataAccess", "5", "85"}, {"CloseRegistrationController", "420", "150"}, {"Student", "5", "290"},
                {"Schedule", "630", "220"}, {"CourseOffering", "800", "400"}},
                new String[]{"C1", "C2", "C3", "C4", "C5", "C7", "R8", "K4", "K5", "K8"});
    }
}
