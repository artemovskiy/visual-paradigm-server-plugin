package ru.urfu.lab2;

import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.shape.IClassUIModel;
import com.vp.plugin.diagram.shape.IPackageUIModel;
import com.vp.plugin.model.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exercises 5.1–5.3 (design model), variant 3. Built on top of a project with stage 2.
 * The manual copies the analysis model with "Duplicate Recursively"; the Open API's duplicate() is shallow,
 * so the design elements are created directly, already with the refined (design) signatures.
 */
public class Stage3 {
    final B b;
    final Map<String, IClass> dc = new LinkedHashMap<String, IClass>();   // design classes by name
    final Map<String, IModelElement> pk = new HashMap<String, IModelElement>();   // design packages
    IModelElement design;

    Stage3(B b) { this.b = b; }

    // ------------------------------------------------------------------ helpers
    static <T> T[] nn(T[] a, T[] empty) { return a == null ? empty : a; }

    IModelElement child(IModelElement parent, String type, String name) {
        for (IModelElement c : nn(parent.toChildArray(), new IModelElement[0]))
            if (name.equals(c.getName()) && (type == null || type.equals(c.getModelType()))) return c;
        return null;
    }

    IPackage pack(IModelElement parent, String name, String stereo) {
        IPackage p = b.named(b.f.createPackage(), name);
        if (stereo != null) p.addStereotype(stereo);
        b.adopt(parent, p);
        pk.put(name, p);
        return p;
    }

    /** Class from Middleware (not a design class): first match whose parent package is 'pkg' (null = any). */
    IModelElement mw(String name, String pkg) {
        for (IModelElement e : b.project.toAllLevelModelElementArray("Class"))
            if (name.equals(e.getName()) && !isDesign(e) && (pkg == null || (e.getParent() != null && pkg.equals(e.getParent().getName()))))
                return e;
        return null;
    }

    boolean isDesign(IModelElement e) {
        for (IModelElement x : dc.values()) if (x.getId().equals(e.getId())) return true;
        return false;
    }

    Object type(String t) {
        if (dc.containsKey(t)) return dc.get(t);
        if ("Date".equals(t)) return mw("Date", "util");
        for (String m : new String[]{"Object", "URL", "XmlRpcController", "XmlRpcStreamConfig", "TypeParser", "TypeSerializer",
                "XmlRpcClient", "XmlRpcClientConfigImpl", "TypeFactoryImpl", "TypeFactory"})
            if (m.equals(t)) { IModelElement e = mw(t, null); if (e != null) return e; }
        return t;
    }

    /** "T[m]" / "T[]" -> {type, modifier}; modifier "[]"/"[4]"/"[0..2]" or null. */
    static String[] splitType(String t) {
        int lb = t.indexOf('[');
        if (lb < 0) return new String[]{t, null};
        return new String[]{t.substring(0, lb), t.substring(lb)};
    }

    void setType(IAttribute a, String t) {
        String[] p = splitType(t);
        Object ty = type(p[0]);
        if (ty instanceof IModelElement) a.setType((IModelElement) ty); else a.setType((String) ty);
        if (p[1] != null) { if ("[]".equals(p[1])) a.setTypeModifier("[]"); else a.setMultiplicity(p[1].substring(1, p[1].length() - 1)); }
    }

    void setType(IParameter a, String t) {
        String[] p = splitType(t);
        Object ty = type(p[0]);
        if (ty instanceof IModelElement) a.setType((IModelElement) ty); else a.setType((String) ty);
        if (p[1] != null) a.setTypeModifier(p[1]);
    }

    /**
     * Members: operations "[$][+-]name(p:T, q:U[4]):R[]", attributes "[$][-+][/]name:T[0..2][=init][{id}]".
     * '$' = static (classifier scope, underlined).
     */
    IClass klass(IModelElement pkg, String name, String stereo, String... members) {
        IClass k = b.named(b.f.createClass(), name);
        if (stereo != null) k.addStereotype(stereo);
        b.adopt(pkg, k);
        dc.put(name, k);
        pending.put(k, members);
        return k;
    }

    final Map<IClass, String[]> pending = new LinkedHashMap<IClass, String[]>();

    /** Members are added after all classes exist, so types can refer to each other. */
    void members() {
        for (Map.Entry<IClass, String[]> e : pending.entrySet()) for (String m : e.getValue()) member(e.getKey(), m);
        pending.clear();
    }

    void member(IClass k, String spec) {
        boolean stat = spec.startsWith("$");
        if (stat) spec = spec.substring(1);
        if (k.hasStereotype("enumeration")) {
            k.createEnumerationLiteral().setName(spec);
            return;
        }
        char vis = spec.charAt(0);
        if (vis != '/') spec = spec.substring(1); else vis = '-';   // "/name" = private derived attribute
        if (spec.contains("(")) {
            IOperation o = k.createOperation();
            o.setVisibility(vis == '-' ? IOperation.VISIBILITY_PRIVATE : IOperation.VISIBILITY_PUBLIC);
            if (stat) o.setScope(IOperation.SCOPE_CLASSIFIER);
            int lp = spec.indexOf('('), rp = spec.lastIndexOf(')');
            o.setName(spec.substring(0, lp));
            String params = spec.substring(lp + 1, rp).trim();
            if (!params.isEmpty()) for (String p : params.split(",")) {
                String[] nt = p.trim().split(":", 2);
                IParameter par = b.f.createParameter();
                par.setName(nt[0].trim());
                if (nt.length > 1) setType(par, nt[1].trim());
                o.addParameter(par);
            }
            String ret = spec.substring(rp + 1);
            if (ret.startsWith(":")) {
                String[] p = splitType(ret.substring(1));
                Object ty = type(p[0]);
                if (ty instanceof IModelElement) o.setReturnType((IModelElement) ty); else o.setReturnType((String) ty);
                if (p[1] != null) o.setTypeModifier(p[1]);
            }
        } else {
            boolean derived = spec.startsWith("/");
            if (derived) spec = spec.substring(1);
            boolean id = spec.endsWith("{id}");
            if (id) spec = spec.substring(0, spec.length() - 4);
            String init = null;
            int eq = spec.indexOf('=');
            if (eq > 0) { init = spec.substring(eq + 1).trim(); spec = spec.substring(0, eq).trim(); }
            IAttribute a = k.createAttribute();
            a.setVisibility(vis == '+' ? IAttribute.VISIBILITY_PUBLIC : IAttribute.VISIBILITY_PRIVATE);
            if (stat) a.setScope(IAttribute.SCOPE_CLASSIFIER);
            String[] nt = spec.split(":", 2);
            a.setName(nt[0]);
            if (nt.length > 1) setType(a, nt[1]);
            if (derived) a.setDerived(true);
            if (id) a.setIsID(true);
            if (init != null) a.setInitialValue(init);
            try {   // no "{unique}" (see Stage2Action)
                IMultiplicity md = a.getMultiplicityDetail();
                if (md == null) { md = b.f.createMultiplicity(); a.setMultiplicityDetail(md); }
                md.setUnique(false);
            } catch (Throwable t) { b.log("WARN unique " + a.getName()); }
        }
    }

    IDependency dep(IModelElement client, IModelElement supplier, String stereo) {
        IDependency d = b.f.createDependency();
        d.setFrom(client);
        d.setTo(supplier);
        if (stereo != null) d.addStereotype(stereo);
        return d;
    }

    IDependency depOnce(IModelElement client, IModelElement supplier) {
        for (ISimpleRelationship r : nn(client.toFromRelationshipArray(), new ISimpleRelationship[0]))
            if (r instanceof IDependency && r.getTo().getId().equals(supplier.getId())) return (IDependency) r;
        return dep(client, supplier, null);
    }

    boolean hasDep(IModelElement client, IModelElement supplier) {
        for (ISimpleRelationship r : nn(client.toFromRelationshipArray(), new ISimpleRelationship[0]))
            if (r instanceof IDependency && r.getTo().getId().equals(supplier.getId())) return true;
        return false;
    }

    IDiagramUIModel freshDiagram(String type, String name, IModelElement parent) {
        IDiagramUIModel old = b.diagram(type, name, parent);
        if (old != null) old.delete();
        IDiagramUIModel d = b.newDiagram(type, name, parent);
        b.dm.openDiagram(d);
        return d;
    }

