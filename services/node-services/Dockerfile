FROM node:18 AS build
ARG NPM_TOKEN

WORKDIR /usr/src/app
COPY . .

RUN echo "//npm.pkg.github.com/:_authToken=$NPM_TOKEN\n" >> .npmrc && \
    echo "@restatedev:registry=https://npm.pkg.github.com/" >> .npmrc && \
 npm ci && \
 npm run build && \
 rm -f .npmrc

FROM build as prod

COPY --from=build /usr/src/app/dist /dist

EXPOSE 8080
CMD npm run app