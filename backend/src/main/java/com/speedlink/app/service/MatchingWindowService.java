package com.speedlink.app.service;

import com.speedlink.app.dto.MatchingWindowRequest;
import com.speedlink.app.dto.MatchingWindowResponse;
import com.speedlink.app.entity.AppSetting;
import com.speedlink.app.repository.AppSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MatchingWindowService {
    private static final String SETTINGS_ID = "default";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private final AppSettingRepository repository;
    private final Clock clock;
    private final boolean defaultEnabled;
    private final LocalTime defaultStart;
    private final LocalTime defaultEnd;
    private final String defaultZoneId;

    public MatchingWindowService(
            AppSettingRepository repository,
            @Value("${speedlink.matching-window.enabled:true}") boolean defaultEnabled,
            @Value("${speedlink.matching-window.start:21:00}") String defaultStart,
            @Value("${speedlink.matching-window.end:22:00}") String defaultEnd,
            @Value("${speedlink.matching-window.zone:Asia/Kolkata}") String defaultZoneId
    ) {
        this.repository = repository;
        this.clock = Clock.systemUTC();
        this.defaultEnabled = defaultEnabled;
        this.defaultStart = parseTime(defaultStart);
        this.defaultEnd = parseTime(defaultEnd);
        this.defaultZoneId = normalizeZone(defaultZoneId).getId();
    }

    public MatchingWindowResponse current() {
        return toResponse(loadSettings());
    }

    public boolean isOpenNow() {
        AppSetting setting = loadSettings();
        if (!setting.isMatchingWindowEnabled()) {
            return true;
        }
        ZoneId zoneId = normalizeZone(setting.getMatchingWindowZoneId());
        LocalTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId).toLocalTime();
        return isWithinWindow(now, setting.getMatchingWindowStart(), setting.getMatchingWindowEnd());
    }

    public MatchingWindowResponse update(MatchingWindowRequest request) {
        ZoneId zoneId = normalizeZone(request.zoneId());
        LocalTime start = parseTime(request.startTime());
        LocalTime end = parseTime(request.endTime());
        if (start.equals(end)) {
            throw new IllegalArgumentException("Start and end time must be different.");
        }

        AppSetting setting = loadSettings();
        setting.updateMatchingWindow(request.enabled(), start, end, zoneId.getId());
        return toResponse(repository.save(setting));
    }

    private AppSetting loadSettings() {
        return repository.findById(SETTINGS_ID)
                .orElseGet(() -> repository.save(new AppSetting(
                        SETTINGS_ID,
                        defaultEnabled,
                        defaultStart,
                        defaultEnd,
                        defaultZoneId
                )));
    }

    private MatchingWindowResponse toResponse(AppSetting setting) {
        ZoneId zoneId = normalizeZone(setting.getMatchingWindowZoneId());
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
        LocalTime start = setting.getMatchingWindowStart();
        LocalTime end = setting.getMatchingWindowEnd();
        boolean openNow = !setting.isMatchingWindowEnabled() || isWithinWindow(now.toLocalTime(), start, end);
        ZonedDateTime nextOpen = nextOpen(now, start, end, openNow);
        ZonedDateTime nextClose = nextClose(now, start, end, openNow);
        String displayLabel = formatDisplayLabel(start, end, zoneId);

        return new MatchingWindowResponse(
                setting.isMatchingWindowEnabled(),
                TIME_FORMATTER.format(start),
                TIME_FORMATTER.format(end),
                zoneId.getId(),
                openNow,
                Instant.now(clock).toEpochMilli(),
                nextOpen.toInstant().toEpochMilli(),
                nextClose.toInstant().toEpochMilli(),
                displayLabel,
                setting.getUpdatedAt().toString()
        );
    }

    private ZonedDateTime nextOpen(ZonedDateTime now, LocalTime start, LocalTime end, boolean openNow) {
        if (openNow) {
            return now;
        }

        LocalDate date = now.toLocalDate();
        ZonedDateTime todayStart = date.atTime(start).atZone(now.getZone());
        if (start.isBefore(end) && now.isBefore(todayStart)) {
            return todayStart;
        }
        if (start.isAfter(end)) {
            ZonedDateTime yesterdayStart = date.minusDays(1).atTime(start).atZone(now.getZone());
            ZonedDateTime todayEnd = date.atTime(end).atZone(now.getZone());
            if (now.isBefore(todayEnd) && now.isAfter(yesterdayStart)) {
                return yesterdayStart;
            }
            if (now.isBefore(todayStart)) {
                return todayStart;
            }
        }
        return date.plusDays(1).atTime(start).atZone(now.getZone());
    }

    private ZonedDateTime nextClose(ZonedDateTime now, LocalTime start, LocalTime end, boolean openNow) {
        LocalDate date = now.toLocalDate();
        if (!openNow) {
            ZonedDateTime open = nextOpen(now, start, end, false);
            return closeForOpen(open, start, end);
        }

        if (!start.isBefore(end) && now.toLocalTime().isBefore(end)) {
            return date.atTime(end).atZone(now.getZone());
        }
        ZonedDateTime close = date.atTime(end).atZone(now.getZone());
        if (!start.isBefore(end) || !close.isAfter(now)) {
            close = close.plusDays(1);
        }
        return close;
    }

    private ZonedDateTime closeForOpen(ZonedDateTime open, LocalTime start, LocalTime end) {
        ZonedDateTime close = open.toLocalDate().atTime(end).atZone(open.getZone());
        if (!start.isBefore(end)) {
            close = close.plusDays(1);
        }
        return close;
    }

    private boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private String formatDisplayLabel(LocalTime start, LocalTime end, ZoneId zoneId) {
        return DISPLAY_TIME_FORMATTER.format(start) + " to " + DISPLAY_TIME_FORMATTER.format(end)
                + " (" + zoneId.getId() + ")";
    }

    private LocalTime parseTime(String value) {
        return LocalTime.parse(value, TIME_FORMATTER);
    }

    private ZoneId normalizeZone(String zoneId) {
        try {
            return ZoneId.of(zoneId == null || zoneId.isBlank() ? "Asia/Kolkata" : zoneId.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Timezone is invalid.");
        }
    }
}
