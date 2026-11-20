package ru.urfu.lab2;

/** Rebuilds stage 2 classes (if missing) and Login Basic Flow, without the toolbar buttons. */
public class Smoke {
    public static Object run(B b) throws Exception {
        if (b.find("Collaboration", "RegisterForCourses") == null) {
            // Stage2Action.performAction needs a VPAction; it ignores it, so pass null
            new Stage2Action().performAction(null);
        }
        b.step("Login Basic Flow", () -> Stage2SeqAction.loginBasicFlow(b));
        return "classes=" + b.project.toAllLevelModelElementArray("Class").length;
    }
}
