FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
# Pull in fixed Alpine packages the base image has not picked up yet (Trivy HIGH/CRITICAL gate); the base runs as uid 101.
USER root
# Changes on every CI run so the cached layer below never keeps stale packages.
ARG CACHE_BUST=0
RUN echo "$CACHE_BUST" > /dev/null && apk upgrade --no-cache
USER 101
# The image renders these templates into /etc/nginx/conf.d at startup, substituting only defined env vars.
COPY nginx/default.conf.template nginx/security-headers.inc.template /etc/nginx/templates/
COPY --from=build /app/dist /usr/share/nginx/html
# CSP origins; keep in sync with config.js (Kubernetes overrides both).
ENV CSP_CONNECT_SRC="http://localhost:8000 http://localhost:8180" \
    CSP_FRAME_SRC="http://localhost:8180"
EXPOSE 8080
