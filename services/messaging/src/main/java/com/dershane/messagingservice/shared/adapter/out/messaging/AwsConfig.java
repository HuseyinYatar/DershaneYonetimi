package com.dershane.messagingservice.shared.adapter.out.messaging;

import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * SNS and SQS clients. The endpoint comes from {@code AWS_ENDPOINT_URL}: set to Floci locally and
 * in CI, left empty in production so the SDK talks to real AWS. Never hard-coded.
 *
 * <p>Credentials use the default AWS chain (IAM role in the cluster). Static credentials are only
 * meant for the local emulator, which accepts any value.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class AwsConfig {

  @Bean(destroyMethod = "close")
  public SnsClient snsClient(
      @Value("${messaging.aws.region}") String region,
      @Value("${messaging.aws.endpoint-url:}") String endpointUrl,
      @Value("${messaging.aws.static-access-key-id:}") String accessKeyId,
      @Value("${messaging.aws.static-secret-access-key:}") String secretAccessKey) {
    return configure(
            SnsClient.builder(),
            "SNS",
            region,
            endpointUrl,
            accessKeyId,
            secretAccessKey,
            Duration.ofSeconds(3),
            Duration.ofSeconds(10))
        .build();
  }

  /** Long polling holds a request open for the whole wait time, hence the longer timeouts. */
  @Bean(destroyMethod = "close")
  public SqsClient sqsClient(
      @Value("${messaging.aws.region}") String region,
      @Value("${messaging.aws.endpoint-url:}") String endpointUrl,
      @Value("${messaging.aws.static-access-key-id:}") String accessKeyId,
      @Value("${messaging.aws.static-secret-access-key:}") String secretAccessKey) {
    return configure(
            SqsClient.builder(),
            "SQS",
            region,
            endpointUrl,
            accessKeyId,
            secretAccessKey,
            Duration.ofSeconds(30),
            Duration.ofSeconds(60))
        .build();
  }

  private static <B extends AwsClientBuilder<B, ?>> B configure(
      B builder,
      String service,
      String region,
      String endpointUrl,
      String accessKeyId,
      String secretAccessKey,
      Duration attemptTimeout,
      Duration callTimeout) {
    builder
        .region(Region.of(region))
        .credentialsProvider(credentials(accessKeyId, secretAccessKey))
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(attemptTimeout)
                .apiCallTimeout(callTimeout)
                .build());
    if (endpointUrl == null || endpointUrl.isBlank()) {
      log.atInfo()
          .addKeyValue("service", service)
          .addKeyValue("region", region)
          .log("AWS client uses the default endpoint (AWS_ENDPOINT_URL not set)");
    } else {
      log.atInfo()
          .addKeyValue("service", service)
          .addKeyValue("region", region)
          .addKeyValue("endpoint", endpointUrl)
          .log("AWS client uses a custom endpoint (local emulator)");
      builder.endpointOverride(URI.create(endpointUrl));
    }
    return builder;
  }

  private static AwsCredentialsProvider credentials(String accessKeyId, String secretAccessKey) {
    if (accessKeyId.isBlank() || secretAccessKey.isBlank()) {
      log.info("AWS credentials: default provider chain");
      return DefaultCredentialsProvider.builder().build();
    }
    log.info("AWS credentials: static (local emulator)");
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(accessKeyId, secretAccessKey));
  }
}
