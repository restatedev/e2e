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

FROM node:18

# Use a new stage so that the build-arg GH_PACKAGE_READ_ACCESS_TOKEN isn't leaked into the final image history
COPY --from=prod /usr/src/app/ /usr/src/app/

# Install Tini
RUN apt-get update && apt-get -y install tini

EXPOSE 8080
ENTRYPOINT ["tini", "--"]
CMD ["node", "/usr/src/app/dist/app.js"]
