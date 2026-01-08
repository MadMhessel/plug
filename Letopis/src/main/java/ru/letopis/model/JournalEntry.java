package ru.letopis.model;

public record JournalEntry(long ts, String world, String type, Scale scale, String detailsJson) {}
