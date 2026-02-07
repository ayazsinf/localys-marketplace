# Stage 1 — Build Angular app
FROM node:20-alpine AS build

WORKDIR /app

# Angular CLI kur
RUN npm install -g @angular/cli

COPY package*.json ./
RUN npm install --legacy-peer-deps

COPY . .

# Angular 17+ için production config gerekmez
RUN ng build --configuration production

# Stage 2 — Serve with Nginx
FROM nginx:1.25-alpine

COPY --from=build /app/dist/localys-frontend/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
