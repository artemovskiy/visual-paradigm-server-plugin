package ru.urfu.lab2;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.model.IInteractionOperand;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;

/** Exercises 4.2–4.4: sequence diagrams. Pilot: Login Basic Flow (fig. 4.2.2). Re-runnable. */
public class Stage2SeqAction implements VPActionController {
    public void update(VPAction action) {}

    public void performAction(VPAction action) {
        final B b = new B();
        all(b);
        b.finish("Stage 2b (sequences)");
    }

    static void all(B b) {
        b.step("Login Basic Flow", () -> loginBasicFlow(b));
        b.step("DisplayCourseOfferings Subflow", () -> displayCourseOfferings(b));   // before its ref users
        for (String n : new String[]{"UpdateSchedule Subflow", "DeleteSchedule Subflow", "SubmitSchedule Subflow"})
            b.step(n, () -> placeholder(b, n));
        b.step("CreateSchedule Subflow", () -> createSchedule(b));
        b.step("RegisterForCourses Basic Flow", () -> registerBasicFlow(b));
        b.step("TransferSchedulesData Subflow", () -> transferSchedulesData(b));
    }

    static final String[] NONE = new String[0];

    /** Subflows of other variants: empty diagrams so the refs in RegisterForCourses Basic Flow resolve. */
    static void placeholder(B b, String name) {
        Seq s = new Seq(b, name, b.need("Collaboration", "RegisterForCourses"), 1.0, 200);
        s.done();
    }

    /** Fig. 4.3.5 (variants 1 and 3). */
    static void displayCourseOfferings(B b) {
        Seq s = new Seq(b, "DisplayCourseOfferings Subflow", b.need("Collaboration", "RegisterForCourses"), 1.5, 250);
        s.lifeline("rform", "rform", "RegistrationForm", 110, 5, false);
        s.lifeline("rctrl", "rctrl", "RegistrationController", 332, 5, false);
        s.lifeline("cOff", "cOff[i]", "CourseOffering", 553, 5, true);
        s.call("rform", "rctrl", "RegistrationController", "getCourseOfferingList", 75);
        s.reply("rctrl", "rform", "cOff[]", 100);
        IMessage a = s.call("rform", "cOff", "CourseOffering", "toJSON", 190);
        IMessage c = s.self("rform", "RegistrationForm", "displayCourseString", 205);
        s.fragment("loop", null, 50, 650, new double[]{128, 245}, new String[]{"for all cOff[i]"}, new IMessage[][]{{a, c}});
        s.done();
    }

    /** Fig. 4.3.4 (variants 1 and 3). */
    static void createSchedule(B b) {
        Seq s = new Seq(b, "CreateSchedule Subflow", b.need("Collaboration", "RegisterForCourses"), 1.2, 650);
        s.actor("st", "Студент", 150);
        s.lifeline("rform", "rform", "RegistrationForm", 288, 5, false);
        s.lifeline("rctrl", "rctrl", "RegistrationController", 515, 5, false);
        s.lifeline("dao", "dao", "DataAccess", 740, 5, false);
        s.lifeline("curStud", "curStud", "Student", 1053, 5, false);
        s.lifeline("cOff", "cOff[i]", "CourseOffering", 905, 180, true);
        s.lifeline("sch", "sch", "Schedule", 820, 528, false);
        java.util.List<IMessage> in = new java.util.ArrayList<IMessage>();
        in.add(s.call("st", "rform", "RegistrationForm", "createSchedule", 158));
        in.add(s.call("rform", "rctrl", "RegistrationController", "createSchedule", 172));
        in.add(s.call("rctrl", "dao", "DataAccess", "getCourseOfferingList", 186));
        in.add(s.create("dao", "cOff", "new", 203));
        in.add(s.self("rform", "RegistrationForm", "displayBlankSchedule", 225));
        in.add(s.call("st", "rform", "RegistrationForm", "selectPrimaryCourse", 382));
        in.add(s.call("st", "rform", "RegistrationForm", "selectAltCourse", 420));
        in.add(s.call("st", "rform", "RegistrationForm", "submitSelectedCourses", 458));
        in.add(s.call("rform", "rctrl", "RegistrationController", "submitSelectedCourses", 478));
        in.add(s.call("rctrl", "curStud", "Student", "createSchedule", 500));
        in.add(s.create("curStud", "sch", "new", 545));
        in.add(s.call("rctrl", "dao", "DataAccess", "updateStudent", 578));
        in.add(s.self("rform", "RegistrationForm", "displayMessage", 600));
        s.fragment("opt", null, 110, 1132, new double[]{80, 640},
                new String[]{"учебный план на текущий семестр у студента отсутствует"}, new IMessage[][]{in.toArray(new IMessage[0])});
        s.ref("DisplayCourseOfferings Subflow", null, new String[]{"rform", "rctrl", "dao", "cOff"}, 245, 998, 268, 345, s.last[0]);
        s.done();
    }

