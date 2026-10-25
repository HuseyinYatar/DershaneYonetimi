package com.dershane.userservice.shared.adapter.in.openapi;

import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.adapter.in.web.idempotency.IdempotencyAspect;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation. Swagger UI logs in through Keycloak (authorization code + PKCE with the public
 * {@code dershane-swagger} client). Only enabled in local/dev profiles.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  private static final String SECURITY_SCHEME = "keycloak";

  static {
    // The caller is resolved from the token, so it is not a request parameter.
    SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentActor.class);
  }

  @Bean
  public OpenAPI openApi(OpenApiProperties properties) {
    OAuthFlow authorizationCode =
        new OAuthFlow()
            .authorizationUrl(properties.authorizationUrl())
            .tokenUrl(properties.tokenUrl())
            .scopes(new Scopes().addString("openid", "OpenID Connect"));
    return new OpenAPI()
        .info(
            new Info()
                .title("user service API")
                .version("v1")
                .description(
                    "Branches, classrooms, subjects and student/teacher/guardian profiles."
                        + " Errors are RFC 9457 problem details with an errorCode."))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
  }

  /** Documents the optional Idempotency-Key header on endpoints that support it. */
  @Bean
  public OperationCustomizer idempotencyKeyHeader() {
    return (operation, handlerMethod) -> {
      if (handlerMethod.hasMethodAnnotation(Idempotent.class)) {
        operation.addParametersItem(
            new HeaderParameter()
                .name(IdempotencyAspect.HEADER)
                .required(false)
                .description(
                    "Optional. Retrying with the same key and body returns the first response"
                        + " instead of creating a duplicate.")
                .schema(new StringSchema().maxLength(100)));
      }
      return operation;
    };
  }
}
