package com.example.health.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 环形热力图自定义 View，将 12 个时段（每段 2 小时）的运动数据用环形扇区可视化。
 * 支持触摸切换选中时段，显示该时段的运动类型详情，数据变化时带 500ms 动画过渡。
 * 分为 AM 版（0-12 时）和 PM 版（12-24 时），用于 SportFragment 的运动热力图展示。
 */
public class AnnularHeatMapView extends View {

    public static class HeatMapData {
        public double totalDuration;
        public Map<String, Double> typeDetails;

        public HeatMapData(double total, Map<String, Double> details) {
            this.totalDuration = total;
            this.typeDetails = details != null ? details : new HashMap<>();
        }
    }

    private static final int SECTOR_COUNT = 12;
    private static final int START_ANGLE = -90; // Top
    private static final int SWEEP_ANGLE = 30; // 360 / 12

    private Paint sectorPaint;
    private TextPaint textPaint;
    private RectF bounds;
    private List<HeatMapData> data;
    private List<Double> currentAnimatedValues;
    private int startHour = 0; // 0 or 12
    private int hoveredIndex = -1;
    
    // Animation for text fade in/out
    private ValueAnimator alphaAnimator;
    private AnimatorSet switchAnimatorSet;
    private int currentTextAlpha = 0;
    
    // Auto-hide feature
    private static final long AUTO_HIDE_DELAY = 4000;
    private final Runnable autoHideTask = () -> {
        if (hoveredIndex != -1) {
            startAlphaAnimation(0, () -> {
                hoveredIndex = -1;
                announceForAccessibility("信息面板已自动关闭");
            });
        }
    };

    // Colors
    private int colorEmpty = 0xFFEEEEEE;
    private int colorMin = 0xFFFFCDD2; // Red 100
    private int colorMax = 0xFFB71C1C; // Red 900

    // Optimization: Reuse arrays for color interpolation to avoid object creation in onDraw
    private final float[] hsvStart = new float[3];
    private final float[] hsvEnd = new float[3];
    private final float[] hsvResult = new float[3];

    // Cached text sizes
    private float titleTextSize;
    private float subtitleTextSize;
    private float detailTextSize;

    public AnnularHeatMapView(Context context) {
        this(context, null);
    }

