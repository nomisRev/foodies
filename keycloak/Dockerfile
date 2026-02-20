FROM quay.io/keycloak/keycloak:26.5.4 AS builder
WORKDIR /opt/keycloak

ARG JAR_VERSION
COPY keycloak-rabbitmq-publisher/build/libs/keycloak-rabbitmq-publisher-${JAR_VERSION}-all.jar /opt/keycloak/providers/profile-webhook.jar

# Rebuild the Keycloak distribution with the custom provider installed.
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.5.4
COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
