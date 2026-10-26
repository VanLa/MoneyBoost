package com.joyboost.moneyboost;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Optional project-owned button art. No advertiser assets or click handlers are modified. */
final class MoneyBoostNativeArtwork {
    final boolean unityBackground;
    private final Bitmap bitmap;
    private final byte[] chunk;
    MoneyBoostNativeArtwork(Resources resources, byte[] png, int left, int bottom, int right, int top, boolean unityBackground) {
        this.unityBackground=unityBackground;
        Bitmap source=png==null || png.length==0?null:BitmapFactory.decodeByteArray(png,0,png.length);
        if(source==null){bitmap=null;chunk=null;return;}
        // Keep the art's non-stretchable corners below the smallest supported CTA height.
        float factor=32*resources.getDisplayMetrics().density/source.getHeight();
        int w=Math.max(4,Math.round(source.getWidth()*factor)), h=Math.max(4,Math.round(source.getHeight()*factor));
        bitmap=Bitmap.createScaledBitmap(source,w,h,true);
        bitmap.setDensity(resources.getDisplayMetrics().densityDpi);
        if(source!=bitmap)source.recycle();
        int l=Math.max(1,Math.min(w/2-1,Math.round(left*factor)));
        int r=Math.max(1,Math.min(w/2-1,Math.round(right*factor)));
        int t=Math.max(1,Math.min(h/2-1,Math.round(top*factor)));
        int b=Math.max(1,Math.min(h/2-1,Math.round(bottom*factor)));
        ByteBuffer data=ByteBuffer.allocate(84).order(ByteOrder.nativeOrder());
        data.put((byte)1).put((byte)2).put((byte)2).put((byte)9);
        data.putInt(0).putInt(0);
        // Artwork borders describe stretching, not content padding; keep the CTA text inside its own view.
        data.putInt(0).putInt(0).putInt(0).putInt(0).putInt(0);
        data.putInt(l).putInt(w-r).putInt(t).putInt(h-b);
        for(int i=0;i<9;i++)data.putInt(1);
        chunk=data.array();
    }
    Drawable button(Resources resources) {
        return bitmap==null?null:new NinePatchDrawable(resources,bitmap,chunk,new Rect(),"MoneyBoost project CTA");
    }
}
