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

EXPOSE 8080
CMD npm run app