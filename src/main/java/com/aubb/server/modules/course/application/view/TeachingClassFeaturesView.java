package com.aubb.server.modules.course.application.view;

public record TeachingClassFeaturesView(
        boolean announcementEnabled,
        boolean discussionEnabled,
        boolean resourceEnabled,
        boolean labEnabled,
        boolean assignmentEnabled) {}
