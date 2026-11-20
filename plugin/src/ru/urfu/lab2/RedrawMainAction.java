package ru.urfu.lab2;

import com.vp.plugin.action.VPAction;
import com.vp.plugin.action.VPActionController;
import com.vp.plugin.model.IModelElement;

import java.util.Map;

/** Deletes and redraws the "Main" use case diagram from the existing stage-1 model elements. */
public class RedrawMainAction implements VPActionController {
    public void update(VPAction action) {}

    public void performAction(VPAction action) {
        final B b = new B();
        b.step("redraw Main", () -> {
            Map<String, IModelElement> m = Stage1Action.existing(b);
            Stage1Action.drawMain(b, m);
        });
        b.finish("Redraw Main");
    }
}
