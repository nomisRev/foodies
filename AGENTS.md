# Guidelines

## Issue tracking

This project uses 'br' (rust beads) for task management.

When implementing a feature always consult 'br' and try to break down your tasks first.
Keep your task as short as possible, and prefer creating new tasks in 'br' for follow-up work.

When reviewing create issues using 'br' for any issues you find. 

# Build with Gradle

- Build any module `./gradlew :<module>:build`
- Run all checks for a module `./gradlew :<module>:jvmTest`
    - Test selection accepts the pipe | character to separate test elements:
      `./gradlew cleanJvmTest jvmTest --tests "com.example.TestSuite|inner suite|*" --no-build-cache`
- Run `./gradlew publishImageToLocalRegistry` to publish images for local deployment

# Code Style

- No comments unless code is complex and requires context for future developers.
- Testing: Never use mocks. Use TestContainers and prefer testing actual integrations.
- Logging: Use structured logging (tracing). Never log secrets directly. Rely on nocode/javaagent where applicable
- Avoid exceptions for control flow, and use proper types and domain modeling

# Testing
## Kubernetes Debugging

The development machine has `127.0.0.1       foodies.local` configured in `/etc/hosts`.

Everything runs in the foodies namespace:

- List pods: `kubectl get pods -n foodies`
- Describe pod: `kubectl describe pod <pod-name> -n foodies`
- Pod logs: `kubectl logs <pod-name> -n foodies`
- Delete stuck pod: `kubectl delete pod <pod-name> -n foodies`
