# keycloak-rabbitmq-publisher

A Keycloak event listener that publishes events to a RabbitMQ queue.

## Rationale

There are several community efforts to build generic event listeners for Keycloak.

- [keycloak-webhook](https://github.com/vymalo/keycloak-webhook)
- [keycloak-events](https://github.com/p2-inc/keycloak-events)

I decided to handroll a solution since it only requires ~100 lines of code but gives us full control.
The community solutions still require properly integrating them into Keycloak, etc.
