# Changelog

## Unreleased

- [#63](https://github.com/babashka/pods/issues/63): create directory before un-tarring
- [#59](https://github.com/babashka/pods/issues/59): delete port file on exit
- [#65](https://github.com/babashka/pods/issues/65): fix warnings when defining var with core name in JVM
- [#66](https://github.com/babashka/pods/issues/66): Allow metadata on fn arguments for transit+json

## v0.2.0

- [#61](https://github.com/babashka/pods/issues/61): add transit as explicit JVM dependency
- [#60](https://github.com/babashka/pods/issues/60): transform pod reader error into exception of caller
- Switch "out" and "err" messages to print and flush instead of `println` ([@justone](https://github.com/justone))
- Set TCP_NODELAY on transport socket ([@retrogradeorbit](https://github.com/retrogradeorbit))
-  Allow env vars OS_NAME & OS_ARCH to override os props ([@cap10morgan](https://github.com/cap10morgan))
- [#49](https://github.com/babashka/pods/issues/49): don't log socket closed exception

## v0.1.0

Initial version
