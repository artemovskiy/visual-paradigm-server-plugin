package ru.urfu.lab2;
public class S1 {
    public static Object run(B b) throws Exception {
        new Stage1Action().performAction(null);
        b.step("generalizations", () -> Stage1Action.drawGeneralizations(b, Stage1Action.existing(b)));
        return "ok";
    }
}
