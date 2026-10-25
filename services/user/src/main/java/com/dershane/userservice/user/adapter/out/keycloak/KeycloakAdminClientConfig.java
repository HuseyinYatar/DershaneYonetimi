package com.dershane.userservice.user.adapter.out.keycloak;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * RestClient for the Keycloak Admin REST API, authenticated with this service's own
 * client-credentials token. Both the token request and the API calls have timeouts, so a hanging
 * Keycloak turns into a fast 503 instead of blocked request threads.
 */
@Configuration(proxyBeanMethods = false)
public class KeycloakAdminClientConfig {

  /** The token belongs to the service, not to the end user making the request. */
  private static final Authentication SERVICE_PRINCIPAL =
      new AnonymousAuthenticationToken(
          "user-service", "user-service", AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

  @Bean
  public OAuth2AuthorizedClientManager keycloakAdminAuthorizedClientManager(
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientService clientService,
      KeycloakProperties properties) {
    RestClientClientCredentialsTokenResponseClient tokenClient =
        new RestClientClientCredentialsTokenResponseClient();
    tokenClient.setRestClient(
        RestClient.builder()
            .requestFactory(requestFactory(properties))
            .configureMessageConverters(
                converters -> {
                  converters.addCustomConverter(new FormHttpMessageConverter());
                  converters.addCustomConverter(
                      new OAuth2AccessTokenResponseHttpMessageConverter());
                })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clientService);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(builder -> builder.accessTokenResponseClient(tokenClient))
            .build());
    return manager;
  }

  @Bean
  public RestClient keycloakAdminRestClient(
      RestClient.Builder builder,
      KeycloakProperties properties,
      OAuth2AuthorizedClientManager keycloakAdminAuthorizedClientManager) {
    OAuth2ClientHttpRequestInterceptor interceptor =
        new OAuth2ClientHttpRequestInterceptor(keycloakAdminAuthorizedClientManager);
    interceptor.setClientRegistrationIdResolver(request -> properties.clientRegistrationId());
    interceptor.setPrincipalResolver(request -> SERVICE_PRINCIPAL);
    // Boot's builder adds the observation (trace span + http.client.requests metric).
    return builder
        .clone()
        .baseUrl(properties.serverUrl() + "/admin/realms/" + properties.realm())
        .requestFactory(requestFactory(properties))
        .requestInterceptor(interceptor)
        .build();
  }

  private static SimpleClientHttpRequestFactory requestFactory(KeycloakProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    return factory;
  }
}
