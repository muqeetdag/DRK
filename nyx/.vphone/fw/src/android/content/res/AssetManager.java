package android.content.res;

/** AssetManager access to the APK's assets (opened from the unpacked dir). */
public class AssetManager {
    public java.io.InputStream open(String fileName) throws java.io.IOException {
        java.io.File root = new java.io.File(android.test.Env.resRoot, "assets");
        java.io.File file = new java.io.File(root, fileName);
        if (!file.exists()) throw new java.io.FileNotFoundException(fileName);
        return new java.io.FileInputStream(file);
    }

    public String[] list(String path) throws java.io.IOException { return new String[0]; }

    public void close() {}
}
