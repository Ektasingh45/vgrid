package com.vgrid.model;

import java.time.LocalDateTime;

/** One line in the dashboard's "Recent activity" list. */
public record ActivityItem(String title, String detail, LocalDateTime time) {
}
