package com.ad4game.admobadapter;

import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.libraries.ads.mobile.sdk.common.Image;

final class AdFullNativeMappedImage extends com.google.android.gms.ads.formats.NativeAd.Image {
    @Nullable private final Image image;

    AdFullNativeMappedImage(@Nullable Image image) {
        this.image = image;
    }

    @Nullable
    @Override
    public Drawable getDrawable() {
        return image == null ? null : image.getDrawable();
    }

    @Nullable
    @Override
    public Uri getUri() {
        return image == null ? null : image.getUri();
    }

    @Override
    public double getScale() {
        return image == null ? 1.0 : image.getScale();
    }
}
