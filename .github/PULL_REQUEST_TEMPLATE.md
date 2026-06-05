## Summary
<!-- What does this PR change and why? -->

## Type of change
- [ ] Bug fix
- [ ] New feature
- [ ] Refactor
- [ ] Documentation
- [ ] Test / CI

## Checklist
- [ ] Follows the conventions in [CONTRIBUTING.md](../CONTRIBUTING.md) / [CLAUDE.md](../CLAUDE.md)
- [ ] All targets compile:
      `./gradlew :shared:compileKotlinJvm :shared:compileKotlinIosArm64 :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileAndroidMain`
- [ ] Tests pass: `./gradlew :shared:jvmTest :shared:testAndroidHostTest`
- [ ] Added/updated tests for new behavior
- [ ] No hardcoded colors/strings/sizes; state changes go through `ServiceIntent`
- [ ] Updated docs (README / CLAUDE.md / CHANGELOG) where relevant

## Test plan
<!-- How did you verify this works? Which platforms did you test? -->

## Related issues
<!-- Closes #123 -->
