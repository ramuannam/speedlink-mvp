# SpeedLink Scaling Notes

This backend is now safer for larger realtime traffic, but 100,000 concurrent users requires horizontal infrastructure, not a single process.

## Required Production Shape

- Run multiple backend replicas behind a load balancer that supports WebSocket upgrades.
- Use Redis as the shared queue, pending-match, cooldown, and temporary profile store.
- Keep WebSocket session affinity enabled where possible, or add Redis pub/sub for cross-replica signaling and chat.
- Use managed PostgreSQL with connection pooling. Do not let every backend replica open large database pools.
- Use a dedicated TURN service for users behind restrictive NATs.
- For group calls or guaranteed media scale, use an SFU such as LiveKit, mediasoup, Janus, or Jitsi. Peer-to-peer WebRTC is not enough for reliable 100k-user production traffic.

## Current Backend Limits

- A single backend replica can only send to WebSocket sessions connected to that replica.
- Active call rooms are local to the replica. For multi-replica deployment, either use sticky sessions so matched users stay on the same replica, or move signaling/chat delivery to Redis pub/sub.
- Matching scans a bounded Redis queue window instead of the entire queue to avoid O(n) and O(n^2) work against very large queues.
- Queue-size updates are sent directly on meaningful state changes instead of broadcasting every queue change to all waiting users.

## Railway Environment

Use Railway Redis internal URL:

```env
REDIS_URL=${{Redis.REDIS_URL}}
```

Suggested backend env knobs per replica:

```env
SERVER_TOMCAT_MAX_CONNECTIONS=100000
SERVER_TOMCAT_THREADS_MAX=800
SERVER_TOMCAT_ACCEPT_COUNT=10000
SPEEDLINK_SCHEDULER_THREADS=8
DB_POOL_MAX_SIZE=20
DB_POOL_MIN_IDLE=5
```

These values remove the low connection cap, but they do not mean one small server can actually sustain 100,000 live users. Scale by adding replicas, not by endlessly increasing one instance. For 100,000 concurrent WebSocket users, start load testing with at least 8-12 backend replicas and tune based on memory, CPU, Redis latency, load-balancer limits, operating-system file-descriptor limits, and WebSocket churn.
