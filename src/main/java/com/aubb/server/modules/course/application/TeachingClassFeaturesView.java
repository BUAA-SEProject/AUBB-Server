package com.aubb.server.modules.course.application;

public record TeachingClassFeaturesView(
        boolean announcementEnabled,
        boolean discussionEnabled,
        boolean resourceEnabled,
        boolean labEnabled,
        boolean assignmentEnabled) {}
