# :net:sync:statesync

This module implements the StateSync Lite v1 client independently from the gPodder-compatible sync provider.
It owns the protocol codec, secure bearer-token storage, HTTP transport, merge coordination, and WorkManager jobs.
Business-state and Outbox writes remain in `:storage:database` so they can share one SQLite transaction.
