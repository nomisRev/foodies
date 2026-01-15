FROM quay.io/keycloak/keycloak:26.5.0 AS builder
WORKDIR /opt/keycloak

# Copy the pre-built provider JAR into the image
COPY keycloak-nats-publisher/build/libs/keycloak-nats-publisher-*-all.jar /opt/keycloak/providers/profile-webhook.jar

# Rebuild the Keycloak distribution with the custom provider installed.
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.5.0
COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
