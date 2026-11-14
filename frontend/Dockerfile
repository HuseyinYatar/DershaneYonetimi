FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine
# The image renders these templates into /etc/nginx/conf.d at startup, substituting only defined env vars.
COPY nginx/default.conf.template nginx/security-headers.inc.template /etc/nginx/templates/
COPY --from=build /app/dist /usr/share/nginx/html
# CSP origins; keep in sync with config.js (Kubernetes overrides both).
ENV CSP_CONNECT_SRC="http://localhost:8000 http://localhost:8180" \
    CSP_FRAME_SRC="http://localhost:8180"
EXPOSE 8080
