# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
# Sirius Web's front-end packages come from GitHub Packages: the token is a build secret, never in an image layer.
RUN --mount=type=secret,id=github_token,required=true \
    NPM_TOKEN="$(cat /run/secrets/github_token)" npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 3000
