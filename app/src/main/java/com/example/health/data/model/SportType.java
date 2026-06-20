package com.example.health.data.model;

/**
 * 运动类型枚举，定义系统支持的四类运动及其代谢当量（MET）。
 * MET 值用于卡路里消耗估算：卡路里 = MET × 体重(kg) × 时间(h)。
 */
public enum SportType {
    WALKING("步行", 3.5),
    RUNNING("跑步", 8.0),
    CYCLING("骑行", 6.0),
    FITNESS("健身", 5.0);

    private final String displayName;
    private final double metValue;

    SportType(String displayName, double metValue) {
        this.displayName = displayName;
        this.metValue = metValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMetValue() {
        return metValue;
    }
}
