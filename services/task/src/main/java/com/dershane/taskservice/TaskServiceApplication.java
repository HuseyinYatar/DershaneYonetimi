package com.dershane.taskservice;

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
public class TaskServiceApplication {

  private static final String JOB_ARGUMENT_PREFIX = "--task.job=";

  /**
   * Started with {@code --task.job=<name>} (a Kubernetes CronJob of the same image), the
   * application runs that one job without a web server, outbox publisher or event consumer and
   * exits.
   */
  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(TaskServiceApplication.class);
    boolean jobRun = Arrays.stream(args).anyMatch(arg -> arg.startsWith(JOB_ARGUMENT_PREFIX));
    if (jobRun) {
      application.setWebApplicationType(WebApplicationType.NONE);
      application.setDefaultProperties(
          Map.of("task.outbox.enabled", "false", "task.inbox.enabled", "false"));
    }
    ConfigurableApplicationContext context = application.run(args);
    if (jobRun) {
      System.exit(SpringApplication.exit(context));
    }
  }
}
