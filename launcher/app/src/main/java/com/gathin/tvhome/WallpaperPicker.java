package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen grid of DCIM thumbnails. Each thumbnail is decoded at about 320 px on one background thread,
 * so a folder of phone photos costs a few MB, and they are dropped when the picker closes.
 */
final class WallpaperPicker {

    interface Picked {
        void onPicked(File file);
    }

    private static final int COLUMNS = 4;
    private static final int MAX = 60;

    static void show(Activity a, View anchor, List<File> pics, Picked picked) {
        float density = a.getResources().getDisplayMetrics().density;
        Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = d.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(0xF2000000));

        ScrollView scroll = new ScrollView(a);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (56 * density);
        col.setPadding(pad, (int) (30 * density), pad, pad);
        scroll.addView(col);

        TextView title = new TextView(a);
        title.setText(R.string.wallpaper_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setTypeface(ResourcesCompat.getFont(a, R.font.outfit));
        title.setFontVariationSettings("'wght' 300");
        title.setPadding(0, 0, 0, (int) (20 * density));
        col.addView(title);

        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        int gap = (int) (16 * density);
        int cellW = (screenW - 2 * pad - (COLUMNS - 1) * gap) / COLUMNS;
        int cellH = cellW * 9 / 16;

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        LinearLayout row = null;
        View first = null;
        int n = Math.min(MAX, pics.size());
        for (int i = 0; i < n; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(a);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = gap;
                col.addView(row, rlp);
            }
            File f = pics.get(i);
            FrameLayout cell = new FrameLayout(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cellW, cellH);
            if (i % COLUMNS != COLUMNS - 1) lp.setMarginEnd(gap);
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setBackground(frame(density, false));
            int inset = (int) (3 * density);
            cell.setPadding(inset, inset, inset, inset);
            ImageView img = new ImageView(a);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundColor(0xFF16171B);
            img.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 9 * density);
                }
            });
            img.setClipToOutline(true);
            cell.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            cell.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(frame(density, focused));
                v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(140).start();
            });
            cell.setOnClickListener(v -> {
                d.dismiss();
                picked.onPicked(f);
            });
            row.addView(cell, lp);
            if (first == null) first = cell;
            pool.execute(() -> {
                Bitmap bmp = thumb(f, cellW);
                if (bmp != null) main.post(() -> img.setImageBitmap(bmp));
            });
        }
        d.setOnDismissListener(x -> pool.shutdownNow());
        d.setContentView(scroll);
        d.show();
        if (first != null) first.requestFocus();
    }

    /** White ring only when focused; the inset keeps the picture from jumping. */
    private static GradientDrawable frame(float density, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(12 * density);
        g.setColor(Color.TRANSPARENT);
        if (focused) g.setStroke((int) (3 * density), Color.WHITE);
        return g;
    }

    private static Bitmap thumb(File f, int width) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private WallpaperPicker() {}
}
