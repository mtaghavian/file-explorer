package fileexplorer.misc;

import java.io.File;
import java.io.FileOutputStream;

public class SystemUtils {

    public static String execute(String addr, String cmd) {
        File cmdFile = null, cmdOut = null;
        try {
            boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");
            cmdFile = new File("cmd-" + System.currentTimeMillis() + (windows ? ".bat" : ".sh"));
            cmdOut = new File(cmdFile.getPath() + ".txt");
            if (addr != null) {
                cmd = "cd " + addr + "\n" + cmd;
            }
            StreamUtils.writeString(cmd, cmdFile);
            Process proc = Runtime.getRuntime().exec(windows ? cmdFile.getPath() : ("sh " + cmdFile.getPath()), null, new File("."));
            FileOutputStream os = new FileOutputStream(cmdOut);
            StreamUtils.copy(proc.getInputStream(), os, false, false);
            StreamUtils.copy(proc.getErrorStream(), os, false, false);
            proc.waitFor();
            os.close();
            return StreamUtils.readString(cmdOut);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            StreamUtils.delete(cmdFile);
            StreamUtils.delete(cmdOut);
        }
    }
}
