package com.dershane.attendanceservice.schedule.adapter.out.userdirectory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the user service lives ({@code attendance.user-service.url}, env {@code USER_SERVICE_URL}).
 */
@ConfigurationProperties("attendance.user-service")
public record UserServiceProperties(@DefaultValue("http://localhost:8080") String url) {}
