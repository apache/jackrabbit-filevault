# Multi stage docker build:
# Build filevault
FROM maven:3.5-jdk-8

COPY . /build
WORKDIR /build
RUN mvn clean package && chmod -R +x /build/vault-cli/target/appassembler/bin/

# Second stage of the build
# Copy build artifacts to
FROM openjdk:8-jre-slim

LABEL description="Filevault executable image"

COPY --from=0 /build/vault-cli/target/appassembler /filevault
WORKDIR /filevault

CMD ["/filevault/bin/vlt"]
ENTRYPOINT ["/filevault/bin/vlt"]