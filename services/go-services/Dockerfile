FROM --platform=$BUILDPLATFORM golang:1.22 AS build


# Download Go modules
COPY go.mod go.sum ./
RUN go mod download

COPY . ./

# Build
ARG TARGETOS
ARG TARGETARCH
RUN CGO_ENABLED=0 GOOS=$TARGETOS GOARCH=$TARGETARCH go build -o /out/go-services

FROM alpine
COPY --from=build /out/go-services /bin
# Run
CMD ["/bin/go-services"]