    /** Fig. 4.3.3 (all variants). */
    static void registerBasicFlow(B b) {
        Seq s = new Seq(b, "RegisterForCourses Basic Flow", b.need("Collaboration", "RegisterForCourses"), 1.4, 800);
        s.actor("st", "Студент", 40);
        s.lifeline("menu", "menu", "MainMenuForm", 158, 5, false);
        s.lifeline("rctrl", "rctrl", "RegistrationController", 418, 5, false);
        s.lifeline("dao", "dao", "DataAccess", 603, 5, false);
        s.lifeline("curStud", "curStud", "Student", 705, 178, false);
        s.lifeline("rform", "rform", "RegistrationForm", 298, 382, false);
        s.call("st", "menu", "MainMenuForm", "registerForCourses", 85);
        s.call("menu", "rctrl", "RegistrationController", "registerForCourses", 93);
        s.self("rctrl", "RegistrationController", "isRegistrationOpened", 105);
        IMessage o1 = s.call("rctrl", "dao", "DataAccess", "getStudentByUser", 175);
        IMessage o2 = s.create("dao", "curStud", "new", 197);
        IMessage o3 = s.reply("dao", "rctrl", "curStud", 208);
        IMessage o4 = s.call("rctrl", "curStud", "Student", "hasScheduleForSemester", 230);
        s.fragment("opt", new String[]{"menu", "rctrl", "dao", "curStud"}, 165, 778, new double[]{138, 250},
                new String[]{"rctrl.isRegistrationOpened(s)"}, new IMessage[][]{{o1, o2, o3, o4}});
        s.reply("rctrl", "menu", "result", 258);
        IMessage e1 = s.self("menu", "MainMenuForm", "displayError", 320);
        IMessage e2 = s.create("menu", "rform", "open", 398);
        s.fragment("alt", null, 0, 780, new double[]{268, 368, 795},
                new String[]{"!rctrl.registerForCourses(curUser)", "else"}, new IMessage[][]{{e1}, {e2}});
        IInteractionOperand elseOp = s.last[1];
        s.fragment(elseOp, "alt", new String[]{"st", "menu", "rform", "rctrl", "dao", "curStud"}, 8, 728, new double[]{427, 519, 607, 696, 785},
                new String[]{"создаётся учебный план", "изменяется учебный план", "удаляется учебный план", "утверждается учебный план"}, null);
        String[] subs = {"CreateSchedule Subflow", "UpdateSchedule Subflow", "DeleteSchedule Subflow", "SubmitSchedule Subflow"};
        double[] ry = {437, 530, 623, 711};
        IInteractionOperand[] inner = s.last;
        for (int i = 0; i < 4; i++)
            s.ref(subs[i], null, new String[]{"rform", "rctrl", "dao", "curStud"}, 283, 720, ry[i], ry[i] + 64, inner[i]);
        s.done();
    }

