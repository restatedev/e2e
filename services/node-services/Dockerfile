FROM node:18 AS build
ARG NPM_TOKEN

WORKDIR /usr/src/app
COPY . .

RUN echo "//npm.pkg.github.com/:_authToken=$NPM_TOKEN\n" >> .npmrc && \
    echo "@restatedev:registry=https://npm.pkg.github.com/" >> .npmrc && \
 npm ci && \
 npm run build && \
 rm -f .npmrc

FROM node:18 as prod
ARG NPM_TOKEN
WORKDIR /usr/src/app

# Install app dependencies
COPY package*.json ./
RUN echo "//npm.pkg.github.com/:_authToken=$NPM_TOKEN\n" >> .npmrc && \
    echo "@restatedev:registry=https://npm.pkg.github.com/" >> .npmrc && \
 npm ci --production && \
 rm -f .npmrc

COPY --from=build /usr/src/app/dist /usr/src/app/dist

# Install Tini
RUN apt-get update && apt-get -y install tini

EXPOSE 8080
ENTRYPOINT ["tini", "--"]
CMD ["node", "/usr/src/app/dist/app.js"]