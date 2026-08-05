package com.erp.manufacturing.module.shift.domain;

import java.time.DayOfWeek;

public enum Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    public static Weekday from(DayOfWeek dayOfWeek) {
        return Weekday.valueOf(dayOfWeek.name());
    }
}
