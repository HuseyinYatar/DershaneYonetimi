FROM node:26-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
# Pull in fixed Alpine packages the base image has not picked up yet (Trivy HIGH/CRITICAL gate); the base runs as uid 101.
USER root
RUN apk upgrade --no-cache
USER 101
# The image renders these templates into /etc/nginx/conf.d at startup, substituting only defined env vars.
COPY nginx/default.conf.template nginx/security-headers.inc.template /etc/nginx/templates/
COPY --from=build /app/dist /usr/share/nginx/html
# CSP origins; keep in sync with config.js (Kubernetes overrides both).
ENV CSP_CONNECT_SRC="http://localhost:8000 http://localhost:8180" \
    CSP_FRAME_SRC="http://localhost:8180"
EXPOSE 8080
