package com.example.health.ui.sport;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.TextView;

import com.example.health.R;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 图表自定义标记视图（MPAndroidChart MarkerView），在图表数据点上显示时间戳和运动时长。
 * 随手指滑动自动调整偏移量，确保不超出图表边界。
 */
public class CustomMarkerView extends MarkerView {

    private TextView tvContent;
    private List<Long> timestamps;
    private SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private MPPointF mOffset;

    public CustomMarkerView(Context context, int layoutResource, List<Long> timestamps) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.tvContent);
        this.timestamps = timestamps;
    }

    // runs every time the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int index = (int) e.getX();
        String timeStr = "";
        if (timestamps != null && index >= 0 && index < timestamps.size()) {
            timeStr = format.format(new Date(timestamps.get(index)));
        }
        
        tvContent.setText(String.format(Locale.getDefault(), "%s\n%.1f 分钟", timeStr, e.getY()));

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        if (mOffset == null) {
            // center the marker horizontally and vertically
            mOffset = new MPPointF(-(getWidth() / 2), -getHeight());
        }
        return mOffset;
    }

    @Override
    public void draw(Canvas canvas, float posX, float posY) {
        MPPointF offset = getOffsetForDrawingAtPoint(posX, posY);
        int saveId = canvas.save();
        // translate to the correct position and draw
        canvas.translate(posX + offset.x, posY + offset.y);
        draw(canvas);
        canvas.restoreToCount(saveId);
    }

    public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
        MPPointF offset = getOffset();
        Chart chart = getChartView();
        float width = getWidth();
        float height = getHeight();

        if (posX + offset.x < 0) {
            offset.x = - posX;
        } else if (chart != null && posX + width + offset.x > chart.getWidth()) {
            offset.x = chart.getWidth() - posX - width;
        }

        if (posY + offset.y < 0) {
            offset.y = - posY;
        } else if (chart != null && posY + height + offset.y > chart.getHeight()) {
            offset.y = chart.getHeight() - posY - height;
        }

        return offset;
    }
}
