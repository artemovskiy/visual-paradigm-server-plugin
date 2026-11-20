package ru.urfu.lab2;
public class LoginOnly {
    public static Object run(B b) throws Exception {
        b.step("Login Basic Flow", () -> Stage2SeqAction.loginBasicFlow(b));
        return "ok";
    }
}
