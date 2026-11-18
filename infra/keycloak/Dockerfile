# Keycloak for the cluster: the official image plus our login theme, pre-built for PostgreSQL.
# Locally docker compose mounts the theme folder into the official image; a pod cannot reach that folder,
# so the theme is copied into the image here. `kc.sh build` bakes the build-time options in, so the pod
# starts with `start --optimized` instead of redoing them on every start (Keycloak's production setup).
#
# Build from the repository root (the theme lives in local/keycloak/themes):
#   docker build --platform linux/amd64 -f infra/keycloak/Dockerfile -t <ecr_registry>/keycloak:<commit sha> .
# Same Keycloak version as local/docker-compose.yml.

FROM quay.io/keycloak/keycloak:26.8.1 AS builder
# One Keycloak pod: a local cache instead of the cluster-wide one (no peers to find).
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_CACHE=local
COPY local/keycloak/themes/dershane /opt/keycloak/themes/dershane
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.1
ENV KC_CACHE=local
COPY --from=builder /opt/keycloak/ /opt/keycloak/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
