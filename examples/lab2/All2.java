package ru.urfu.lab2;
public class All2 {
    public static Object run(B b) throws Exception {
        if (b.find("Collaboration", "RegisterForCourses") == null) new Stage2Action().performAction(null);
        Stage2SeqAction.all(b);
        return "ok";
    }
}
