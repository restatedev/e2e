FROM node:18 AS build

WORKDIR /usr/src/app
COPY . .

RUN npm ci
RUN npm run build

FROM node:18 as prod
WORKDIR /usr/src/app

# Install app dependencies
COPY package*.json *.tgz ./
RUN npm ci --production

COPY --from=build /usr/src/app/dist /usr/src/app/dist

# Install Tini
RUN apt-get update && apt-get -y install tini

EXPOSE 8080
ENTRYPOINT ["tini", "--"]
CMD ["node", "/usr/src/app/dist/app.js"]