    /** Fig. 4.4.2 (all variants). */
    static void transferSchedulesData(B b) {
        Seq s = new Seq(b, "TransferSchedulesData Subflow", b.need("Collaboration", "CloseRegistration"), 1.15, 765);
        s.lifeline("crctrl", "crctrl", "CloseRegistrationController", 105, 5, false);
        s.lifeline("dao", "dao", "DataAccess", 288, 5, false);
        s.lifeline("stud", "studlist[i]", "Student", 435, 5, true);
        s.lifeline("sch", "studlist[i].schedule[s]", "Schedule", 595, 5, true);
        s.lifeline("cOff", "cOff[j]", "CourseOffering", 760, 5, true);
        s.lifeline("bs", "bs", "BillingSystem", 900, 5, false);
        s.actor("ext", "Расчётная система", 1060);
        s.call("crctrl", "dao", "DataAccess", "getStudentList", 100);
        s.call("crctrl", "bs", "BillingSystem", "startTransferData", 121);
        IMessage r = s.async("bs", "ext", "isReady", 207, 245);
        s.fragment("loop", new String[]{"cOff", "bs", "ext"}, 738, 1085, new double[]{133, 257},
                new String[]{"готовность к приёму данных не подтверждена"}, new IMessage[][]{{r}});
        s.loopBounds("1", "10");
        IMessage l1 = s.call("crctrl", "stud", "Student", "getName", 383);
        IMessage l2 = s.call("crctrl", "stud", "Student", "getAddress", 407);
        IMessage l3 = s.call("crctrl", "stud", "Student", "getScheduleCourses", 431);
        IMessage l4 = s.call("stud", "sch", "Schedule", "getCourseOfferingList", 441);
        IMessage l5 = s.call("crctrl", "bs", "BillingSystem", "sendStudentData", 463);
        IMessage j1 = s.call("bs", "cOff", "CourseOffering", "toJSON", 553);
        IMessage l6 = s.async("bs", "ext", "sendStudentData", 577, 615);
        IMessage f1 = s.call("crctrl", "bs", "BillingSystem", "finishTransferData", 665);
        IMessage f2 = s.async("bs", "ext", "endOfData", 675, 715);
        s.fragment("opt", null, 60, 1085, new double[]{263, 750}, new String[]{"bs.startTransferData()"}, new IMessage[][]{{f1, f2}});
        IInteractionOperand optOp = s.last[0];
        s.fragment(optOp, "loop", null, 70, 1075, new double[]{326, 645}, new String[]{"for all students in list"},
                new IMessage[][]{{l1, l2, l3, l4, l5, l6}});
        IInteractionOperand loopOp = s.last[0];
        s.fragment(loopOp, "loop", new String[]{"cOff", "bs"}, 698, 925, new double[]{480, 573}, new String[]{"for all cOff[j]"},
                new IMessage[][]{{j1}});
        s.done();
    }

    static void loginBasicFlow(B b) {
        IModelElement login = b.need("Collaboration", "Login");
        Seq s = new Seq(b, "Login Basic Flow", login, 1.2, 600);
        s.actor("user", "Пользователь", 40);
        s.lifeline("lform", "lform", "LoginForm", 200, 5, false);
        s.lifeline("lctrl", "lctrl", "LoginController", 548, 5, false);
        s.lifeline("dao", "dao", "DataAccess", 752, 5, false);
        s.lifeline("curUser", "curUser", "User", 887, 147, false);
        s.lifeline("menu", "menu", "MainMenuForm", 370, 342, false);

        s.call("user", "lform", "LoginForm", "login", 110);
        s.call("lform", "lctrl", "LoginController", "login", 120);
        s.call("lctrl", "dao", "DataAccess", "getUserByLogin", 128);
        IMessage m4 = s.create("dao", "curUser", "new", 172);
        IMessage m5 = s.reply("dao", "lctrl", "result", 184);
        IMessage m6 = s.call("lctrl", "curUser", "User", "checkPassword", 216);
        IMessage m7 = s.reply("curUser", "lctrl", "result", 243);
        s.fragment("opt", new String[]{"lform", "lctrl", "dao", "curUser"}, 205, 980,
                new double[]{138, 268}, new String[]{"найдена учётная запись по логину"},
                new IMessage[][]{{m4, m5, m6, m7}});

        IMessage m8 = s.create("lctrl", "menu", "open(curUser)", 367);
        IMessage m9 = s.call("menu", "curUser", "User", "getType", 395);
        IMessage m10 = s.call("lctrl", "lform", "LoginForm", "displayError", 467);
        s.fragment("alt", new String[]{"lform", "menu", "lctrl", "dao", "curUser"}, 95, 960,
                new double[]{282, 423, 528},
                new String[]{"curUser != NULL && curUser.checkPassword() == TRUE", "else"},
                new IMessage[][]{{m8, m9}, {m10}});
        s.destroy("lctrl", "lform", "close()", 545);
        s.done();
    }
}
