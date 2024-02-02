# Background on fork

This is technically not a true fork.
It's sole purpose is to provide early access to the fix [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/10389),
which resolves the issue [here](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10379).

Once this distribution is updated to a version of upstream OTeL that includes the fix, the forked code will be deleted.

At present, the forked code is in the `org.springframework.*` and `io.opentelemetry.*` packages.
`SpringWebMvcInstrumentationModule` references the forked classes,
and would need updating when the fork is removed.
In addition, the `SpringWebMvcConfigCustomizer` would need updating to not disable the upstream OTeL instrumentation.
