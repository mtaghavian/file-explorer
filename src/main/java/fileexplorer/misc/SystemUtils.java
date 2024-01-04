package fileexplorer.misc;

import java.io.*;

public class SystemUtils {

    public static Process proc = null;

    private static void read(StringBuilder sb, InputStream is) {
        try {
            int available = is.available();
            if (available > 0) {
                DataInputStream dis = new DataInputStream(is);
                byte b[] = new byte[available];
                dis.readFully(b);
                sb.append(new String(b));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void executeBash(StringBuilder sb, String addr) throws IOException {
        File cmdFile = new File("cmd.sh");
        String cmd = "/bin/bash";
        if (addr != null) {
            cmd = "cd " + addr + "\n" + cmd;
        }
        StreamUtils.writeString(cmd, cmdFile);
        proc = Runtime.getRuntime().exec("sh " + cmdFile.getPath(), null, new File("."));
        sb.append("/bin/bash process created!\n");
    }

    public static String executeInBash(String addr, String cmd) throws IOException {
        StringBuilder out = new StringBuilder();
        if (proc != null) {
            read(out, proc.getInputStream());
            read(out, proc.getErrorStream());

            if (!proc.isAlive()) {
                executeBash(out, addr);
            }

            if ("<check>".equals(cmd)) {
                // do nothing!
            } else if ("<end>".equals(cmd)) {
                proc.destroy();
            } else {
                proc.getOutputStream().write((cmd + "\n").getBytes());
                proc.getOutputStream().flush();

                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                read(out, proc.getInputStream());
                read(out, proc.getErrorStream());
            }
        } else {
            executeBash(out, addr);
            proc.getOutputStream().write((cmd + "\n").getBytes());
            proc.getOutputStream().flush();

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            read(out, proc.getInputStream());
            read(out, proc.getErrorStream());
        }
        return out.toString();
    }

    public static String executeSingleCommand(String addr, String cmd) {
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
