package ru.urfu.lab2;
public class Var {
    public static Object run(B b) throws Exception {
        Seq.FLAG_NAME = 0; Seq.FLAG_SIG = 0; Seq.FLAG_CONN = 0;
        b.step("Login Basic Flow", () -> Stage2SeqAction.loginBasicFlow(b));
        return "ok";
    }
}
