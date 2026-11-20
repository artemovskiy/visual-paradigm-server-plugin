package ru.urfu.lab2;
public class S3 {
    public static Object run(B b) throws Exception {
        Stage3 s = new Stage3(b);
        b.step("5.1 model", s::model);
        b.step("5.1.1 Main", s::mainPackages);
        b.step("5.1.3 Design Use Case Realizations", s::designRealizations);
        b.step("5.1.4 Dependencies", s::dependencies);
        b.step("5.1.5 BusinessServices", s::businessServices);
        b.step("5.1.6 Deployment", s::deployment);
        b.step("5.2.1 Design Login", s::designLogin);
        b.step("5.2.2/4/5 Design Register", s::designRegister);
        b.step("5.2.3 Design Transfer", s::designTransfer);
        b.step("BSAccess collaboration", s::bsCollaboration);
        b.step("5.2.17 init", s::bsInit);
        b.step("5.2.18 sendStudentData", s::bsSend);
        b.step("5.2.14 BSAccess packages", s::bsPackages);
        b.step("5.2.15 BSAccess classes", s::bsClasses);
        b.step("UniversityArtefacts", s::universityArtefacts);
        b.step("5.2.19 Student", s::studentDiagram);
        b.step("5.2.20 LifeCycle", s::lifeCycle);
        b.step("5.2.22 createSchedule activity", s::createScheduleActivity);
        b.step("5.2.23 Design VOPC", s::designVopc);
        b.step("5.3.1 schema", s::schema);
        b.step("generalization set", s::fixSets);
        return "ok";
    }
}