    public AnnularHeatMapView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnnularHeatMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        sectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sectorPaint.setStyle(Paint.Style.STROKE);
        
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF333333); // Dark Gray
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        // Modern Sans-serif Font
        Typeface font = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        textPaint.setTypeface(font);
        
        // Shadow for better readability
        textPaint.setShadowLayer(3, 1, 1, 0x40CCCCCC); // Soft light gray shadow
        
        // Letter spacing optimization (API 21+)
        textPaint.setLetterSpacing(0.03f);

        // Pre-calculate text sizes to improve performance in onDraw
        titleTextSize = spToPx(16);
        subtitleTextSize = spToPx(14);
        detailTextSize = spToPx(12);

        bounds = new RectF();
        data = new ArrayList<>();
        for (int i = 0; i < SECTOR_COUNT; i++) {
            data.add(new HeatMapData(0, new HashMap<>()));
        }
        currentAnimatedValues = new ArrayList<>(Collections.nCopies(SECTOR_COUNT, 0.0));
    }

    public void setStartHour(int startHour) {
        this.startHour = startHour;
        invalidate();
    }

    public void setHeatMapData(List<HeatMapData> newData) {
        if (newData == null || newData.size() != SECTOR_COUNT) return;
        
        this.data = new ArrayList<>(newData);
        startAnimation();
    }

    private void startAnimation() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(500);
        
        final List<Double> oldValues = new ArrayList<>(currentAnimatedValues);
        
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            for (int i = 0; i < SECTOR_COUNT; i++) {
                double start = oldValues.get(i);
                double end = data.get(i).totalDuration;
                currentAnimatedValues.set(i, start + (end - start) * fraction);
            }
            invalidate();
        });
        animator.start();
    }
    
    private void startAlphaAnimation(int targetAlpha, @Nullable Runnable onEnd) {
        cancelAnimations();
        
        // Manage auto-hide timer
        if (targetAlpha > 0) {
            rescheduleAutoHide();
        } else {
            removeCallbacks(autoHideTask);
        }
        
        if (currentTextAlpha == targetAlpha) {
            if (onEnd != null) onEnd.run();
            return;
        }
        
        alphaAnimator = ValueAnimator.ofInt(currentTextAlpha, targetAlpha);
        alphaAnimator.setDuration(200);
        alphaAnimator.addUpdateListener(animation -> {
            currentTextAlpha = (int) animation.getAnimatedValue();
            invalidate();
        });
        if (onEnd != null) {
            alphaAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        alphaAnimator.start();
    }
    
    private void startSwitchAnimation(int newIndex) {
        cancelAnimations();
        rescheduleAutoHide(); // Reset timer on switch

        // Calculate fade out duration based on current alpha (faster if already partially faded)
        long fadeOutDuration = (long) (150 * (currentTextAlpha / 255f));
        
        // Phase 1: Fade Out
        ValueAnimator fadeOut = ValueAnimator.ofInt(currentTextAlpha, 0);
        fadeOut.setDuration(fadeOutDuration);
        fadeOut.addUpdateListener(animation -> {
            currentTextAlpha = (int) animation.getAnimatedValue();
            invalidate();
        });
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                hoveredIndex = newIndex;
                announceSelection(newIndex);
            }
        });

        // Phase 2: Fade In
        ValueAnimator fadeIn = ValueAnimator.ofInt(0, 255);
        fadeIn.setDuration(150);
        fadeIn.addUpdateListener(animation -> {
            currentTextAlpha = (int) animation.getAnimatedValue();
            invalidate();
        });

        switchAnimatorSet = new AnimatorSet();
        switchAnimatorSet.play(fadeIn).after(fadeOut);
        switchAnimatorSet.start();
    }
    
    private void cancelAnimations() {
        if (alphaAnimator != null && alphaAnimator.isRunning()) {
            alphaAnimator.cancel();
        }
        if (switchAnimatorSet != null && switchAnimatorSet.isRunning()) {
            switchAnimatorSet.cancel();
        }
    }
    
    private void rescheduleAutoHide() {
        removeCallbacks(autoHideTask);
        postDelayed(autoHideTask, AUTO_HIDE_DELAY);
    }
    
    private void announceSelection(int index) {
        if (index >= 0 && index < data.size()) {
            HeatMapData item = data.get(index);
            String timeRange = (startHour + index) + "点到" + (startHour + index + 1) + "点";
            String announcement = timeRange + ", 总时长" + String.format("%.0f", item.totalDuration) + "分钟";
            announceForAccessibility(announcement);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(autoHideTask);
        cancelAnimations();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Keep square aspect ratio based on width
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float radius = Math.min(width, height) / 2f;
        float strokeWidth = radius * 0.3f; // Ring thickness
        float padding = strokeWidth / 2f + 10f; // Padding for stroke

        sectorPaint.setStrokeWidth(strokeWidth);
        bounds.set(padding, padding, width - padding, height - padding);

        double maxVal = 0;
        for (Double val : currentAnimatedValues) {
            if (val > maxVal) maxVal = val;
        }

        // Draw sectors
        for (int i = 0; i < SECTOR_COUNT; i++) {
            double val = currentAnimatedValues.get(i);
            
            // Determine color
            if (val <= 0) {
                sectorPaint.setColor(colorEmpty);
            } else {
                float ratio = (float) (maxVal > 0 ? val / maxVal : 0);
                sectorPaint.setColor(interpolateColor(colorMin, colorMax, ratio));
            }

            // Draw arc
            canvas.drawArc(bounds, START_ANGLE + i * SWEEP_ANGLE + 1, SWEEP_ANGLE - 2, false, sectorPaint);
        }

        // Draw hover info
        if (hoveredIndex >= 0 && hoveredIndex < SECTOR_COUNT && currentTextAlpha > 0) {
            HeatMapData item = data.get(hoveredIndex);
            
            int startH = (startHour + hoveredIndex * 2) % 24;
            int endH = (startHour + hoveredIndex * 2 + 2) % 24;
            String timeRange = String.format("%02d:00 - %02d:00", startH, endH);
            String totalStr = String.format("总时长: %.1f分钟", item.totalDuration);
            
            float centerX = width / 2f;
            float centerY = height / 2f;
            
            textPaint.setAlpha(currentTextAlpha);
            
            // Draw Time Range
            textPaint.setTextSize(titleTextSize);
            textPaint.setFakeBoldText(true);
            float timeY = centerY - radius * 0.25f;
            canvas.drawText(timeRange, centerX, timeY, textPaint);
            
            // Draw Total Duration
            textPaint.setTextSize(subtitleTextSize);
            textPaint.setFakeBoldText(false);
            float totalY = centerY - radius * 0.10f;
            canvas.drawText(totalStr, centerX, totalY, textPaint);
            
            // Draw Breakdown
            textPaint.setTextSize(detailTextSize);
            float detailY = centerY + radius * 0.05f;
            float lineHeight = detailTextSize * 1.4f; // Optimized line height
            
            if (item.typeDetails.isEmpty()) {
                canvas.drawText("无详细数据", centerX, detailY, textPaint);
            } else {
                for (Map.Entry<String, Double> entry : item.typeDetails.entrySet()) {
                    if (entry.getValue() > 0) {
                        String detail = entry.getKey() + ": " + String.format("%.1f", entry.getValue()) + "m";
                        canvas.drawText(detail, centerX, detailY, textPaint);
                        detailY += lineHeight;
                    }
                }
            }
        }
    }
    
    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }

    private int interpolateColor(int colorStart, int colorEnd, float ratio) {
        Color.colorToHSV(colorStart, hsvStart);
        Color.colorToHSV(colorEnd, hsvEnd);

        for (int i = 0; i < 3; i++) {
            hsvResult[i] = hsvStart[i] + (hsvEnd[i] - hsvStart[i]) * ratio;
        }
        return Color.HSVToColor(hsvResult);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float dx = x - centerX;
                float dy = y - centerY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                
                // Calculate exact radii based on onDraw logic
                float width = getWidth();
                float height = getHeight();
                float maxRadius = Math.min(width, height) / 2f;
                float strokeWidth = maxRadius * 0.3f;
                float padding = strokeWidth / 2f + 10f;
                
                float ovalRadius = maxRadius - padding;
                float outerRadius = ovalRadius + strokeWidth / 2f;
                float innerRadius = ovalRadius - strokeWidth / 2f;
                
                int targetIndex = -1;
                
                // Precise hit detection
                if (dist >= innerRadius && dist <= outerRadius) {
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    double rotatedAngle = angle + 90;
                    if (rotatedAngle < 0) rotatedAngle += 360;
                    
                    int index = (int) (rotatedAngle / 30);
                    if (index >= 0 && index < SECTOR_COUNT) {
                        targetIndex = index;
                    }
                }
                
                // Logic Separation for Click (DOWN) vs Drag (MOVE)
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    final int finalTargetIndex = targetIndex;
                    // Click Interaction
                    if (targetIndex != -1) {
                        if (targetIndex == hoveredIndex) {
                            // Optimized: Click same segment -> Immediately clear content
                            // This fulfills the requirement to toggle off on re-click
                            startAlphaAnimation(0, () -> hoveredIndex = -1);
                        } else {
                            // Clicked a different segment
                            if (hoveredIndex != -1) {
                                // Switch from A to B -> Animated Switch
                                startSwitchAnimation(targetIndex);
                            } else {
                                // None to A -> Fade In
                                hoveredIndex = targetIndex;
                                startAlphaAnimation(255, () -> announceSelection(finalTargetIndex));
                            }
                        }
                    }
                    // Requirement: Click non-heatmap area (targetIndex == -1) -> Do nothing
                    // This keeps current display content timer unaffected
                    
                } else {
                    // Drag Interaction (ACTION_MOVE)
                    // Immediate feedback, no complex animations to avoid lag/flicker
                    if (targetIndex != hoveredIndex) {
                        cancelAnimations();
                        hoveredIndex = targetIndex;
                        // If we dragged into a valid sector, show it immediately
                        if (targetIndex != -1) {
                            currentTextAlpha = 255;
                            rescheduleAutoHide();
                            // Optional: Announce if needed, but might be too spammy during drag
                        } else {
                            // Dragged out -> Hide immediately
                            currentTextAlpha = 0;
                            removeCallbacks(autoHideTask);
                        }
                        invalidate();
                    } else if (targetIndex != -1) {
                        // Dragging within same sector, keep timer alive
                        rescheduleAutoHide();
                    }
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // We don't clear selection on UP to allow reading the info
                return true;
        }
        return super.onTouchEvent(event);
    }
}
