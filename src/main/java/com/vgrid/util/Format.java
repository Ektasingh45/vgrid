package com.vgrid.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Small helpers that turn values into display text. */
public final class Format {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE, d MMM");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private Format() {
    }

    public static String distance(double km) {
        return String.format("%.1f km", km);
    }

    public static String power(double kw) {
        if (kw == Math.floor(kw)) {
            return (int) kw + " kW";
        }
        return kw + " kW";
    }

    public static String minutes(int minutes) {
        return minutes <= 0 ? "No wait" : "~" + minutes + " min";
    }

    public static String price(double perKwh) {
        return "₹" + String.format("%.0f", perKwh) + "/kWh";
    }

    public static String rupees(double amount) {
        return "₹" + String.format("%,.0f", amount);
    }

    /** "Today", "Tomorrow", "Yesterday" or a short date. */
    public static String date(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "Today";
        if (date.equals(today.plusDays(1))) return "Tomorrow";
        if (date.equals(today.minusDays(1))) return "Yesterday";
        return date.format(SHORT_DATE);
    }

    public static String longDate(LocalDate date) {
        return date.format(LONG_DATE);
    }

    public static String dateTime(LocalDateTime time) {
        return time.format(DATE_TIME);
    }

    /** "Just now", "5 min ago", "3 hr ago", "Yesterday", ... */
    public static String relative(LocalDateTime time) {
        long minutes = Duration.between(time, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hr ago";
        long days = hours / 24;
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        return time.toLocalDate().format(SHORT_DATE);
    }

    public static String plural(long count, String word) {
        return count + " " + word + (count == 1 ? "" : "s");
    }
}
