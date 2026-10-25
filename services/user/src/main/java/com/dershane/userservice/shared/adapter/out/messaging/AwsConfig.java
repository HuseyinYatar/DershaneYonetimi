package com.dershane.userservice.shared.adapter.out.messaging;

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
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

/**
 * SNS client. The endpoint comes from {@code AWS_ENDPOINT_URL}: set to Floci locally and in CI,
 * left empty in production so the SDK talks to real AWS. Never hard-coded.
 *
 * <p>Credentials use the default AWS chain (IAM role in the cluster). Static credentials are only
 * meant for the local emulator, which accepts any value.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class AwsConfig {

  @Bean(destroyMethod = "close")
  public SnsClient snsClient(
      @Value("${user.aws.region}") String region,
      @Value("${user.aws.endpoint-url:}") String endpointUrl,
      @Value("${user.aws.static-access-key-id:}") String accessKeyId,
      @Value("${user.aws.static-secret-access-key:}") String secretAccessKey) {
    SnsClientBuilder builder =
        SnsClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials(accessKeyId, secretAccessKey))
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallAttemptTimeout(Duration.ofSeconds(3))
                    .apiCallTimeout(Duration.ofSeconds(10))
                    .build());
    if (endpointUrl == null || endpointUrl.isBlank()) {
      log.atInfo()
          .addKeyValue("region", region)
          .log("SNS client uses the default AWS endpoint (AWS_ENDPOINT_URL not set)");
    } else {
      log.atInfo()
          .addKeyValue("region", region)
          .addKeyValue("endpoint", endpointUrl)
          .log("SNS client uses a custom endpoint (local emulator)");
      builder.endpointOverride(URI.create(endpointUrl));
    }
    return builder.build();
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