    // ------------------------------------------------------------------ 5.1 model
    void model() {
        design = b.need("Model", "Design Model");
        IModelElement app = pack(design, "Application", "layer");
        IModelElement bs = pack(design, "BusinessServices", "layer");
        IModelElement login = pack(app, "Login", null), reg = pack(app, "Registration", null), close = pack(app, "CloseRegistration", null);
        IModelElement ua = pack(bs, "UniversityArtefacts", null), ifc = pack(bs, "Interfaces", null);
        IModelElement bsa = pack(bs, "BSAccess", "subsystem"), dba = pack(bs, "DBAccess", "subsystem");

        // UniversityArtefacts (final state after 5.2: Classification metamorphosis, getters/setters, LifeCycle members)
        klass(ua, "DayOfWeek", "enumeration", "SU", "MO", "TU", "WE", "TH", "FR", "SA");
        klass(ua, "UserType", "enumeration", "STUDENT", "PROFESSOR", "REGISTRAR");
        klass(ua, "Course", null, "-code:string{id}", "-description:string", "-duration:byte");
        klass(ua, "CourseOffering", null, "-day:DayOfWeek", "-isCancelled:boolean=FALSE", "-isClosed:boolean=FALSE", "-number:long",
                "/numStudents:byte=0", "-pair:byte", "-semester:byte",
                "+addSchedule(sch:Schedule):boolean", "+cancel():void", "+close():void", "+destroy():void", "+getAttribute():string",
                "+getDay():DayOfWeek", "+getNumber():long", "+getPair():byte", "+getSemester():byte", "+new():CourseOffering",
                "+removeSchedule(sch:Schedule):boolean", "+setAttribute(value:string):void", "+setDay(day:DayOfWeek):void",
                "+setNumber(number:long):void", "+setPair(pair:byte):void", "+setSemester(semester:byte):void",
                "+toJSON():string", "+toString():string");
        klass(ua, "CourseOfferingList", null);
        klass(ua, "Professor", null, "-name:string{id}", "-phones:string[0..2]", "-academicDegree:string");
        klass(ua, "Schedule", null, "-semester:byte", "+delete():void", "+getCourseOfferingList():CourseOffering[]");
        klass(ua, "User", null, "-login:string{id}", "-passwordHash:long[2]", "-type:UserType",
                "+checkPassword(pswrd:string):boolean", "+getType():UserType");
        klass(ua, "Student", null, "-address:string", "-id:long{id}", "-name:string", "$-nextAvailableId:long", "-phones:string[0..2]",
                "+createSchedule(semester:byte, primary:CourseOffering[], alt:CourseOffering[]):boolean",
                "+deleteSchedule(semester:byte):boolean", "+getAddress():string", "+getId():long", "+getName():string",
                "$+getNextAvailableId():long", "+getPhones():string[0..2]", "+getScheduleCourses(semester:byte):CourseOffering[]",
                "+hasScheduleForSemester(semester:byte):boolean", "+setAddress(address:string):void", "+setId(id:long):void",
                "+setName(name:string):void", "$+setNextAvailableId(nextAvailableId:long):void", "+setPhones(phones:string[0..2]):void");
        klass(ua, "Classification", null).setAbstract(true);
        klass(ua, "FullTimeClassification", null, "-graduateDate:Date", "+getGraduateDate():Date", "+setGraduateDate(gd:Date):void");
        klass(ua, "PartTimeClassification", null, "-maxNumCourses:byte", "+getMaxNumCourses():byte", "+setMaxNumCourses(mNC:byte):void");

        // Application
        klass(login, "LoginForm", null, "+login(l:string, pswrd:string):void", "+displayError(errorMsg:string):void", "+close():void");
        klass(login, "LoginController", null, "+login(l:string, pswrd:string):boolean");
        klass(login, "MainMenuForm", null, "+displayError(errorMsg:string):void", "+open(user:User):void", "+registerForCourses():void");
        klass(reg, "RegistrationForm", null, "+confirmDeleteSchedule():void", "+createSchedule(semester:byte):void", "+deleteSchedule():void",
                "+displayBlankSchedule():void", "+displayCourseString(cf:string):void", "+displayMessage(msg:string):void",
                "+requestDeleteScheduleConfirmation():void", "+selectAltCourse(nums:long[2]):void", "+selectPrimaryCourse(nums:long[4]):void",
                "+submitSelectedCourses(semester:byte, primary:long[4], alt:long[2]):void");
        klass(reg, "RegistrationController", null, "+confirmDeleteSchedule():void", "+createSchedule(semester:byte):boolean",
                "+deleteSchedule(semester:byte):boolean", "+getCourseOfferingList(semester:byte):CourseOffering[]",
                "+isRegistrationOpened(semester:byte):boolean", "+registerForCourses(u:User):boolean",
                "+submitSelectedCourses(semester:byte, primary:CourseOffering[4], alt:CourseOffering[2]):boolean");
        klass(close, "RegistrarForm", null);
        klass(close, "CloseRegistrationController", null);

        // Interfaces of the subsystems
        String[] idb = {"+getCourseOfferingList(semester:byte):CourseOfferingList", "+getStudentByUser(u:User):Student",
                "+getStudentList(semester:byte):Student[]", "+getUserByLogin(l:string):User",
                "+updateCourseOfferings(cOffs:CourseOfferingList):boolean", "+updateStudent(st:Student):boolean", "+init():void", "+close():void"};
        String[] ibs = {"+finishTransferData():boolean", "+init(serverURL:string):void",
                "+sendStudentData(name:string, address:string, cOff:CourseOffering[]):boolean", "+startTransferData():boolean"};
        klass(ifc, "IDBAccess", "Interface", idb);
        klass(ifc, "IBSAccess", "Interface", ibs);

        // BSAccess subsystem (variant 3): XMLRPCInterchange mechanism bound to CourseOffering
        klass(bsa, "BSAccess", "subsystem proxy", ibs);
        klass(bsa, "CourseOfferingFactory", null, "+CourseOfferingFactory(pController:XmlRpcController)",
                "+getParser(pConfig:XmlRpcStreamConfig, pContext:NamespaceContextImpl, pURI:string, pLocalName:string):TypeParser",
                "+getSerializer(pConfig:XmlRpcStreamConfig, pObject:Object):TypeSerializer");
        klass(bsa, "CourseOfferingParser", null, "-createCourseOffering(n:long, d:byte, p:byte, s:byte):CourseOffering", "+getResult():Object");
        klass(bsa, "CourseOfferingSerializer", null, "+write(pHandler:ContentHandler, pObject:Object):void");
        // DBAccess subsystem: designed in variants 1 and 2; here only its proxy realizing the interface
        klass(dba, "DBAccess", "subsystem proxy", idb);
        members();

        dc.get("Student").setAbstract(false);
        relations();
        b.log("design classes: " + dc.size());
    }

    final Map<String, IModelElement> rel = new HashMap<String, IModelElement>();
    IGeneralizationSet gset;

    /** VP drops the set membership of these generalizations while diagrams are built: attach them again at the end. */
    void fixSets() {
        for (String k : new String[]{"G-FullTimeClassification", "G-PartTimeClassification"}) {
            IGeneralization g = (IGeneralization) rel.get(k);
            g.setGeneralizationSet(gset);
            boolean in = false;
            for (IGeneralization x : nn(gset.toGeneralizationArray(), new IGeneralization[0])) if (x.getId().equals(g.getId())) in = true;
            if (!in) gset.addGeneralization(g);
        }
        b.log("generalization set members: " + gset.generalizationCount());
    }

