package android.os;

/** Process identity + kill, used by the crash handler. */
public class Process {
    public static final int SYSTEM_UID = 1000;
    public static int myPid() { return 12345; }
    public static int myTid() { return 12345; }
    public static int myUid() { return 10000; }
    public static void killProcess(int pid) {
        android.test.Services.LOG.add("killProcess(" + pid + ")");
        android.test.Services.KILL_REQUESTED = true;
    }
    public static void sendSignal(int pid, int signal) {}
    public static final int FIRST_APPLICATION_UID = 10000;
}
