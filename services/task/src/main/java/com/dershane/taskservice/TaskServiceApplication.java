package com.dershane.taskservice;

import java.util.Arrays;
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
    boolean jobRun = isJobRun(args);
    if (jobRun) {
      application.setWebApplicationType(WebApplicationType.NONE);
    }
    ConfigurableApplicationContext context = application.run(runArguments(args));
    if (jobRun) {
      System.exit(SpringApplication.exit(context));
    }
  }

  static boolean isJobRun(String[] args) {
    return Arrays.stream(args).anyMatch(arg -> arg.startsWith(JOB_ARGUMENT_PREFIX));
  }

  /**
   * In job mode the outbox publisher and the event consumer are switched off with command-line
   * arguments. SpringApplication.setDefaultProperties ranks below application.yml, whose {@code
   * ${INBOX_ENABLED:true}} would win and make a CronJob pod poll the queue; arguments rank above.
   */
  static String[] runArguments(String[] args) {
    if (!isJobRun(args)) {
      return args;
    }
    String[] extended = Arrays.copyOf(args, args.length + 2);
    extended[args.length] = "--task.outbox.enabled=false";
    extended[args.length + 1] = "--task.inbox.enabled=false";
    return extended;
  }
}
