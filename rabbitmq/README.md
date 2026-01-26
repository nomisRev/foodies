# RabbitMQ with x-delayed-message Plugin

Custom RabbitMQ image with the `x-delayed-message` exchange type plugin installed.

## Plugin Version

- RabbitMQ: 4.2-management
- Plugin: rabbitmq-delayed-message-exchange 4.2.0

## Building

```bash
./gradlew :rabbitmq:publishImageToLocalRegistry
```

## Usage

The order service requires the `x-delayed-message` exchange type for handling delayed message delivery.

### Kubernetes

The image is automatically used in `k8s/base/rabbitmq/cluster.yaml`

### Docker Compose

The image is configured in `docker-compose.yml`

## Limitations

When using RabbitMQ 4.2.x with Khepri enabled, the plugin must be disabled and re-enabled or the node must be restarted after Khepri activation.
