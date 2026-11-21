package be.alterra.morphehelper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Circular app/source image with a gradient monogram fallback.
 *
 * <p>Icons are downloaded from the URLs in the Awesome Morphe index and cached
 * on disk. Search terms are never sent; only the image URL is fetched.</p>
 */
final class PatchIconView extends FrameLayout {

    private static final LruCache<String, Bitmap> MEMORY =
            new LruCache<String, Bitmap>(12 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private final TextView monogram;
    private final ImageView image;
    private final int targetPx;
    private String currentUrl = "";

    PatchIconView(Context context, int sizeDp) {
        super(context);
        ThemePalette palette = ThemeManager.palette(context);
        setBackground(M3Ui.brandGradient(context, palette, sizeDp / 2));

        monogram = M3Ui.text(context, "?", Math.max(11, sizeDp * 4 / 10),
                palette.onPrimary, 700);
        monogram.setGravity(Gravity.CENTER);
        addView(monogram, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setVisibility(View.GONE);
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        targetPx = (int) (sizeDp * context.getResources()
                .getDisplayMetrics().density);
    }

    void bind(String url, String label) {
        String next = url == null ? "" : url.trim();
        currentUrl = next;
        image.setVisibility(View.GONE);
        image.setImageDrawable(null);
        monogram.setVisibility(View.VISIBLE);
        monogram.setText(initial(label));
        setContentDescription(label == null ? "" : label);
        if (next.isEmpty()) {
            return;
        }

        Bitmap cached = MEMORY.get(next);
        if (cached != null) {
            show(cached);
            return;
        }

        final String requested = next;
        EXECUTOR.execute(() -> {
            Bitmap bitmap = loadBitmap(getContext(), requested, targetPx);
            if (bitmap == null) {
                return;
            }
            MEMORY.put(requested, bitmap);
            MAIN.post(() -> {
                if (requested.equals(currentUrl)) {
                    show(bitmap);
                }
            });
        });
    }

    private void show(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        image.setImageBitmap(bitmap);
        image.setVisibility(View.VISIBLE);
        monogram.setVisibility(View.GONE);
    }

    private static String initial(String label) {
        String value = label == null ? "" : label.trim();
        if (value.isEmpty()) {
            return "?";
        }
        int end = value.offsetByCodePoints(0, 1);
        return value.substring(0, end).toUpperCase(Locale.getDefault());
    }

    private static Bitmap loadBitmap(Context context, String url, int targetPx) {
        File cacheDir = new File(context.getCacheDir(), "patch-icons");
        File cacheFile = new File(cacheDir, hash(url));
        byte[] bytes = readFile(cacheFile);
        if (bytes == null) {
            bytes = download(url);
            if (bytes == null) {
                return null;
            }
            writeFile(cacheFile, bytes);
        }
        return decode(bytes, targetPx);
    }

    private static byte[] download(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Morphe-Download-Helper/3.2")
                .build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            return body == null ? null : body.bytes();
        } catch (Exception error) {
            return null;
        }
    }

    private static Bitmap decode(byte[] bytes, int targetPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        int target = Math.max(1, targetPx * 2);
        while (largest / sample > target) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static byte[] readFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
            return offset == bytes.length ? bytes : null;
        } catch (IOException error) {
            return null;
        }
    }

    private static void writeFile(File file, byte[] bytes) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        File temporary = new File(file.getPath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(bytes);
        } catch (IOException error) {
            temporary.delete();
            return;
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
        }
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format(Locale.US, "%02x", b));
            }
            return hex.toString();
        } catch (Exception error) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
