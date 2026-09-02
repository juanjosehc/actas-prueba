# Etapa 1: instalar flyonui. node_modules NO está en git; hay que generarlo.
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install

# Etapa 2: servir las páginas con nginx.
FROM nginx:alpine
COPY . /usr/share/nginx/html
COPY --from=build /app/node_modules/flyonui /usr/share/nginx/html/node_modules/flyonui