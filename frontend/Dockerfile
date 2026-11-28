# Frontend image: build the React app with Node 22, then serve dist/ with nginx.
# nginx also passes /api to the backend container, so the browser sees a single origin.

# ---------- build ----------
FROM node:22-bookworm-slim AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---------- serve ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
