package com.pointofdata.podos.message;

/** Represents a Pod-OS AIP date-time decomposed into individual fields. */
public class DateTimeObject {
    public int year;
    public int month;
    public int day;
    public int hour;
    public int minute;
    public int second;
    public int microsecond;

    public DateTimeObject() {}

    public DateTimeObject(int year, int month, int day, int hour, int minute, int second, int microsecond) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.microsecond = microsecond;
    }

    @Override
    public String toString() {
        return String.format("DateTimeObject{%04d-%02d-%02d %02d:%02d:%02d.%06d}",
                year, month, day, hour, minute, second, microsecond);
    }
}
