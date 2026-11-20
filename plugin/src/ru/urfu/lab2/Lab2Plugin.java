package ru.urfu.lab2;

import com.vp.plugin.VPPlugin;
import com.vp.plugin.VPPluginInfo;

public class Lab2Plugin implements VPPlugin {
    public void loaded(VPPluginInfo info) { Server.start(info.getPluginDir()); }
    public void unloaded() { Server.stop(); }
}
