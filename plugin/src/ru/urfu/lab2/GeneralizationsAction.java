package ru.urfu.lab2;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;

/** Adds the generalizations Professor/Student/Registrar -> User to the drawn Main diagram. */
public class GeneralizationsAction implements VPActionController {
    public void update(VPAction action) {}

    public void performAction(VPAction action) {
        final B b = new B();
        b.step("generalizations on Main", () -> Stage1Action.drawGeneralizations(b, Stage1Action.existing(b)));
        b.finish("Generalizations");
    }
}
