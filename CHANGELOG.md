# Changelog

## Unreleased

- [#63](https://github.com/babashka/pods/issues/63): create directory before un-tarring

## v0.2.0

- [#61](https://github.com/babashka/pods/issues/61): add transit as explicit JVM dependency
- [#60](https://github.com/babashka/pods/issues/60): transform pod reader error into exception of caller
- Switch "out" and "err" messages to print and flush instead of `println` ([@justone](https://github.com/justone))
- Set TCP_NODELAY on transport socket ([@retrogradeorbit](https://github.com/retrogradeorbit))
-  Allow env vars OS_NAME & OS_ARCH to override os props ([@cap10morgan](https://github.com/cap10morgan))
- [#49](https://github.com/babashka/pods/issues/49): don't log socket closed exception

## v0.1.0

Initial version
