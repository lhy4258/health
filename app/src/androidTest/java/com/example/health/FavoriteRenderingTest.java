package com.example.health;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.health.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class FavoriteRenderingTest {

    @Test
    public void itemFavoriteLayout_hasRequiredViews() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_favorite, null, false);
        assertNotNull(view.findViewById(R.id.tvFavoriteSource));
        assertNotNull(view.findViewById(R.id.tvFavoriteTime));
        assertNotNull(view.findViewById(R.id.tvFavoriteContent));
        assertNotNull(view.findViewById(R.id.tvFavoriteImageInfo));
    }
}

