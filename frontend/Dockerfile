# --- build stage ---
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite inlines VITE_* at build time, so the API origin must be known here. Empty
# (the default) means same-origin, which is what the nginx proxy below serves;
# a split deploy passes the real API origin to reproduce cross-origin behaviour.
ARG VITE_API_URL=""
ARG VITE_RECAPTCHA_SITE_KEY=""
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_RECAPTCHA_SITE_KEY=$VITE_RECAPTCHA_SITE_KEY
RUN npm run build

# --- serve stage ---
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
