package be.alterra.morphehelper;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class UpdateUserService extends IUpdateService.Stub {

    private static final String TAG = "MorpheUpdateService";
    private static final File STAGED =
            new File("/data/local/tmp/morphe-helper-update.apk");

    @Override
    public int install(String path) {
        try {
            File source = new File(path);
            if (!source.isFile() || !source.canRead()) {
                Log.e(TAG, "Update APK is not readable: " + path);
                return 255;
            }

            try (InputStream input = new FileInputStream(source);
                 OutputStream output = new FileOutputStream(STAGED)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            // The package manager reads the staged file as the system user.
            //noinspection ResultOfMethodCallIgnored
            STAGED.setReadable(true, false);

            Process process = Runtime.getRuntime().exec(
                    new String[]{"pm", "install", "-r", STAGED.getAbsolutePath()});
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int result = process.waitFor();
            Log.i(TAG, "Shizuku pm install result=" + result + " " + output);
            //noinspection ResultOfMethodCallIgnored
            STAGED.delete();
            return result;
        } catch (Exception error) {
            Log.e(TAG, "Shizuku update install failed", error);
            //noinspection ResultOfMethodCallIgnored
            STAGED.delete();
            return -1;
        }
    }
}
