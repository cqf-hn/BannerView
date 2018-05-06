package com.cqf.hn.banner;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;

import java.util.ArrayList;

import bannerview.mylibrary.BannerView;
import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.banner)
    BannerView mBanner;
    @BindView(R.id.activity_main)
    RelativeLayout mActivityMain;
    private int[] images = {
            R.mipmap.ic_test_0, R.mipmap.ic_test_1,
            R.mipmap.ic_test_2, R.mipmap.ic_test_3,
            R.mipmap.ic_test_4, R.mipmap.ic_test_5,
            R.mipmap.ic_test_6};
    private int mViewWidth;
    private int mViewHeight;
    private ArrayList<Bitmap> bitmaps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mBanner.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mBanner.getHeight() > 0 && mBanner.getWidth() > 0) {
                    mBanner.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    mViewWidth = mBanner.getWidth();
                    mViewHeight = mBanner.getHeight();
                    for (int i = 0; i < images.length; i++) {
                        addBitmap(MainActivity.this, images[i]);
                    }

                }
            }
        });
    }

    private void addBitmap(Context context, int resId) {
        Glide.with(context)
                .load(resId)
                .asBitmap()
                .dontAnimate()
                .into(new SimpleTarget<Bitmap>(mViewWidth, mViewHeight) {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {
                        if (resource.getHeight() > 0) {
                            Bitmap bitmap = Bitmap.createScaledBitmap(resource, mViewWidth, mViewHeight, true);
                            bitmaps.add(bitmap);
                            if(images.length == bitmaps.size()) {
                                mBanner.setBitmaps(bitmaps).start();
                            }
                        }
                    }
                });
    }
}
