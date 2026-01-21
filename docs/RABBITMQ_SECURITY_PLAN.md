# RabbitMQ Security Implementation Plan

## Current State

All services use default `guest/guest` credentials for RabbitMQ access. This is a security risk as:
- Guest user has full administrative access
- No isolation between services
- Credentials are shared across all services
- Cannot audit which service performed which action

## Service RabbitMQ Interaction Matrix

| Service | Role | Exchanges | Queues | Routing Keys |
|---------|------|-----------|--------|--------------|
| **Profile** | Consumer | - | profile.registration | - |
| **Menu** | Both | foodies (publish) | menu.stock-validation (consume) | - |
| **Basket** | Consumer | - | basket.order-created | - |
| **Order** | Publisher | foodies.events | - | order.created |
| **Payment** | Both | foodies.events (publish) | payment.stock-confirmed (consume) | - |

## Proposed Security Model

### 1. User and Permission Design

Create dedicated users with least-privilege permissions:

#### Profile Service
```
User: profile_service
Permissions:
  - Read from queue: profile.registration
  - No write/configure permissions
```

#### Menu Service
```
User: menu_service
Permissions:
  - Read from queue: menu.stock-validation
  - Write to exchange: foodies
  - Configure queue: menu.stock-validation (for queue binding/creation)
```

#### Basket Service
```
User: basket_service
Permissions:
  - Read from queue: basket.order-created
  - No write to exchanges (purely consumer)
```

#### Order Service
```
User: order_service
Permissions:
  - Write to exchange: foodies.events
  - No read from queues (purely publisher)
```

#### Payment Service
```
User: payment_service
Permissions:
  - Read from queue: payment.stock-confirmed
  - Write to exchange: foodies.events
  - Configure queue: payment.stock-confirmed
```

### 2. RabbitMQ Permission Commands

```bash
# Profile service
rabbitmqctl add_user profile_service <secure_password>
rabbitmqctl set_permissions -p / profile_service "" "" "profile\.registration"

# Menu service
rabbitmqctl add_user menu_service <secure_password>
rabbitmqctl set_permissions -p / menu_service "menu\.stock-validation" "foodies" "menu\.stock-validation"

# Basket service
rabbitmqctl add_user basket_service <secure_password>
rabbitmqctl set_permissions -p / basket_service "" "" "basket\.order-created"

# Order service
rabbitmqctl add_user order_service <secure_password>
rabbitmqctl set_permissions -p / order_service "" "foodies\.events" ""

# Payment service
rabbitmqctl add_user payment_service <secure_password>
rabbitmqctl set_permissions -p / payment_service "payment\.stock-confirmed" "foodies\.events" "payment\.stock-confirmed"
```

Permission format: `configure write read`
- **configure**: Create/delete queues, exchanges
- **write**: Publish messages to exchanges
- **read**: Consume from queues

### 3. Implementation Approach

#### Option A: RabbitMQ Definitions File (Recommended)

Create a RabbitMQ definitions.json file loaded on startup:

```json
{
  "users": [
    {
      "name": "profile_service",
      "password_hash": "<hash>",
      "tags": []
    }
  ],
  "permissions": [
    {
      "user": "profile_service",
      "vhost": "/",
      "configure": "",
      "write": "",
      "read": "profile\\.registration"
    }
  ]
}
```

Mount as ConfigMap and set `RABBITMQ_DEFINITIONS_FILE` environment variable.

#### Option B: Init Container

Add Kubernetes init container that runs `rabbitmqctl` commands after RabbitMQ startup.

#### Option C: RabbitMQ Management API

Use HTTP API to configure users/permissions after deployment.

**Recommendation: Option A** - Declarative, version-controlled, repeatable

### 4. Kubernetes Secrets Structure

Update from single shared secret to per-service secrets:

```yaml
# Current (BAD)
secretGenerator:
  - name: rabbitmq-credentials
    literals:
      - RABBITMQ_USERNAME=guest
      - RABBITMQ_PASSWORD=guest

# Proposed (GOOD)
secretGenerator:
  - name: profile-rabbitmq-credentials
    literals:
      - RABBITMQ_USERNAME=profile_service
      - RABBITMQ_PASSWORD=<secure_random_password>

  - name: menu-rabbitmq-credentials
    literals:
      - RABBITMQ_USERNAME=menu_service
      - RABBITMQ_PASSWORD=<secure_random_password>

  # ... etc for each service
```

### 5. Migration Steps

1. **Create RabbitMQ definitions file**
   - Generate secure passwords (use `openssl rand -base64 32`)
   - Create definitions.json with all users and permissions
   - Add to k8s/base/rabbitmq/definitions-configmap.yaml

2. **Update RabbitMQ StatefulSet**
   - Mount definitions ConfigMap
   - Set `RABBITMQ_DEFINITIONS_FILE=/etc/rabbitmq/definitions.json`
   - Keep guest user during migration for backward compatibility

3. **Update Kubernetes secrets**
   - Create per-service secrets in kustomization.yaml
   - Store passwords securely (consider external secret management)

4. **Update service deployments**
   - Change secretKeyRef to point to service-specific secrets
   - Deploy one service at a time

5. **Verify connectivity**
   - Check service logs for successful RabbitMQ connection
   - Monitor for authentication errors

6. **Disable guest user**
   - Remove guest user from definitions.json
   - Redeploy RabbitMQ

### 6. Testing Approach

#### Unit Tests
No changes needed - services use configurable credentials

#### Integration Tests
- Update test containers to use per-service credentials
- Verify each service can only access its designated resources
- Test permission violations (e.g., Order trying to consume from Profile queue)

#### E2E Tests
- Full flow testing with new credential system
- Verify event publishing/consumption works across services

### 7. Security Considerations

- **Password Generation**: Use cryptographically secure random passwords (32+ chars)
- **Secret Management**: Consider Kubernetes external secrets (AWS Secrets Manager, HashiCorp Vault)
- **Rotation**: Document password rotation procedure
- **Monitoring**: Set up alerts for authentication failures
- **Audit Logging**: Enable RabbitMQ audit logging for compliance

### 8. Rollback Plan

If issues occur:
1. Revert to guest credentials in secrets
2. Keep guest user enabled in RabbitMQ definitions
3. Redeploy affected services
4. Investigate and fix before retry

### 9. Files to Modify

```
k8s/base/
├── kustomization.yaml              # Update secretGenerator
├── rabbitmq/
│   ├── definitions-configmap.yaml  # NEW: RabbitMQ definitions
│   └── statefulset.yaml           # Add ConfigMap mount
├── profile/deployment.yaml         # Update secret reference
├── menu/deployment.yaml            # Update secret reference
├── basket/deployment.yaml          # Update secret reference
├── order/deployment.yaml           # Update secret reference
└── payment/deployment.yaml         # Update secret reference
```

### 10. Estimated Implementation Time

- RabbitMQ definitions creation: 1 hour
- Kubernetes configuration: 1 hour
- Deployment and testing: 2 hours
- Documentation: 1 hour
- **Total: ~5 hours**

## Next Steps

1. Review and approve this plan
2. Generate secure passwords for all services
3. Create RabbitMQ definitions.json
4. Implement Kubernetes changes
5. Test in local/dev environment
6. Deploy to production

## References

- [RabbitMQ Access Control](https://www.rabbitmq.com/access-control.html)
- [RabbitMQ Definitions Export/Import](https://www.rabbitmq.com/definitions.html)
- [Kubernetes Secrets Best Practices](https://kubernetes.io/docs/concepts/security/secrets-good-practices/)
