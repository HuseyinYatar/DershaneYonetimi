package com.dershane.attendanceservice;

import java.util.Arrays;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AttendanceServiceApplication {

  private static final String JOB_ARGUMENT_PREFIX = "--attendance.job=";

  /**
   * Started with {@code --attendance.job=<name>} (a Kubernetes CronJob of the same image), the
   * application runs that one job without a web server or outbox publisher and exits.
   */
  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(AttendanceServiceApplication.class);
    boolean jobRun = Arrays.stream(args).anyMatch(arg -> arg.startsWith(JOB_ARGUMENT_PREFIX));
    if (jobRun) {
      application.setWebApplicationType(WebApplicationType.NONE);
      application.setDefaultProperties(Map.of("attendance.outbox.enabled", "false"));
    }
    ConfigurableApplicationContext context = application.run(args);
    if (jobRun) {
      System.exit(SpringApplication.exit(context));
    }
  }
}