    IAssociation assoc(String key, String from, String fm, String fr, String to, String tm, String tr, String nav, String agg) {
        IAssociation as = b.f.createAssociation();
        as.setFrom(dc.get(from));
        as.setTo(dc.get(to));
        IAssociationEnd fe = (IAssociationEnd) as.getFromEnd(), te = (IAssociationEnd) as.getToEnd();
        if (!fm.isEmpty()) fe.setMultiplicity(fm);
        if (!fr.isEmpty()) fe.setName(fr);
        if (!tm.isEmpty()) te.setMultiplicity(tm);
        if (!tr.isEmpty()) { te.setName(tr.replace("/", "")); if (tr.startsWith("/")) te.setDerived(true); }
        int un = IAssociationEnd.NAVIGABLE_NAV_UNSPECIFIED, nv = IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE;
        fe.setNavigable("both".equals(nav) ? nv : un);
        te.setNavigable("none".equals(nav) ? un : nv);
        if ("composite".equals(agg)) fe.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_COMPOSITED);
        if ("shared".equals(agg)) fe.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_SHARED);
        rel.put(key, as);
        return as;
    }

    void relations() {
        // fig. 5.2.23 (Design VOPC RegisterForCourses) + key abstractions
        assoc("A-course-pre", "Course", "*", "prerequisitesOf", "Course", "*", "hasPrerequisites", "none", "");
        assoc("A-course-off", "Course", "1", "", "CourseOffering", "*", "", "none", "");
        assoc("A-off-prof", "CourseOffering", "*", "", "Professor", "0..1", "", "none", "");
        assoc("A-user-prof", "User", "1", "", "Professor", "0..1", "", "none", "composite");
        assoc("A-user-stud", "User", "1", "", "Student", "0..1", "", "none", "composite");
        assoc("A-primary", "Schedule", "*", "", "CourseOffering", "0..4", "primary", "both", "");
        assoc("A-alt", "Schedule", "*", "", "CourseOffering", "0..2", "alternative", "both", "");
        IAssociation ss = assoc("A-stud-sch", "Student", "1", "", "Schedule", "0..1", "", "to", "composite");
        try {   // qualifier semester on the Student end: one schedule per semester
            IQualifier q = b.f.createQualifier();
            IAttribute qa = q.createAttribute();
            qa.setName("semester");
            qa.setType("byte");
            qa.setVisibility(IAttribute.VISIBILITY_PRIVATE);
            ((IAssociationEnd) ss.getFromEnd()).setQualifier(q);
        } catch (Throwable t) { b.log("WARN qualifier: " + t); }
        assoc("A-stud-cls", "Student", "1", "", "Classification", "1", "", "to", "composite");
        assoc("A-menu-rform", "MainMenuForm", "1", "", "RegistrationForm", "0..1", "", "to", "shared");
        assoc("A-menu-rctrl", "MainMenuForm", "1", "", "RegistrationController", "0..1", "", "to", "shared");
        assoc("A-rform-rctrl", "RegistrationForm", "1", "", "RegistrationController", "1", "", "both", "");
        assoc("A-rctrl-off", "RegistrationController", "0..1", "", "CourseOffering", "*", "", "to", "shared");
        assoc("A-rctrl-stud", "RegistrationController", "0..1", "", "Student", "0..1", "curStud", "to", "shared");
        assoc("A-rctrl-dba", "RegistrationController", "0..1", "", "IDBAccess", "0..1", "", "to", "shared");
        assoc("A-menu-rgf", "MainMenuForm", "1", "", "RegistrarForm", "0..1", "", "to", "");
        assoc("A-menu-crc", "MainMenuForm", "1", "", "CloseRegistrationController", "0..1", "", "to", "");
        assoc("A-crc-bs", "CloseRegistrationController", "0..1", "", "IBSAccess", "1", "", "to", "");
        assoc("A-crc-db", "CloseRegistrationController", "0..1", "", "IDBAccess", "1", "", "to", "");
        assoc("A-lf-lc", "LoginForm", "", "", "LoginController", "", "", "both", "");
        assoc("A-lc-db", "LoginController", "", "", "IDBAccess", "", "", "to", "");
        assoc("A-lc-menu", "LoginController", "", "", "MainMenuForm", "", "", "to", "");
        // as in Stage2Action: the set must exist before the generalizations are created and attached to it
        IGeneralizationSet set = b.f.createGeneralizationSet();
        set.setName("{complete, disjoint}");
        set.setDisjoint(true);
        set.setCovering(true);
        b.adopt(design, set);
        gset = set;
        for (String sub : new String[]{"FullTimeClassification", "PartTimeClassification"}) {
            IGeneralization g = b.gen(dc.get("Classification"), dc.get(sub));
            g.setGeneralizationSet(set);
            set.addGeneralization(g);
            rel.put("G-" + sub, g);
        }

        rel.put("D-off-dow", dep(dc.get("CourseOffering"), dc.get("DayOfWeek"), null));
        rel.put("D-user-type", dep(dc.get("User"), dc.get("UserType"), null));
        rel.put("D-rform-off", dep(dc.get("RegistrationForm"), dc.get("CourseOffering"), null));
        rel.put("D-idb-list", dep(dc.get("IDBAccess"), dc.get("CourseOfferingList"), null));
        rel.put("D-idb-stud", dep(dc.get("IDBAccess"), dc.get("Student"), null));
        rel.put("D-idb-user", dep(dc.get("IDBAccess"), dc.get("User"), null));
        rel.put("D-list-off", dep(dc.get("CourseOfferingList"), dc.get("CourseOffering"), null));
        rel.put("D-ibs-off", dep(dc.get("IBSAccess"), dc.get("CourseOffering"), null));

        // realizations of the interfaces (supplier = interface is 'from', see Stage2Action realizations)
        rel.put("R-bs", realize(dc.get("IBSAccess"), dc.get("BSAccess")));
        rel.put("R-db", realize(dc.get("IDBAccess"), dc.get("DBAccess")));

        // fig. 5.2.15 (BSAccess)
        IModelElement object = mw("Object", "lang");
        rel.put("D-bs-off", dep(dc.get("BSAccess"), dc.get("CourseOffering"), null));
        rel.put("D-bs-cfg", dep(dc.get("BSAccess"), mw("XmlRpcClientConfigImpl", null), null));
        rel.put("D-bs-url", dep(dc.get("BSAccess"), mw("URL", null), null));
        IAssociation cl = b.f.createAssociation();
        cl.setFrom(dc.get("BSAccess"));
        cl.setTo(mw("XmlRpcClient", null));
        ((IAssociationEnd) cl.getFromEnd()).setMultiplicity("0..1");
        ((IAssociationEnd) cl.getFromEnd()).setAggregationKind(IAssociationEnd.AGGREGATION_KIND_COMPOSITED);
        ((IAssociationEnd) cl.getToEnd()).setMultiplicity("0..1");
        ((IAssociationEnd) cl.getToEnd()).setName("client");
        ((IAssociationEnd) cl.getFromEnd()).setNavigable(IAssociationEnd.NAVIGABLE_NAV_UNSPECIFIED);
        ((IAssociationEnd) cl.getToEnd()).setNavigable(IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE);
        rel.put("A-bs-client", cl);
        rel.put("G-off-obj", b.gen(object, dc.get("CourseOffering")));
        rel.put("G-fac-impl", b.gen(mw("TypeFactoryImpl", null), dc.get("CourseOfferingFactory")));
        rel.put("D-ser-off", dep(dc.get("CourseOfferingSerializer"), dc.get("CourseOffering"), null));
        rel.put("D-par-off", dep(dc.get("CourseOfferingParser"), dc.get("CourseOffering"), null));
        rel.put("D-fac-ser", dep(dc.get("CourseOfferingFactory"), dc.get("CourseOfferingSerializer"), null));
        rel.put("D-fac-par", dep(dc.get("CourseOfferingFactory"), dc.get("CourseOfferingParser"), null));
        rel.put("D-fac-tser", dep(dc.get("CourseOfferingFactory"), mw("TypeSerializer", null), null));
        rel.put("D-fac-tpar", dep(dc.get("CourseOfferingFactory"), mw("TypeParser", null), null));
        rel.put("G-impl-obj", b.gen(object, mw("TypeFactoryImpl", null)));
        rel.put("D-tser-obj", depOnce(mw("TypeSerializer", null), object));
        rel.put("D-tpar-obj", depOnce(mw("TypeParser", null), object));
        rel.put("R-ser", realize(mw("TypeSerializer", null), dc.get("CourseOfferingSerializer")));
        rel.put("R-par", realize(mw("TypeParser", null), dc.get("CourseOfferingParser")));
    }

    IRealization realize(IModelElement iface, IModelElement impl) {
        IRealization r = b.f.createRealization();
        r.setFrom(iface);
        r.setTo(impl);
        return r;
    }

    // ------------------------------------------------------------------ 5.1 diagrams
    IPackageUIModel ps(IDiagramUIModel d, IModelElement p, double x, double y, double w, double h, boolean owner) {
        IDiagramElement e = b.shape(d, p, x, y, w, h);
        if (owner && e instanceof IPackageUIModel) ((IPackageUIModel) e).setShowOwnerOption(IPackageUIModel.SHOW_OWNER_NAME_ONLY);
        return (IPackageUIModel) e;
    }

    IDiagramElement link(IDiagramUIModel d, IModelElement rel, IDiagramElement from, IDiagramElement to) {
        IDiagramElement c = b.tryConnect(d, rel, from, to);
        if (c != null) c.setRequestResetCaption(true);
        return c;
    }

    /** Orthogonal route through figure-unit points x0,y0,x1,y1,... (first/last on the shapes' edges). */
    void route(IDiagramElement c, double... xy) {
        if (!(c instanceof com.vp.plugin.diagram.IConnectorUIModel)) return;
        com.vp.plugin.diagram.IConnectorUIModel cc = (com.vp.plugin.diagram.IConnectorUIModel) c;
        cc.clearPoints();
        for (int i = 0; i + 1 < xy.length; i += 2) cc.addPoint(new java.awt.Point((int) Math.round(xy[i] * b.k), (int) Math.round(xy[i + 1] * b.k)));
    }

    IDependency pdep(IDiagramUIModel d, Map<String, IDiagramElement> s, String from, String to, String stereo, String name, double... xy) {
        IModelElement a = s.get(from).getMetaModelElement(), z = s.get(to).getMetaModelElement();
        IDependency dd = null;
        for (ISimpleRelationship r : nn(a.toFromRelationshipArray(), new ISimpleRelationship[0]))
            if (r instanceof IDependency && r.getTo().getId().equals(z.getId())) dd = (IDependency) r;
        if (dd == null) dd = dep(a, z, stereo);
        if (name != null) dd.setName(name);
        IDiagramElement c = link(d, dd, s.get(from), s.get(to));
        if (xy.length >= 4) route(c, xy);
        return dd;
    }

    void mainPackages() {
        IModelElement mech = child(design, null, "Architectural Mechanisms");
        IModelElement remote = child(mech, null, "RemoteInterchange"), persist = child(mech, null, "Persistency");
        IModelElement middle = child(design, null, "Middleware");
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM, "Main", design);
        b.k = 1.2;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        s.put("app", ps(d, pk.get("Application"), 215, 5, 170, 110, false));
        s.put("bs", ps(d, pk.get("BusinessServices"), 215, 155, 170, 120, false));
        s.put("mw", ps(d, middle, 215, 320, 170, 110, false));
        s.put("ri", ps(d, remote, 5, 305, 165, 125, true));
        s.put("pe", ps(d, persist, 425, 305, 165, 125, true));
        pdep(d, s, "app", "bs", null, null, 300, 115, 300, 155);
        pdep(d, s, "bs", "mw", null, null, 300, 275, 300, 320);
        pdep(d, s, "bs", "ri", null, null, 215, 230, 97, 230, 97, 305);
        pdep(d, s, "bs", "pe", null, null, 385, 230, 500, 230, 500, 305);
        pdep(d, s, "ri", "mw", null, null, 170, 380, 215, 380);
        pdep(d, s, "pe", "mw", null, null, 425, 380, 385, 380);
    }

    void dependencies() {
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM, "Dependencies", pk.get("Application"));
        b.k = 1.5;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        s.put("login", ps(d, pk.get("Login"), 172, 5, 120, 70, false));
        s.put("close", ps(d, pk.get("CloseRegistration"), 20, 95, 135, 65, false));
        s.put("reg", ps(d, pk.get("Registration"), 330, 95, 135, 65, false));
        s.put("ua", ps(d, pk.get("UniversityArtefacts"), 25, 210, 150, 75, true));
        s.put("ifc", ps(d, pk.get("Interfaces"), 292, 210, 150, 75, true));
        pdep(d, s, "login", "close", null, null, 172, 55, 45, 55, 45, 95);
        pdep(d, s, "login", "reg", null, null, 292, 55, 400, 55, 400, 95);
        pdep(d, s, "login", "ua", null, null, 172, 35, 8, 35, 8, 245, 25, 245);
        pdep(d, s, "login", "ifc", null, null, 232, 75, 232, 180, 310, 180, 310, 210);
        pdep(d, s, "close", "ua", null, null, 60, 160, 60, 210);
        pdep(d, s, "close", "ifc", null, null, 120, 160, 120, 195, 330, 195, 330, 210);
        pdep(d, s, "reg", "ifc", null, null, 400, 160, 400, 210);
        pdep(d, s, "reg", "ua", null, null, 465, 130, 478, 130, 478, 300, 12, 300, 12, 270, 25, 270);
    }

    void businessServices() {
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM, "BusinessServices", pk.get("BusinessServices"));
        b.k = 1.4;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        s.put("bsa", ps(d, pk.get("BSAccess"), 5, 5, 190, 95, false));
        s.put("ifc", ps(d, pk.get("Interfaces"), 230, 65, 120, 70, false));
        s.put("dba", ps(d, pk.get("DBAccess"), 385, 5, 190, 95, false));
        s.put("ua", ps(d, pk.get("UniversityArtefacts"), 215, 185, 150, 70, false));
        pdep(d, s, "bsa", "ifc", null, null);
        pdep(d, s, "dba", "ifc", null, null);
        pdep(d, s, "ifc", "ua", null, null);
        pdep(d, s, "bsa", "ua", null, null);
        pdep(d, s, "dba", "ua", null, null);
    }

    final Map<String, IModelElement> collab = new HashMap<String, IModelElement>();

    void designRealizations() {
        IModelElement p = pack(design, "Design Use Case Realizations", null);
        String[][] map = {{"DesignRegisterForCourses", "Зарегистрироваться на курсы"}, {"DesignLogin", "Войти в систему"},
                {"DesignCloseRegistration", "Закрыть регистрацию"}};
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_COMPOSITE_STRUCTURE_DIAGRAM, "Design Use Case Realizations", p);
        b.k = 1.2;
        for (String[] r : map) {
            ICollaboration c = b.named(b.f.createCollaboration(), r[0]);
            c.addStereotype("usecase realization");
            b.adopt(p, c);
            collab.put(r[0], c);
        }
        s.put("Зарегистрироваться на курсы", b.shape(d, b.need("UseCase", "Зарегистрироваться на курсы"), 5, 5, 270, 80));
        s.put("Войти в систему", b.shape(d, b.need("UseCase", "Войти в систему"), 220, 85, 310, 85));
        s.put("Закрыть регистрацию", b.shape(d, b.need("UseCase", "Закрыть регистрацию"), 480, 5, 270, 80));
        s.put("DesignRegisterForCourses", b.shape(d, collab.get("DesignRegisterForCourses"), 10, 160, 245, 70));
        s.put("DesignLogin", b.shape(d, collab.get("DesignLogin"), 275, 250, 225, 65));
        s.put("DesignCloseRegistration", b.shape(d, collab.get("DesignCloseRegistration"), 505, 160, 245, 70));
        for (String[] r : map) {
            IRealization re = realize(b.need("UseCase", r[1]), collab.get(r[0]));
            link(d, re, s.get(r[1]), s.get(r[0]));
        }
    }

    // ------------------------------------------------------------------ 5.1.6 deployment
    void deployment() {
        IModelElement dm = b.need("Model", "Deployment Model");
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_DEPLOYMENT_DIAGRAM, "Main", dm);
        b.k = 1.3;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        Object[][] nodes = {
                {"stud", "Компьютер студента", "executionEnvironment", 5, 5, 295, 130},
                {"prof", "Компьютер лектора", "executionEnvironment", 5, 155, 295, 125},
                {"regr", "Компьютер работника учебного отдела", "executionEnvironment", 5, 295, 295, 165},
                {"sw", "Коммутатор", "device", 420, 170, 125, 70},
                {"prn", "Кампусный принтер", "device", 605, 5, 250, 75},
                {"srv", "Сервер системы регистрации", "executionEnvironment", 605, 95, 250, 285},
                {"bill", "Сервер рассчётной системы", "executionEnvironment", 320, 395, 265, 65}};
        for (Object[] n : nodes) {
            INode node = b.named(b.f.createNode(), (String) n[1]);
            node.addStereotype((String) n[2]);
            b.adopt(dm, node);
            s.put((String) n[0], b.shape(d, node, ((Number) n[3]).doubleValue(), ((Number) n[4]).doubleValue(), ((Number) n[5]).doubleValue(), ((Number) n[6]).doubleValue()));
        }
        Object[][] comps = {
                {"stud", "StudentApplication.exe", 20, 62, 250, 58}, {"prof", "ProfessorApplication.exe", 20, 215, 250, 55},
                {"regr", "RegistrarApplication.exe", 20, 395, 250, 58}, {"srv", "RegistrationProcess", 620, 185, 220, 58},
                {"srv", "DBAccessProcess", 620, 247, 220, 58}, {"srv", "BSAccessProcess", 620, 309, 220, 58}};
        for (Object[] c : comps) {
            IComponent comp = b.named(b.f.createComponent(), (String) c[1]);
            IModelElement owner = s.get((String) c[0]).getMetaModelElement();
            owner.addChild(comp);
            IDiagramElement cs = b.shape(d, comp, ((Number) c[2]).doubleValue(), ((Number) c[3]).doubleValue(), ((Number) c[4]).doubleValue(), ((Number) c[5]).doubleValue());
            try { b.inside(s.get((String) c[0]), cs); } catch (Throwable t) { b.log("WARN component inside: " + t); }
        }
        String[][] paths = {{"stud", "1..*", "sw", "1", "Campus Wi-Fi"}, {"prof", "1..*", "sw", "1", "Campus Wi-Fi"},
                {"regr", "1..*", "sw", "1", "Campus LAN"}, {"sw", "1", "prn", "1", "USB"}, {"sw", "1", "srv", "1", "Campus LAN"},
                {"srv", "1", "bill", "1", "Accounting Department LAN"}};
        double[][] routes = {{300, 70, 455, 70, 455, 170}, {300, 215, 420, 215}, {300, 360, 455, 360, 455, 240},
                {530, 170, 530, 65, 605, 65}, {530, 240, 530, 275, 605, 275}, {815, 380, 815, 425, 585, 425}};
        int ri = 0;
        for (String[] p : paths) {
            IAssociation a = b.f.createAssociation();
            a.setFrom(s.get(p[0]).getMetaModelElement());
            a.setTo(s.get(p[2]).getMetaModelElement());
            a.setName(p[4]);
            ((IAssociationEnd) a.getFromEnd()).setMultiplicity(p[1]);
            ((IAssociationEnd) a.getToEnd()).setMultiplicity(p[3]);
            route(link(d, a, s.get(p[0]), s.get(p[2])), routes[ri++]);
        }
    }
    // ------------------------------------------------------------------ 5.2 design use-case realizations
    Seq seq(String name, String collabName, double k, double bottom) {
        Seq s = new Seq(b, name, collab.get(collabName), k, bottom);
        s.classes = dc;
        return s;
    }

    /** Fig. 5.2.1 */
    void designLogin() {
        Seq s = seq("Design Login Basic Flow", "DesignLogin", 1.2, 560);
        s.actor("user", "Пользователь", 55);
        s.lifeline("lform", "lform", "LoginForm", 195, 5, false);
        s.lifeline("lctrl", "lctrl", "LoginController", 540, 5, false);
        s.lifeline("dao", "dao", "IDBAccess", 757, 5, false); s.stereo("dao", "interface");
        s.lifeline("curUser", "curUser", "User", 910, 5, false);
        s.lifeline("menu", "menu", "MainMenuForm", 340, 350, false);
        s.call("user", "lform", "LoginForm", "login", 112);
        s.call("lform", "lctrl", "LoginController", "login", 135);
        s.call("lctrl", "dao", "IDBAccess", "getUserByLogin", 150);
        IMessage o1 = s.call("lctrl", "curUser", "User", "checkPassword", 237);
        IMessage o2 = s.reply("curUser", "lctrl", "result", 260);
        s.fragment("opt", new String[]{"lform", "lctrl", "dao", "curUser"}, 208, 958, new double[]{168, 275},
                new String[]{"найдена учётная запись по логину"}, new IMessage[][]{{o1, o2}});
        IMessage a1 = s.create("lctrl", "menu", "open(curUser)", 376);
        IMessage a2 = s.call("menu", "curUser", "User", "getType", 408);
        IMessage a3 = s.call("lctrl", "lform", "LoginForm", "displayError", 460);
        s.fragment("alt", null, 90, 958, new double[]{285, 423, 500},
                new String[]{"curUser != NULL && curUser.checkPassword() == TRUE", "else"}, new IMessage[][]{{a1, a2}, {a3}});
        s.destroy("lctrl", "lform", "close()", 513);
        s.done();
    }

    /** Fig. 5.2.2 */
    void designRegister() {
        for (String n : new String[]{"Design UpdateSchedule Subflow", "Design DeleteSchedule Subflow", "Design SubmitSchedule Subflow"})
            seq(n, "DesignRegisterForCourses", 1.0, 200).done();
        designDisplay();
        designCreate();
        Seq s = seq("Design RegisterForCourses Basic Flow", "DesignRegisterForCourses", 1.4, 790);
        s.actor("st", "Студент", 35);
        s.lifeline("menu", "menu", "MainMenuForm", 147, 5, false);
        s.lifeline("rctrl", "rctrl", "RegistrationController", 400, 5, false);
        s.lifeline("dao", "dao", "IDBAccess", 585, 5, false); s.stereo("dao", "interface");
        s.lifeline("curStud", "curStud", "Student", 708, 5, false);
        s.lifeline("rform", "rform", "RegistrationForm", 292, 350, false);
        s.call("st", "menu", "MainMenuForm", "registerForCourses", 85);
        s.call("menu", "rctrl", "RegistrationController", "registerForCourses", 95);
        s.self("rctrl", "RegistrationController", "isRegistrationOpened", 107);
        IMessage o1 = s.call("rctrl", "dao", "IDBAccess", "getStudentByUser", 162);
        IMessage o2 = s.call("rctrl", "curStud", "Student", "hasScheduleForSemester", 185);
        s.fragment("opt", new String[]{"menu", "rctrl", "dao", "curStud"}, 157, 765, new double[]{138, 222},
                new String[]{"rctrl.isRegistrationOpened(s)"}, new IMessage[][]{{o1, o2}});
        s.reply("rctrl", "menu", "result", 240);
        IMessage e1 = s.self("menu", "MainMenuForm", "displayError", 305);
        IMessage e2 = s.create("menu", "rform", "open", 370);
        s.fragment("alt", null, 3, 765, new double[]{252, 340, 777},
                new String[]{"!rctrl.registerForCourses(curUser)", "else"}, new IMessage[][]{{e1}, {e2}});
        IInteractionOperand elseOp = s.last[1];
        s.fragment(elseOp, "alt", null, 10, 730, new double[]{405, 495, 590, 675, 762},
                new String[]{"создаётся учебный план", "изменяется учебный план", "удаляется учебный план", "утверждается учебный план"}, null);
        String[] subs = {"Design CreateSchedule Subflow", "Design UpdateSchedule Subflow", "Design DeleteSchedule Subflow", "Design SubmitSchedule Subflow"};
        double[] ry = {420, 510, 605, 688};
        IInteractionOperand[] inner = s.last;
        for (int i = 0; i < 4; i++) s.ref(subs[i], null, new String[]{"rform", "rctrl", "dao", "curStud"}, 262, 718, ry[i], ry[i] + 63, inner[i]);
        s.done();
    }

    /** Fig. 5.2.5 */
    void designDisplay() {
        Seq s = seq("Design DisplayCourseOfferings Subflow", "DesignRegisterForCourses", 1.6, 250);
        s.lifeline("rform", "rform", "RegistrationForm", 88, 5, false);
        s.lifeline("rctrl", "rctrl", "RegistrationController", 300, 5, false);
        s.lifeline("cOff", "cOff[i]", "CourseOffering", 475, 5, true);
        s.call("rform", "rctrl", "RegistrationController", "getCourseOfferingList", 70);
        s.reply("rctrl", "rform", "cOff[]", 100);
        IMessage a = s.call("rform", "cOff", "CourseOffering", "toJSON", 185);
        IMessage c = s.self("rform", "RegistrationForm", "displayCourseString", 200);
        s.fragment("loop", null, 38, 545, new double[]{125, 240}, new String[]{"for all cOff[i]"}, new IMessage[][]{{a, c}});
        s.done();
    }

    /** Fig. 5.2.4 (variants 1 and 3); shifted right so that long actor-message labels are not clipped. */
    void designCreate() {
        double X = 90;
        Seq s = seq("Design CreateSchedule Subflow", "DesignRegisterForCourses", 1.25, 630);
        s.actor("st", "Студент", 30 + X);
        s.lifeline("rform", "rform", "RegistrationForm", 165 + X, 5, false);
        s.lifeline("rctrl", "rctrl", "RegistrationController", 392 + X, 5, false);
        s.lifeline("dao", "dao", "IDBAccess", 620 + X, 5, false); s.stereo("dao", "interface");
        s.lifeline("curStud", "curStud", "Student", 858 + X, 5, false);
        s.lifeline("sch", "sch", "Schedule", 720 + X, 500, false);
        java.util.List<IMessage> in = new java.util.ArrayList<IMessage>();
        in.add(s.call("st", "rform", "RegistrationForm", "createSchedule", 158));
        in.add(s.call("rform", "rctrl", "RegistrationController", "createSchedule", 183));
        in.add(s.call("rctrl", "dao", "IDBAccess", "getCourseOfferingList", 210));
        in.add(s.self("rform", "RegistrationForm", "displayBlankSchedule", 225));
        in.add(s.call("st", "rform", "RegistrationForm", "selectPrimaryCourse", 368));
        in.add(s.call("st", "rform", "RegistrationForm", "selectAltCourse", 402));
        in.add(s.call("st", "rform", "RegistrationForm", "submitSelectedCourses", 436));
        in.add(s.call("rform", "rctrl", "RegistrationController", "submitSelectedCourses", 462));
        in.add(s.call("rctrl", "curStud", "Student", "createSchedule", 488));
        in.add(s.create("curStud", "sch", "new", 522));
        in.add(s.call("rctrl", "dao", "IDBAccess", "updateStudent", 556));
        in.add(s.self("rform", "RegistrationForm", "displayMessage", 575));
        s.fragment("opt", null, 3 + X, 905 + X, new double[]{80, 615},
                new String[]{"учебный план на текущий семестр у студента отсутствует"}, new IMessage[][]{in.toArray(new IMessage[0])});
        s.ref("Design DisplayCourseOfferings Subflow", null, new String[]{"rform", "rctrl"}, 122 + X, 438 + X, 262, 335, s.last[0]);
        s.done();
    }

    /** Fig. 5.2.3 */
    void designTransfer() {
        Seq s = seq("Design TransferSchedulesData Subflow", "DesignCloseRegistration", 1.4, 450);
        s.lifeline("crctrl", "crctrl", "CloseRegistrationController", 103, 5, false);
        s.lifeline("dao", "dao", "IDBAccess", 287, 5, false); s.stereo("dao", "interface");
        s.lifeline("stud", "studlist[i]", "Student", 432, 5, true);
        s.lifeline("sch", "studlist[i].schedule[s]", "Schedule", 590, 5, true);
        s.lifeline("bs", "bs", "IBSAccess", 742, 5, false); s.stereo("bs", "interface");
        s.call("crctrl", "dao", "IDBAccess", "getStudentList", 85);
        s.call("crctrl", "bs", "IBSAccess", "startTransferData", 115);
        IMessage l1 = s.call("crctrl", "stud", "Student", "getName", 265);
        IMessage l2 = s.call("crctrl", "stud", "Student", "getAddress", 288);
        IMessage l3 = s.call("crctrl", "stud", "Student", "getScheduleCourses", 311);
        IMessage l4 = s.call("stud", "sch", "Schedule", "getCourseOfferingList", 331);
        IMessage l5 = s.call("crctrl", "bs", "IBSAccess", "sendStudentData", 355);
        IMessage f1 = s.call("crctrl", "bs", "IBSAccess", "finishTransferData", 410);
        s.fragment("opt", null, 50, 783, new double[]{130, 438}, new String[]{"bs.startTransferData()"}, new IMessage[][]{{f1}});
        s.fragment(s.last[0], "loop", null, 68, 777, new double[]{196, 380}, new String[]{"for all students in list"},
                new IMessage[][]{{l1, l2, l3, l4, l5}});
        s.done();
    }

    // ------------------------------------------------------------------ 5.2 BSAccess behaviour
    IModelElement bsReal;

    Seq bseq(String name, double k, double bottom) {
        Seq s = new Seq(b, name, bsReal, k, bottom);
        s.classes = dc;
        return s;
    }

    void bsCollaboration() {
        ICollaboration c = b.named(b.f.createCollaboration(), "IBSAccessRealization");
        c.addStereotype("interface realization");
        b.adopt(pk.get("BSAccess"), c);
        bsReal = c;
    }

    /** Fig. 5.2.17 */
    void bsInit() {
        Seq s = bseq("init", 1.3, 490);
        s.gate("in", 20, 97);
        s.lifeline("cn", "cn", "BSAccess", 155, 5, false); s.stereo("cn", "subsystem proxy");
        s.lifeline("config", "config", "XmlRpcClientConfigImpl", 595, 112, false);
        s.lifeline("url", "url", "URL", 58, 162, false);
        s.lifeline("client", "client", "XmlRpcClient", 277, 245, false);
        s.lifeline("of", "of", "CourseOfferingFactory", 427, 340, false);
        s.call("in", "cn", "BSAccess", "init", 97);
        s.create("cn", "config", "XmlRpcClientConfigImpl()", 140);
        s.create("cn", "url", "URL(spec : String = serverURL)", 185);
        s.call("cn", "config", null, "setServerURL(serverURL : URL = url) : void", 232);
        s.create("cn", "client", "new", 262);
        s.call("cn", "client", null, "setConfig(config : XmlRpcClientConfig = config) : void", 337);
        s.create("cn", "of", "new", 367);
        s.call("cn", "client", null, "setTypeFactory(pTypeFactory : TypeFactory = of)", 425);
        s.call("cn", "config", null, "setEnabledForExtensions(pExtensions : boolean = true)", 470);
        s.done();
    }

    /** Fig. 5.2.18 */
    void bsSend() {
        Seq s = bseq("sendStudentData", 1.1, 680);
        s.dx = 200;   // room for the long found-message label left of cn
        s.gate("in", 20, 110);
        s.lifeline("cn", "cn", "BSAccess", 135, 5, false); s.stereo("cn", "subsystem proxy");
        s.lifeline("of", "of", "CourseOfferingFactory", 343, 5, false);
        s.lifeline("cs", "cs", "CourseOfferingSerializer", 560, 5, false);
        s.lifeline("cOff", "cOff[i]", "CourseOffering", 750, 5, true);
        s.lifeline("client", "client", "XmlRpcClient", 890, 5, false);
        s.actor("ext", "Рассчётная система", 1023);
        s.gate("out", 30, 663);
        s.call("in", "cn", "BSAccess", "sendStudentData", 110);
        IMessage i1 = s.self("cn", "BSAccess", "init", 200);
        s.fragment("opt", new String[]{"cn"}, 8, 232, new double[]{122, 243}, new String[]{"client == NULL"}, new IMessage[][]{{i1}});
        s.call("cn", "client", null, "execute(pMethodName : String = requestName, pParams : Object[]) : Object", 258);
        s.call("client", "of", null, "getSerializer()", 290);
        IMessage w = s.call("client", "cs", null, "write(pHandler : ContentHandler, pObject : Object) : void", 393);
        IMessage g1 = s.call("cs", "cOff", "CourseOffering", "getNumber", 412);
        IMessage g2 = s.call("cs", "cOff", "CourseOffering", "getDay", 440);
        IMessage g3 = s.call("cs", "cOff", "CourseOffering", "getPair", 468);
        IMessage g4 = s.call("cs", "cOff", "CourseOffering", "getSemester", 496);
        IMessage rr = s.reply("cs", "client", "return", 527);
        s.fragment("loop", new String[]{"cs", "cOff", "client"}, 530, 1013, new double[]{303, 557}, new String[]{"for all courses"},
                new IMessage[][]{{w, g1, g2, g3, g4, rr}});
        s.call("client", "ext", null, "xmlrpcRequest", 590);
        s.reply("ext", "client", "return(xmlrpcResult)", 632);
        s.reply("client", "cn", "return(result)", 650);
        s.reply("cn", "out", "result", 663);
        s.done();
        for (String n : new String[]{"startTransferData", "finishTransferData"}) bseq(n, 1.0, 200).done();
    }

    // ------------------------------------------------------------------ class diagrams
    IModelElement any(String name) {
        if (dc.containsKey(name)) return dc.get(name);
        if (pk.containsKey(name)) return pk.get(name);
        IModelElement e = mw(name, null);
        if (e == null) throw new IllegalStateException("No element " + name);
        return e;
    }

    /** cells: {name, x, y [, "noops" | "ball" | "owner"]}; links: rel keys; routes: key -> figure points. */
    Map<String, IDiagramElement> cdraw(IDiagramUIModel d, double k, String[][] cells, String[] links, Map<String, double[]> routes) {
        b.k = k;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        for (String[] c : cells) {
            IModelElement e = any(c[0]);
            IDiagramElement sh = b.shape(d, e, Double.parseDouble(c[1]), Double.parseDouble(c[2]), 150, 60);
            if (sh instanceof IClassUIModel) {
                IClassUIModel cu = (IClassUIModel) sh;
                cu.setDisplayAsRobustnessAnalysisIcon(false);
                cu.setShowClassMemberMultiplicity(true);
                cu.setShowAttributesPropertyModifiers(1);
                cu.setSuppressImplied1MultiplicityForAttribute(1);
                cu.setShowClassMemberConstraints(false);
                for (int i = 3; i < c.length; i++) {
                    if ("noops".equals(c[i]) || "bare".equals(c[i])) cu.setShowOperationType(IClassUIModel.OP_SHOW_TYPE_NONE);
                    if ("bare".equals(c[i])) cu.setShowAttributeType(IClassUIModel.ATTR_SHOW_TYPE_NONE);
                    if ("ball".equals(c[i])) cu.setInterfaceBall(true);
                    if ("owner".equals(c[i])) cu.setShowOwnerOption(IPackageUIModel.SHOW_OWNER_FULLY_QUALIFIED);
                }
                if (!"ball".equals(c.length > 3 ? c[3] : "")) try { cu.fitSize(); } catch (Throwable t) { cu.setRequestFitSize(true); }
            }
            s.put(c[0], sh);
        }
        for (String key : links) {
            IRelationship r = (IRelationship) rel.get(key);
            if (r == null) { b.log("WARN no rel " + key); continue; }
            IDiagramElement from = find(s, r.getFrom()), to = find(s, r.getTo());
            if (from == null || to == null) { b.log("WARN skip " + key + " on " + d.getName()); continue; }
            IDiagramElement con;
            if (from == to) {
                int x = from.getX(), y = from.getY(), w = from.getWidth();
                con = b.dm.createConnector(d, r, from, to, new java.awt.Point[]{new java.awt.Point(x + w / 5, y), new java.awt.Point(x + w / 5, y - 45),
                        new java.awt.Point(x + w + 60, y - 45), new java.awt.Point(x + w + 60, y + 20), new java.awt.Point(x + w, y + 20)});
            } else con = b.tryConnect(d, r, from, to);
            if (con == null) continue;
            if (routes != null && routes.get(key) != null) route(con, routes.get(key));
            if (con instanceof com.vp.plugin.diagram.connector.IAssociationUIModel) {
                com.vp.plugin.diagram.connector.IAssociationUIModel au = (com.vp.plugin.diagram.connector.IAssociationUIModel) con;
                au.setShowFromRoleName(true); au.setShowToRoleName(true);
                au.setShowFromMultiplicity(true); au.setShowToMultiplicity(true);
                au.setSuppressImplied1MultiplicityForAssociationEnd(2);
                IAssociation am = (IAssociation) r;
                if (((IAssociationEnd) am.getFromEnd()).getNavigable() == IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE
                        && ((IAssociationEnd) am.getToEnd()).getNavigable() == IAssociationEnd.NAVIGABLE_NAV_NAVIGABLE)
                    au.setShowNavigationArrows(com.vp.plugin.diagram.connector.IAssociationUIModel.SHOW_NAVIGATION_ARROWS_SHOW_ALL_ARROWS_AND_CROSSES);
            }
            con.setRequestResetCaption(true);
        }
        return s;
    }

    static IDiagramElement find(Map<String, IDiagramElement> s, IModelElement e) {
        for (IDiagramElement x : s.values()) if (x.getMetaModelElement() != null && e != null && x.getMetaModelElement().getId().equals(e.getId())) return x;
        return null;
    }

    static Map<String, double[]> R(Object... kv) {
        Map<String, double[]> m = new HashMap<String, double[]>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (double[]) kv[i + 1]);
        return m;
    }

    static double[] P(double... xy) { return xy; }

    /** Fig. 5.2.14 */
    void bsPackages() {
        IModelElement bsa = pk.get("BSAccess");
        IModelElement xi = b.need("Package", "XMLRPCInterchange"), net = b.need("Package", "net"), xml = b.need("Package", "xmlrpc");
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_PACKAGE_DIAGRAM, "BSAccess", bsa);
        b.k = 1.0;
        Map<String, IDiagramElement> s = new HashMap<String, IDiagramElement>();
        s.put("bsa", ps(d, bsa, 345, 5, 200, 85, false));
        s.put("net", ps(d, net, 5, 385, 250, 80, false));
        s.put("xml", ps(d, xml, 640, 385, 305, 80, false));
        s.put("xi", ps(d, xi, 345, 355, 200, 110, true));
        for (String k : new String[]{"net", "xml"}) ((IPackageUIModel) s.get(k)).setShowOwnerOption(IPackageUIModel.SHOW_OWNER_FULLY_QUALIFIED);
        pdep(d, s, "bsa", "net", "import", null, 345, 72, 25, 72, 25, 385);
        pdep(d, s, "bsa", "xml", "import", null, 545, 72, 915, 72, 915, 385);
        pdep(d, s, "bsa", "xi", "bind", "<XMLRPCConnector -> BSAccess,\nObjectToSend -> CourseOffering,\nObjectToReceive -> CourseOffering,\n"
                + "CustomTypeFactory -> CourseOfferingFactory,\nCustomSerializer -> CourseOfferingSerializer,\nCustomParser -> CourseOfferingParser>", 360, 90, 360, 355);
        pdep(d, s, "xi", "net", "import", null, 345, 420, 255, 420);
        pdep(d, s, "xi", "xml", "import", null, 545, 420, 640, 420);
    }

    /** Fig. 5.2.15 */
    void bsClasses() {
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, "BSAccess", pk.get("BSAccess"));
        cdraw(d, 1.0, new String[][]{
                {"BSAccess", "45", "0"}, {"XmlRpcClientConfigImpl", "760", "15", "bare", "owner"}, {"XmlRpcClient", "760", "110", "bare", "owner"},
                {"URL", "735", "180", "bare", "owner"}, {"Object", "45", "155"}, {"CourseOffering", "325", "170", "noops"}, {"IBSAccess", "590", "170", "ball"},
                {"TypeFactoryImpl", "675", "248", "owner"}, {"CourseOfferingSerializer", "45", "362"}, {"CourseOfferingParser", "465", "362"},
                {"CourseOfferingFactory", "130", "460"}, {"TypeSerializer", "45", "585", "owner"}, {"TypeParser", "590", "585", "owner"}},
                new String[]{"D-bs-off", "D-bs-cfg", "A-bs-client", "D-bs-url", "R-bs", "D-ibs-off", "G-off-obj", "G-fac-impl",
                        "D-ser-off", "D-par-off", "D-fac-ser", "D-fac-par", "D-fac-tser", "D-fac-tpar", "R-ser", "R-par",
                        "G-impl-obj", "D-tser-obj", "D-tpar-obj"},
                R("D-bs-url", P(415, 85, 680, 85, 680, 200, 692, 200),
                  "G-impl-obj", P(150, 172, 160, 172, 160, 335, 690, 335, 690, 300),
                  "D-tser-obj", P(45, 620, 20, 620, 20, 185, 45, 185),
                  "D-tpar-obj", P(700, 636, 700, 690, 10, 690, 10, 175, 45, 175),
                  "D-fac-tpar", P(600, 515, 640, 515, 640, 600),
                  "G-fac-impl", P(690, 300, 690, 340, 710, 340, 710, 460)));
    }

    void universityArtefacts() {
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, "UniversityArtefacts", pk.get("UniversityArtefacts"));
        cdraw(d, 1.0, new String[][]{
                {"Course", "5", "50", "noops"}, {"UserType", "250", "10"}, {"User", "450", "10", "noops"},
                {"CourseOffering", "5", "210", "noops"}, {"Professor", "250", "210", "noops"}, {"DayOfWeek", "5", "470"},
                {"Schedule", "250", "470", "noops"}, {"Student", "520", "250", "noops"}, {"Classification", "560", "520"},
                {"FullTimeClassification", "460", "640", "noops"}, {"PartTimeClassification", "700", "640", "noops"},
                {"CourseOfferingList", "250", "640"}},
                new String[]{"A-course-pre", "A-course-off", "A-off-prof", "A-user-prof", "A-user-stud", "A-primary", "A-alt", "A-stud-sch",
                        "A-stud-cls", "G-FullTimeClassification", "G-PartTimeClassification", "D-off-dow", "D-user-type", "D-list-off"},
                R("A-primary", P(120, 395, 120, 500, 250, 500), "A-alt", P(160, 395, 160, 540, 250, 540)));
    }

    /** Fig. 5.2.19 (after the subtype metamorphosis of fig. 5.2.23) */
    void studentDiagram() {
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, "Student", pk.get("UniversityArtefacts"));
        cdraw(d, 1.0, new String[][]{
                {"Student", "5", "5"}, {"Classification", "760", "20"}, {"FullTimeClassification", "600", "140"}, {"PartTimeClassification", "860", "140"}},
                new String[]{"A-stud-cls", "G-FullTimeClassification", "G-PartTimeClassification"}, null);
    }

    /** Fig. 5.2.23 */
    void designVopc() {
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, "Design VOPC RegisterForCourses", collab.get("DesignRegisterForCourses"));
        cdraw(d, 1.0, new String[][]{
                {"RegistrationForm", "5", "5"}, {"MainMenuForm", "420", "5"}, {"RegistrationController", "720", "5"}, {"IDBAccess", "1330", "180", "ball"},
                {"CourseOffering", "5", "320"}, {"Schedule", "330", "330"}, {"Student", "760", "270"}, {"Classification", "420", "520"},
                {"FullTimeClassification", "300", "740"}, {"PartTimeClassification", "620", "740"}, {"CourseOfferingList", "1080", "690"}},
                new String[]{"A-menu-rform", "A-menu-rctrl", "A-rform-rctrl", "A-rctrl-off", "A-rctrl-stud", "A-rctrl-dba", "A-primary", "A-alt",
                        "A-stud-sch", "A-stud-cls", "G-FullTimeClassification", "G-PartTimeClassification", "D-rform-off", "D-idb-list", "D-idb-stud", "D-list-off"},
                R("A-primary", P(330, 380, 225, 380), "A-alt", P(400, 330, 400, 290, 200, 290, 200, 320),
                  "A-menu-rform", P(460, 68, 460, 100, 392, 100), "A-menu-rctrl", P(530, 68, 530, 100, 720, 100),
                  "A-rform-rctrl", P(392, 150, 720, 150), "A-rctrl-off", P(730, 118, 730, 240, 65, 240, 65, 320),
                  "G-FullTimeClassification", P(430, 558, 430, 740), "G-PartTimeClassification", P(470, 558, 470, 640, 700, 640, 700, 740)));   // generalization: from = general
    }

    // ------------------------------------------------------------------ 5.2.20 state machine
    IConstraintElement guard(String text) {
        IConstraintElement c = b.f.createConstraintElement();
        ICompositeValueSpecification v = b.f.createCompositeValueSpecification();
        v.setValue(text);
        c.setSpecification(v);
        return c;
    }

    IActivity act(String text) { return b.named(b.f.createActivity(), text); }

    ITransition2 tr(IModelElement from, IModelElement to, String trigger, String guard, String effect) {
        ITransition2 t = b.f.createTransition2();
        t.setFrom(from);
        t.setTo(to);
        if (trigger != null) {
            ICallTrigger ct = b.f.createCallTrigger();
            IOperation op = null;
            for (IOperation o : dc.get("CourseOffering").toOperationArray()) if (trigger.equals(o.getName())) op = o;
            if (op != null) ct.setOperation(op);
            String[][] shown = {{"addSchedule", "addSchedule(sch)"}, {"removeSchedule", "removeSchedule(sch)"}, {"setNumber", "setNumber(n)"}};
            String label = trigger;
            for (String[] x : shown) if (x[0].equals(trigger)) label = x[1];
            ct.setName(label);
            t.addTrigger(ct);
        }
        if (guard != null) t.setGuard(guard(guard));
        if (effect != null) t.setEffect(act(effect));
        return t;
    }

    void lifeCycle() {
        IClass co = dc.get("CourseOffering");
        IStateMachine sm = b.named(b.f.createStateMachine(), "LifeCycle");
        co.addChild(sm);
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_STATE_DIAGRAM, "LifeCycle", sm);
        b.k = 1.0;
        IInitialPseudoState init = b.f.createInitialPseudoState(); sm.addChild(init);
        IFinalState2 fin = b.f.createFinalState2(); sm.addChild(fin);
        IState2 active = b.named(b.f.createState2(), "Active"); sm.addChild(active);
        IRegion ar = active.createRegion();
        IInitialPseudoState ai = ar.createInitialPseudoState();
        IState2 open = b.named(ar.createState2(), "Open");
        IState2 closed = b.named(ar.createState2(), "Closed");
        IState2 canc = b.named(ar.createState2(), "Cancelled");
        IChoice ch1 = ar.createChoice(), ch2 = ar.createChoice();
        closed.setEntry(act("isClosed = TRUE"));
        canc.setEntry(act("isCancelled = TRUE"));
        ITransition2 i1 = tr(active, active, "getNumber", null, "return number");
        ITransition2 i2 = tr(active, active, "setNumber", null, "number = n");
        try { ar.addInternalTransition(i1); ar.addInternalTransition(i2); i1.setKind(ITransition2.KIND_INTERNAL); i2.setKind(ITransition2.KIND_INTERNAL); }
        catch (Throwable t) { b.log("WARN internal Active: " + t); }
        IRegion or = open.createRegion();
        ITransition2 i3 = tr(open, open, "removeSchedule", "numStudent > 0", "numStudent--; roster=roster->excluding(sch)");
        try { or.addInternalTransition(i3); i3.setKind(ITransition2.KIND_INTERNAL); } catch (Throwable t) { b.log("WARN internal Open: " + t); }

        Map<IModelElement, IDiagramElement> s = new HashMap<IModelElement, IDiagramElement>();
        s.put(active, b.shape(d, active, 165, 5, 725, 460));
        s.put(init, b.shape(d, init, 35, 205, 25, 25));
        s.put(fin, b.shape(d, fin, 30, 385, 30, 30));
        s.put(ai, b.inner(d, s.get(active), ai, 515, 37, 25, 25));
        s.put(open, b.inner(d, s.get(active), open, 205, 87, 640, 130));
        s.put(ch1, b.inner(d, s.get(active), ch1, 185, 280, 40, 30));
        s.put(ch2, b.inner(d, s.get(active), ch2, 503, 375, 55, 30));
        s.put(closed, b.inner(d, s.get(active), closed, 197, 367, 195, 60));
        s.put(canc, b.inner(d, s.get(active), canc, 635, 362, 210, 60));
        Object[][] ts = {
                {init, active, null, null, "numStudents=0;\nisCancelled=FALSE;\nisClosed=FALSE", P(47, 230, 47, 330, 165, 330)},
                {active, fin, "destroy", null, null, P(165, 397, 60, 397)},
                {ai, open, null, null, null, null},
                {open, ch1, "addSchedule", null, "numStudents++;\nroster=roster->including(sch)", P(213, 217, 213, 282)},
                {ch1, open, null, "numStudents < 10", null, P(225, 293, 510, 293, 510, 217)},
                {ch1, closed, null, "else", null, P(213, 307, 213, 367)},
                {open, ch2, "close", null, null, P(530, 217, 530, 375)},
                {ch2, closed, null, "numStudent > 3", null, P(503, 392, 392, 392)},
                {ch2, canc, null, "else", null, P(558, 392, 635, 392)},
                {active, canc, "cancel", "!isCancelled", null, P(885, 300, 637, 300, 637, 362)}};
        for (Object[] t : ts) {
            ITransition2 x = tr((IModelElement) t[0], (IModelElement) t[1], (String) t[2], (String) t[3], (String) t[4]);
            IDiagramElement c = b.tryConnect(d, x, s.get(t[0]), s.get(t[1]));
            if (c != null) { if (t[5] != null) route(c, (double[]) t[5]); c.setRequestResetCaption(true); }
        }
        INOTE note = b.f.createNOTE();
        note.setDescription("другие геттеры и сеттеры аналогично");
        b.shape(d, note, 5, 5, 105, 85);
    }

    // ------------------------------------------------------------------ 5.2.22 activity (variants 2 and 3)
    void createScheduleActivity() {
        IClass rf = dc.get("RegistrationForm");
        IActivity a = b.named(b.f.createActivity(), "createSchedule");
        rf.addChild(a);
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ACTIVITY_DIAGRAM, "createSchedule", a);
        b.k = 1.0;
        IInitialNode in = b.f.createInitialNode(); a.addChild(in);
        IActivityAction a1 = b.named(b.f.createActivityAction(), "rctrl.createSchedule(sem);\nself.displayBlankSchedule();\ncOff = rctrl.getCourseOfferingList()");
        a.addChild(a1);
        ILoopNode loop = b.named(b.f.createLoopNode(), "Loop");
        a.addChild(loop);
        loop.setTestedFirst(true);
        IActivityAction setup = b.named(loop.createActivityAction(), "i = 0");
        IDecisionNode test = loop.createDecisionNode();
        IActivityAction body = b.named(loop.createActivityAction(), "self.displayCourseString(cOff[i].toJSON());\ni++");
        try { loop.addSetupPart(setup); loop.addTest(test); loop.addBodyPart(body); } catch (Throwable t) { b.log("WARN loop parts: " + t); }
        IActivityFinalNode fin = b.f.createActivityFinalNode(); a.addChild(fin);
        IDiagramElement sIn = b.shape(d, in, 178, 90, 25, 25);
        IDiagramElement s1 = b.shape(d, a1, 30, 140, 320, 65);
        IDiagramElement sl = b.shape(d, loop, 5, 228, 380, 360);
        IDiagramElement sSet = b.inner(d, sl, setup, 148, 300, 90, 45);
        IDiagramElement sTest = b.inner(d, sl, test, 172, 405, 30, 30);
        IDiagramElement sBody = b.inner(d, sl, body, 40, 510, 300, 55);
        IDiagramElement sFin = b.shape(d, fin, 178, 610, 30, 30);
        b.tryConnect(d, b.flow(in, a1, null), sIn, s1);
        b.tryConnect(d, b.flow(a1, setup, null), s1, sSet);
        b.tryConnect(d, b.flow(setup, test, null), sSet, sTest);
        b.tryConnect(d, b.flow(test, body, "i < cOff.length()"), sTest, sBody);
        IDiagramElement back = b.tryConnect(d, b.flow(body, test, null), sBody, sTest);
        if (back != null) route(back, 70, 510, 70, 420, 172, 420);
        IDiagramElement out = b.tryConnect(d, b.flow(test, fin, "else"), sTest, sFin);
        if (out != null) route(out, 202, 420, 370, 420, 370, 625, 208, 625);
        INOTE note = b.f.createNOTE();
        note.setDescription("RegistrationForm::createSchedule(sem):void");
        b.shape(d, note, 15, 5, 560, 55);   // wide note: keeps the export watermark off the final node
    }

    // ------------------------------------------------------------------ 5.3 database schema
    void schema() {
        IModelElement sp = pack(design, "Database Schema", "schema");
        Map<String, IClass> t = new LinkedHashMap<String, IClass>();
        String[][] tables = {
                {"TableStaticValues", "studentNextAvailableId:long"},
                {"TableProfessor", "PK name:string", "FK login:string", "academicDegree:string", "phone1:string", "phone2:string"},
                {"TableUser", "PK login:string", "passwordHash1:long", "passwordHash2:long", "type:byte"},
                {"TableCourseRequisites", "PK FK preCourse:string", "PK FK postCourse:string"},
                {"TableCourse", "PK code:string", "description:string", "duration:byte"},
                {"TableCourseOffering", "PK number:long", "FK code:string", "FK professorName:string", "day:byte", "pair:byte", "semester:byte",
                        "isCanceled:boolean=false", "isClosed:boolean=false"},
                {"TableStudent", "PK id:long", "FK login:string", "address:string", "classification:byte", "graduateDate:Date", "maxNumCourses:byte",
                        "name:string", "phone1:string", "phone2:string"},
                {"TablePrimaryCourse", "PK FK courseOffering:long", "PK FK schedule:long"},
                {"TableSchedule", "PK id:long", "FK altCourse1:long", "FK altCourse2:long", "FK student:long", "semester:byte"}};
        for (String[] tb : tables) {
            IClass c = b.named(b.f.createClass(), tb[0]);
            c.addStereotype("table");
            b.adopt(sp, c);
            t.put(tb[0], c);
            for (int i = 1; i < tb.length; i++) {
                String spec = tb[i];
                java.util.List<String> st = new java.util.ArrayList<String>();
                while (spec.startsWith("PK ") || spec.startsWith("FK ")) { st.add(spec.substring(0, 2)); spec = spec.substring(3); }
                String init = null;
                int eq = spec.indexOf('=');
                if (eq > 0) { init = spec.substring(eq + 1); spec = spec.substring(0, eq); }
                String[] nt = spec.split(":");
                IAttribute a = c.createAttribute();
                a.setName(nt[0]);
                a.setVisibility(IAttribute.VISIBILITY_PUBLIC);
                if ("Date".equals(nt[1])) a.setType(mw("Date", "util")); else a.setType(nt[1]);
                if (init != null) a.setInitialValue(init);
                try { IMultiplicity md = b.f.createMultiplicity(); a.setMultiplicityDetail(md); md.setUnique(false); } catch (Throwable e) { b.log("WARN unique"); }
                for (String x : st) try { a.addStereotype(x); } catch (Throwable e) { b.log("WARN attr stereotype " + x + ": " + e.getMessage()); }
            }
        }
        dc.putAll(t);
        // key, from, fromMult, fromRole, to, toMult, toRole, composite-at-from, stereotype
        String[][] links = {
                {"T1", "TableUser", "1", "", "TableProfessor", "0..1", "", "y", "non identifying"},
                {"T2", "TableUser", "1", "", "TableStudent", "0..1", "", "y", "non identifying"},
                {"T3", "TableCourse", "1", "pre", "TableCourseRequisites", "*", "", "y", "identifying"},
                {"T4", "TableCourse", "1", "post", "TableCourseRequisites", "*", "", "y", "identifying"},
                {"T5", "TableProfessor", "0..1", "", "TableCourseOffering", "*", "", "n", "non identifying"},
                {"T6", "TableCourse", "1", "", "TableCourseOffering", "*", "", "n", "non identifying"},
                {"T7", "TableCourseOffering", "1", "", "TablePrimaryCourse", "*", "", "n", "identifying"},
                {"T8", "TableSchedule", "1", "", "TablePrimaryCourse", "0..4", "", "y", "identifying"},
                {"T9", "TableCourseOffering", "0..1", "alt1", "TableSchedule", "*", "", "n", "non identifying"},
                {"T10", "TableCourseOffering", "0..1", "alt2", "TableSchedule", "*", "", "n", "non identifying"},
                {"T11", "TableStudent", "1", "", "TableSchedule", "*", "", "y", "non identifying"}};
        for (String[] l : links) {
            IAssociation as = b.f.createAssociation();
            as.setFrom(t.get(l[1]));
            as.setTo(t.get(l[4]));
            IAssociationEnd fe = (IAssociationEnd) as.getFromEnd(), te = (IAssociationEnd) as.getToEnd();
            fe.setMultiplicity(l[2]);
            if (!l[3].isEmpty()) fe.setName(l[3]);
            te.setMultiplicity(l[5]);
            fe.setNavigable(IAssociationEnd.NAVIGABLE_NAV_UNSPECIFIED);
            te.setNavigable(IAssociationEnd.NAVIGABLE_NAV_UNSPECIFIED);
            if ("y".equals(l[7])) fe.setAggregationKind(IAssociationEnd.AGGREGATION_KIND_COMPOSITED);
            try { as.addStereotype(l[8]); } catch (Throwable e) { b.log("WARN assoc stereotype: " + e.getMessage()); }
            rel.put(l[0], as);
        }
        IDiagramUIModel d = freshDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_CLASS_DIAGRAM, "DataBase Schema", sp);
        cdraw(d, 1.0, new String[][]{
                {"TableStaticValues", "5", "5"}, {"TableProfessor", "300", "5"}, {"TableUser", "580", "60"},
                {"TableCourseRequisites", "5", "120"}, {"TableCourse", "60", "290"}, {"TableCourseOffering", "270", "235"},
                {"TableStudent", "560", "285"}, {"TablePrimaryCourse", "5", "520"}, {"TableSchedule", "330", "520"}},
                new String[]{"T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11"},
                R("T3", P(60, 330, 30, 330, 30, 215), "T4", P(200, 330, 230, 330, 230, 215),
                  "T6", P(130, 380, 130, 400, 270, 400), "T7", P(310, 420, 310, 470, 130, 470, 130, 520),
                  "T8", P(400, 610, 400, 640, 130, 640, 130, 590),
                  "T9", P(480, 420, 480, 440, 540, 440, 540, 520), "T10", P(330, 420, 330, 500, 350, 500, 350, 520),
                  "T11", P(700, 470, 700, 560, 560, 560)));
    }
}
