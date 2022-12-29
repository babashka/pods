# Changelog

## v0.2.0

- #61: add transit as explicit JVM dependency
- #60: transform pod reader error into exception of caller
- Switch "out" and "err" messages to print and flush instead of `println` (@justone)
- Set TCP_NODELAY on transport socket (@retrogradeorbit)
-  Allow env vars OS_NAME & OS_ARCH to override os props (@cap10morgan)
- #49: don't log socket closed exception

## v0.1.0

Initial version
