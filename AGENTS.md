# Issue tracking

This project uses 'br' command line tool for  issue tracking.

When implementing always consult 'br', and try to break down your tasks.
Keep your task as short as possible, and prefer creating new tasks in 'br' for follow-up work.

When reviewing create issues using 'br' for any issues you find. 

# Build with Gradle

- Build any module `./gradlew :<module>:build`
- Run all checks for a module `./gradlew :<module>:jvmTest`
    - Test selection accepts the pipe | character to separate test elements:
      `./gradlew cleanJvmTest jvmTest --tests "com.example.TestSuite|inner suite|*" --no-build-cache`
- Run `./gradlew publishImageToLocalRegistry` to publish images for local deployment
- Always use Gradle's Version Catalog, never hardcode dependencies in build.gradle.kts
- 
# Code Style

- No comments unless code is complex and requires context for future developers.
- Testing: Never use mocks. Use TestContainers and prefer testing actual integrations.
- Logging: Use structured logging (tracing). Never log secrets directly. Rely on nocode/javaagent where applicable
- Avoid exceptions for control flow, and use proper types and domain modeling

# Kubernetes Debugging

The development machine has `127.0.0.1       foodies.local` configured in `/etc/hosts`.

Everything runs in the foodies namespace:

- List pods: `kubectl get pods -n foodies`
- Describe pod: `kubectl describe pod <pod-name> -n foodies`
- Pod logs: `kubectl logs <pod-name> -n foodies`
- Delete stuck pod: `kubectl delete pod <pod-name> -n foodies`

## Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
