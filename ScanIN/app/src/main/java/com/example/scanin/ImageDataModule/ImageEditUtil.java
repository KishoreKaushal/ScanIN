package com.example.scanin.ImageDataModule;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import com.squareup.picasso.Picasso;

import org.opencv.core.Point;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class ImageEditUtil {
    public static String[] filterList = {"magic_filter", "gray_filter", "dark_magic_filter", "sharpen_filter"};

    static {
        System.loadLibrary("image-edit-util");
    }

    // check for filter_name == null;
    public static boolean isValidFilter(String filter_name){
        return Arrays.asList(ImageEditUtil.filterList).contains(filter_name);
    }

    public static int getFilterId (String filter_name) {
        return Arrays.asList(filterList).indexOf (filter_name);
    }

    public static String getFilterName(int filter_id){
        if(filter_id == -1) return "original_filter";
        return filterList[filter_id];
    }

    public static Bitmap ImageProxyToBitmap(Image image){
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            //U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    public static Map<Integer, PointF> convertArrayList2Map (ArrayList<Point> pts) {
        if (pts == null) return null;
        Map <Integer, PointF> res = new HashMap<>();
        res.put (0, new PointF((float) pts.get(0).x, (float) pts.get(0).y));
        res.put (1, new PointF((float) pts.get(1).x, (float) pts.get(1).y));
        // Polygon view uses a stupid order.
        res.put (2, new PointF((float) pts.get(3).x, (float) pts.get(3).y));
        res.put (3, new PointF((float) pts.get(2).x, (float) pts.get(2).y));
        return res;
    }

    public static ArrayList <Point> convertMap2ArrayList (Map <Integer, PointF> pts) {
        if (pts == null) return null;
        ArrayList <Point> res = new ArrayList<>();
        res.add (new Point(pts.get(0).x, pts.get(0).y));
        res.add (new Point(pts.get(1).x, pts.get(1).y));
        res.add (new Point(pts.get(3).x, pts.get(3).y));
        res.add (new Point(pts.get(2).x, pts.get(2).y));
        return res;
    }

    public static Map <Integer, PointF> scalePoints (Map <Integer, PointF> pts, float scale) {
        Map <Integer, PointF> res = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            PointF c = pts.get(i);
            res.put (i, new PointF(c.x * scale, c.y * scale));
        }
        return res;
    }

    public static ArrayList <Point> scalePoints (ArrayList <Point> pts, float scale) {
        ArrayList <Point> res = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            res.add (new Point (pts.get(i).x * scale, pts.get(i).y * scale));
        }
        return res;
    }

    public static float getScale (int width, int height) {
        float fx = (float) width / ImageData.MAX_SIZE;;
        float fy = (float) height / ImageData.MAX_SIZE;
        float scale = max (fx, fy);
        return scale;
    }

    // float operations can cause the crop to be slightly away from default_points.
    // So we check just to avoid crop if we can.
    public static boolean cropRequired (Map <Integer, PointF> pts, int width, int height) {
        if (pts == null) return false;
        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = ImageData.MAX_SIZE;
            height = (int) (width / bitmapRatio);
        } else {
            height = ImageData.MAX_SIZE;
            width = (int) (height * bitmapRatio);
        }
        for (int i = 0; i < 4; i++) {
            float x = pts.get(i).x;
            float y = pts.get(i).y;
            if ((x > 2 && x < width - 2) || (y > 2 && y < height - 2)) {
                return true;
            }
        }
        return false;
    }

    public static Map <Integer, PointF> getDefaultPoints (int width, int height) {
        Map <Integer, PointF> res = new HashMap<>();
        res.put (0, new PointF (0.0f, 0.0f));
        res.put (1, new PointF (width, 0.0f));
        res.put (3, new PointF (width, height));
        res.put (2, new PointF (0.0f, height));
        return res;
    }

    public static Map <Integer, PointF> rotateCropPoints (Map <Integer, PointF> points, int width, int height, int rotationConfig) {
        Map <Integer, PointF> res = new HashMap<>();
        //  0 1     2 0
        //  2 3     3 1
        if (rotationConfig == 1 || rotationConfig == 3) {
            height = width;
        }
        res.put (0, new PointF (height - points.get(2).y, points.get(2).x));
        res.put (1, new PointF (height - points.get(0).y, points.get(0).x));
        res.put (2, new PointF (height - points.get(3).y, points.get(3).x));
        res.put (3, new PointF (height - points.get(1).y, points.get(1).x));
        return res;
    }

    public static ArrayList <Point> rotateCropPoints (ArrayList <Point> points, int width, int height, int rotationConfig) {
        ArrayList <Point> res = new ArrayList<>();
        if (rotationConfig == 1 || rotationConfig == 3) {
            height = width;
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 3) % 4;
            res.add (new Point (height - points.get(j).y, points.get(j).x));
        }
        return res;
    }

    private static final String[] CONTENT_ORIENTATION = new String[] {
            MediaStore.Images.ImageColumns.ORIENTATION
    };

    protected static int getExifOrientation(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            ContentResolver contentResolver = context.getContentResolver();
            cursor = contentResolver.query(uri, CONTENT_ORIENTATION, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        } catch (RuntimeException ignored) {
            // If the orientation column doesn't exist, assume no rotation.
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // for next release https://developer.android.com/topic/performance/graphics/load-bitmap
    public static Bitmap loadBitmap (Context context, Uri fileName) {
        Bitmap temp = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Log.d("loadingImages", "api above P");
                temp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), fileName));
            } else {
                temp = MediaStore.Images.Media.getBitmap(context.getContentResolver(), fileName);
                Log.d("loadingImages", "api below P");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return temp;
    };

    public static Bitmap loadBitmap (Uri fileName) throws Exception {
        return (Bitmap) (Picasso.get().load(fileName).get());
    };

    public static native void getTestGray (long imgAddr, long grayImgAddr);

    public static native void getBestPoints (long imgAddr, long pts);

    public static native void cropImage(long imgAddr, long cropImgAddr, long pts, int interpolation);

    public static native void filterImage(long imgAddr, long filterImgAddr, int filterId);

    public static native void changeContrastAndBrightness(long imgAddr, long outputAddr, double alpha, int beta);
}
