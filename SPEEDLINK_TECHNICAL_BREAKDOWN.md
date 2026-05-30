# SpeedLink Technical Breakdown

This document explains SpeedLink from the top-level product idea down to the implementation details needed to explain, debug, redesign, or rebuild it from scratch.

It is written for two audiences:

- A lecturer, interviewer, or engineer who wants architecture, design choices, scalability, and implementation details.
- A 10-year-old child who needs the simplest possible mental model.

---

## Services Used In SpeedLink

These are the major services, frameworks, protocols, and runtime pieces used by the project.

| Area | Service / Technology | Purpose |
|---|---|---|
| Frontend | React | Builds the browser UI. |
| Frontend build | Vite | Development server and production frontend bundler. |
| Frontend icons | lucide-react | Icon components used in buttons and UI. |
| Backend | Java 17 | Backend programming language/runtime. |
| Backend framework | Spring Boot 3.3.5 | REST APIs, dependency injection, configuration, application startup. |
| REST API | Spring Web | HTTP endpoints such as auth, stats, admin, profile, suggestions. |
| WebSocket | Spring WebSocket | Real-time queue, matching, signaling, chat, and call events. |
| Security | Spring Security | CORS, route access rules, request security filter chain. |
| Database ORM | Spring Data JPA / Hibernate | Maps Java entities to database tables. |
| Database | PostgreSQL | Production relational database. |
| Local database | H2 | Default local fallback database. |
| Cache / realtime state | Redis | Shared queue, profiles, pending matches, pair cooldowns, locks. |
| Authentication provider | Supabase Auth | Email/password signup, login, verification, password reset. |
| App session tokens | JWT via java-jwt | Backend-issued app token used for REST and WebSocket auth. |
| Video calls | WebRTC | Browser-to-browser audio/video media connection. |
| Signaling | SpeedLink WebSocket server | Exchanges WebRTC offers, answers, and ICE candidates. |
| NAT traversal | Google STUN server | Helps browsers discover public network addresses. |
| Containers | Docker | Backend/frontend container build definitions. |
| Local infrastructure | Docker Compose | Runs PostgreSQL, Redis, backend, frontend together. |
| Frontend hosting | Vercel or static hosting | Serves the React/Vite build. |
| Backend hosting | Railway or Docker-capable host | Runs the Spring Boot backend. |

## Platform Service Catalog

The previous table lists the technologies used. This table explains SpeedLink as a group of connected platform services. Some of these are separate external services, such as Supabase, PostgreSQL, and Redis. Others are logical services inside the same Spring Boot backend, such as matching, admin, profile, and call signaling. In real production architecture, each logical service could later be extracted into a separate microservice if scale or team size requires it.

| Platform Service | Implemented By | Main Responsibility | Input | Output | Connected To | Why It Matters |
|---|---|---|---|---|---|---|
| Landing / Marketing Service | React frontend pages and static assets | Presents SpeedLink, explains the value proposition, and routes users to signup/login/app. | Browser request to the site. | Landing UI, CTA buttons, product visuals. | Frontend hosting, assets, route state. | This is the user's first impression and conversion entry point. |
| Frontend Application Service | React + Vite app in `frontend/src/App.jsx` | Runs the user interface, state management, routing, API calls, WebSocket connection, and WebRTC setup. | User clicks, form data, REST responses, WebSocket events. | Rendered pages, API requests, WebSocket messages, WebRTC actions. | Backend REST API, WebSocket `/ws`, Supabase client, browser media APIs. | This is the control center in the browser. |
| Routing / Navigation Service | Manual route handling in React | Maps URL paths to pages such as landing, signup, signin, app, profile, admin, reset password. | Browser path and navigation actions. | Correct page component displayed. | React state, browser history API. | Lets the app behave like a multi-page product without a separate router library. |
| Authentication Service | Supabase Auth + `AuthService` + `AuthController` | Verifies user identity and creates/links SpeedLink accounts. | Email/password session, Supabase access token. | SpeedLink JWT and profile. | Supabase Auth, PostgreSQL `user_accounts`, frontend auth pages. | Users must be trusted before they can enter matching or calls. |
| Email Verification Service | Supabase Auth email system | Sends and validates email verification links. | User email and Supabase signup/reset flow. | Verified Supabase session. | Supabase Auth, frontend auth flow, backend Supabase validation. | Prevents fake or unverified email accounts from entering the platform. |
| Password Reset Service | Supabase Auth reset flow | Allows users to reset passwords without SpeedLink storing passwords. | Reset email request and Supabase session. | Password reset confirmation. | Supabase Auth, frontend reset page, backend `/api/auth/password-reset`. | Keeps password handling outside SpeedLink backend, reducing security risk. |
| Email Delivery Provider Service | Supabase default email, or future SMTP/Resend integration | Delivers verification, reset, and notification emails. | Email event such as signup verification or reset request. | Email delivered to user inbox. | Supabase email settings, optional SMTP/Resend provider. | Reliable email delivery is required for signup and account recovery. |
| App Token Service | `AppTokenService` with JWT | Issues and verifies SpeedLink app tokens after Supabase identity is trusted. | SpeedLink user id or Authorization header. | Signed JWT or verified subject user id. | `AuthService`, REST endpoints, WebSocket auth. | Gives SpeedLink its own session layer independent of Supabase client state. |
| User Account Service | `UserAccount` entity + `UserAccountRepository` | Stores identity, contact, and profile data. | Signup data, Supabase user id, profile update data. | Persisted user account and profile. | PostgreSQL, AuthService, MatchingService, Admin dashboard. | This is the permanent user record. |
| Profile Service | Profile DTO/model + profile update APIs + realtime profile update | Manages user professional identity and matching attributes. | Display name, role, lookingFor, expertise, goals, interests, etc. | Saved profile and cached profile. | PostgreSQL, Redis profile cache, frontend profile page, matching engine. | Matching quality depends heavily on profile quality. |
| Profile Photo / Asset Service | Frontend file input and profile photo field | Lets users attach a visual identity to their profile. | Uploaded image or encoded/string image reference. | `profilePhoto` stored in profile data. | Frontend upload control, profile state, database field. | Makes matches feel more human and recognizable. |
| Matching Window Service | `MatchingWindowService` + `app_settings` | Controls when Search is open or closed. | Admin schedule settings, current time. | Open/closed status, next open/close timestamps. | Admin page, MatchingService, PostgreSQL app settings. | Lets the platform run live networking only during desired time windows. |
| Search Service | Frontend Search button + internal `joinQueue` WebSocket event | Starts the user matching process. | User click, profile, matching mode. | User enters realtime queue. | Frontend dashboard, WebSocket, MatchingService, Redis queue. | This is the main product action from the user's perspective. |
| Queue Service | Redis list `speedlink:queue` + MatchingService queue helpers | Stores users waiting to be matched. | User ids entering/leaving Search. | Ordered waiting list. | MatchingService, Redis, queue status events. | Without a queue, the system cannot know who is available right now. |
| Matching Engine Service | `MatchingService.findBestMatch()` and `compatibilityScore()` | Selects the best pair from waiting users. | Queued users, cached profiles, matching mode. | Pending match between two users. | Redis queue, profile cache, WebSocket match offers. | This is the core intelligence of SpeedLink. |
| Matching Mode Service | `basic` and `advanced` matching mode state | Chooses between random/basic matching and profile-scored matching. | User selected mode. | Matching behavior and score strategy. | Frontend segmented control, MatchingService `matchingModes`. | Gives users control over whether they want random exploration or relevant matching. |
| Pending Match Service | Redis `speedlink:pendingMatch:*` + `PendingMatch` model | Tracks a match offer before both users accept. | Matched user ids, expiry time, accepted users. | Match offer state. | WebSocket match-offer, accept/reject flow, expiry scheduler. | Separates "matched" from "in a call"; both users must still agree. |
| Accept / Join Room Service | `acceptMatch()` + per-match mutation lock | Records accepts safely and starts room when both users accept. | `acceptMatch` event with match id. | `match-accepted` and possibly `call-started`. | Redis pending match, WebSocket, room service. | Prevents stuck matches and makes entering the room reliable. |
| Distributed Lock Service | Redis keys `speedlink:matchLock` and `speedlink:matchMutationLock:*` | Prevents race conditions during matching and accepting. | Lock requests from backend code. | Temporary ownership token. | Redis, MatchingService. | Protects production correctness when actions happen at the same time. |
| Reject / Cooldown Service | `rejectMatch()` + `speedlink:rejectedPair:*` TTL keys | Cancels rejected matches and prevents immediate rematching of the same pair. | Reject event. | Match cancelled, users requeued if available, cooldown key. | Redis, MatchingService, WebSocket events. | Avoids frustrating loops where two users keep getting the same rejected match. |
| Match Expiry Service | Scheduled executor in `MatchingService` | Cancels match offers if users do not accept within the accept window. | Pending match id and scheduled timeout. | `match-cancelled` event and optional requeue. | Scheduler, Redis pending match, WebSocket. | Keeps the queue moving and prevents dead matches. |
| Realtime Connection Service | `SpeedLinkWebSocketHandler` + frontend WebSocket setup | Maintains live bidirectional connection between browser and backend. | WebSocket connection with JWT token. | Server/client events. | AuthService, MatchingService, frontend event handlers. | Matching and calls need instant updates, not slow polling. |
| Presence Service | In-memory session maps in `MatchingService` | Tracks who is online, queued, in a room, or reconnecting. | WebSocket connect/disconnect events. | Presence state and admin stats. | WebSocket handler, MatchingService, admin dashboard. | The platform needs to know who is actually available now. |
| Reconnection Service | `restoreRealtimeState()` and frontend pending message handling | Restores user state after refresh, network drop, or tab reconnect. | New WebSocket connection for an existing user. | Restored queue, pending match, accepted state, or active call. | WebSocket, Redis, in-memory room maps, frontend refs. | Prevents users from losing matches/calls because of small network hiccups. |
| Session Replacement Service | BroadcastChannel frontend + backend `session-replaced` event | Avoids multiple browser tabs fighting over one user's realtime session. | Same user opening another active tab. | Old session closed or marked replaced. | Browser BroadcastChannel, WebSocket sessions. | Reduces duplicate events and confusing multi-tab behavior. |
| Call Room Service | `startCallIfReady()`, `activeRooms`, `callSessions` | Creates and manages temporary call rooms after both users accept. | Mutually accepted pending match. | Room id, call session, `call-started`. | MatchingService, ConversationSession, WebSocket, WebRTC frontend. | Converts a match into an actual live networking session. |
| WebRTC Signaling Service | WebSocket `signal` events forwarded by `forwardSignal()` | Passes offer, answer, and ICE candidate messages between room participants. | Signal payload from one participant. | Signal payload delivered to the other participant. | WebSocket, active room state, frontend RTCPeerConnection. | WebRTC cannot start without a signaling channel. |
| Video Media Service | Browser WebRTC APIs | Captures and streams audio/video between browsers. | Camera/microphone stream, WebRTC negotiation. | Peer-to-peer media stream. | Browser media APIs, STUN, optional future TURN. | This is what makes the networking session feel live and personal. |
| STUN / Network Discovery Service | Google STUN server | Helps browsers discover public network addresses for WebRTC. | ICE gathering request. | Public candidate addresses. | Browser RTCPeerConnection. | Improves chance of direct peer-to-peer video connection. |
| TURN Relay Service | Not currently configured; recommended future service | Relays media when direct peer-to-peer fails. | WebRTC media packets. | Relayed audio/video. | WebRTC clients, TURN provider such as coturn/Twilio/Metered. | Needed for production-grade reliability on strict networks. |
| In-Call Chat Service | WebSocket `chatMessage` + `forwardChatMessage()` | Sends short text messages inside active rooms. | Chat text and room id. | `chat-message` event to participants. | WebSocket, active room participants, frontend chat UI. | Gives users communication even if media setup is slow or unavailable. |
| Call Timer Service | Scheduled executor + `CALL_WINDOW_MILLIS` | Ends calls after the normal call window unless continued. | Room id and expected end time. | `call-ended` or continued call state. | Scheduler, call sessions, WebSocket. | Keeps speed-networking sessions short and structured. |
| Continue Call Service | WebSocket `continueCall` event | Lets users continue a session beyond normal timer. | User action in call screen. | `call-continued` event. | MatchingService, call session state, frontend call controls. | Useful when a conversation is valuable and both want more time. |
| Call Reconnect Grace Service | `scheduleCallReconnectGrace()` | Holds a room briefly if someone disconnects accidentally. | WebSocket disconnect while in call. | Peer reconnecting notice or call end. | WebSocket, scheduler, call room maps. | Prevents accidental network drops from instantly killing a session. |
| Conversation Analytics Service | `ConversationSession` + admin dashboard queries | Records started/ended calls for operational visibility. | Room start/end events. | Conversation rows and dashboard summaries. | PostgreSQL, MatchingService, AdminController. | Helps understand platform usage and session success. |
| Suggestion / Feedback Service | `UserSuggestion` + `/api/suggestions` | Collects user feedback and feature requests. | Suggestion form submission. | Saved suggestion and admin dashboard visibility. | Frontend suggestion widget, REST API, PostgreSQL. | Gives users a way to improve the product. |
| Admin Service | `AdminController` + AdminPage | Lets operators view dashboard data and update matching window. | Admin key, dashboard request, schedule update. | Admin stats or updated settings. | MatchingWindowService, MatchingService, PostgreSQL. | Needed to operate live networking sessions. |
| Health Service | `GET /api/health` | Simple backend availability check. | HTTP GET. | `{ "status": "ok" }`. | Hosting platform, frontend readiness checks. | Helps confirm the backend is alive. |
| Stats Service | `GET /api/stats` + `MatchingService.snapshot()` | Exposes live platform snapshot. | HTTP GET. | Online users, queued users, pending matches, active rooms. | MatchingService, matching window service, frontend/admin. | Useful for dashboards and status indicators. |
| CORS / Browser Trust Service | `SecurityConfig` + `CorsResponseFilter` + `WebSocketConfig` | Controls which frontend origins may call APIs and open WebSockets. | Browser Origin header. | Allowed or blocked cross-origin request. | Frontend hosting domains, backend security config. | Prevents random websites from using the backend from browsers. |
| Database Compatibility Service | `DatabaseCompatibilityConfig` | Applies small compatibility fixes for old schema states. | Backend startup. | Legacy column removed, phone index ensured. | PostgreSQL, JdbcTemplate. | Helps older deployments keep running after auth schema changes. |
| Configuration Service | Spring properties and environment variables | Controls runtime behavior for local/prod profiles. | `.properties` files and environment variables. | Runtime values for DB, Redis, CORS, JWT, matching window. | Spring Boot, hosting platform, Docker Compose. | Lets the same code run safely in local and production environments. |
| Deployment Service | Dockerfiles, Docker Compose, Vercel/Railway-style hosting | Builds and runs frontend/backend/infrastructure. | Source code and environment variables. | Running production app. | Docker, PostgreSQL, Redis, frontend host, backend host. | Turns the codebase into a live system users can access. |

### How These Services Connect

```text
User Browser
    |
    | Landing / Auth / Search / Call UI
    v
Frontend Application Service
    |
    | REST
    v
Auth/Profile/Admin/Suggestion/Stats Services
    |
    +--> Supabase Auth / Email Delivery
    +--> PostgreSQL
    |
    | WebSocket
    v
Realtime Connection Service
    |
    +--> Search Service
    +--> Queue Service
    +--> Matching Engine Service
    +--> Pending Match Service
    +--> Accept / Join Room Service
    +--> Call Room Service
    +--> WebRTC Signaling Service
    |
    +--> Redis
    +--> Conversation Analytics Service
```

### Simple Child Explanation Of The Service Catalog

Think of SpeedLink like a big event with many helpers:

- The landing helper welcomes people.
- The auth helper checks ID cards.
- The profile helper remembers who everyone is.
- The search helper puts people in a line.
- The matching helper chooses two people who should talk.
- The accept helper asks both people, "Do you both want to talk?"
- The room helper opens a private table.
- The video helper lets them see and hear each other.
- The chat helper lets them type.
- The admin helper helps the organizer watch the event.
- The database helper writes important things in a notebook.
- The Redis helper keeps fast sticky notes for what is happening right now.

Simple explanation for a child:

> SpeedLink is like a school fair helper. React draws the screens, Spring Boot is the organizer, PostgreSQL is the notebook, Redis is the quick sticky-note board, Supabase checks IDs, WebSocket is the walkie-talkie, and WebRTC is the video phone.

---

# Level 1: Project Overview

## What Is SpeedLink?

SpeedLink is a real-time professional networking platform. It lets users create a profile, start a live Search session, get matched with another user, accept the match, and enter a short video call.

The platform is similar in spirit to a networking version of a speed-dating app:

1. You sign up.
2. You describe who you are and who you want to meet.
3. You click Search during an allowed time window.
4. The system finds a relevant person.
5. Both users accept.
6. The app opens a live video room.
7. The session ends or continues based on user action.

Simple explanation:

> SpeedLink is an app that helps people meet useful new people quickly. It is like pressing a button and being introduced to someone who might help you build something, learn something, or find an opportunity.

## What Problem Does It Solve?

Professional networking is often slow, awkward, and manual. Users usually need to:

- Search LinkedIn manually.
- Send cold messages.
- Wait for replies.
- Attend large events where introductions are random.
- Spend time finding people with compatible goals.

SpeedLink solves this by making networking:

- Real-time.
- Short and focused.
- Based on user profiles and interests.
- Easy to start with one button.
- More interactive through live video.

## Target Users

Current SpeedLink copy and profile fields point toward:

- Developers.
- Founders.
- Designers.
- AI builders.
- Startup people.
- Students or early professionals looking for collaborators.
- People looking for MVP partners, feedback, hiring, mentoring, or opportunities.

## Complete User Journey

```text
Landing Page
    |
    v
Signup / Login
    |
    v
Profile Setup
    |
    v
Dashboard
    |
    v
Search
    |
    v
Match Found
    |
    v
Both Users Accept
    |
    v
Video Room Opens
    |
    v
Chat / Talk / Continue / End
    |
    v
Conversation Stored For Admin Analytics
```

Step-by-step:

1. User opens SpeedLink.
2. Landing page explains real-time professional matching.
3. User signs up or logs in using Supabase Auth.
4. Backend exchanges the Supabase identity for a SpeedLink JWT.
5. User profile is saved in the backend database.
6. User opens the dashboard.
7. Frontend opens a WebSocket connection using the SpeedLink JWT.
8. User clicks Search.
9. Backend stores the user in Redis queue.
10. Matching service scans queued users and selects a compatible pair.
11. Backend sends `match-offer` to both users through WebSocket.
12. Users click Accept.
13. Backend records accepted users.
14. When both accepted, backend creates a room.
15. Backend sends `call-started`.
16. Frontend creates a WebRTC peer connection.
17. Users exchange offer, answer, and ICE candidates through WebSocket.
18. Browser media flows peer-to-peer where possible.
19. User ends the call or time expires.
20. Backend updates the conversation session.

## Major Features

- Landing page.
- Email/password signup.
- Email verification through Supabase.
- Login and password reset.
- User profile creation and update.
- Matching window schedule.
- Search start/stop backed by an internal queue.
- Basic/random matching mode.
- Advanced profile-based matching mode.
- Match accept/reject flow.
- Real-time WebSocket events.
- WebRTC video/audio calling.
- In-call chat.
- Reconnect handling.
- Admin dashboard.
- User suggestions/feedback.
- Production configuration for PostgreSQL, Redis, JWT, CORS, and schema validation.

---

# Level 2: High-Level System Architecture

## Architecture Diagram

```text
                         +----------------------+
                         |      User Browser    |
                         | React + Vite bundle  |
                         +----------+-----------+
                                    |
              HTTP REST             | WebSocket              WebRTC media
       /api/auth, /api/stats        | /ws                    peer-to-peer
                                    |
                                    v
                         +----------------------+
                         | Spring Boot Backend  |
                         | REST + WebSocket     |
                         +---+------+-----+-----+
                             |      |     |
                 JPA/Hibernate     |     | RedisTemplate
                             |      |     |
                             v      v     v
                    +------------+  |  +------------+
                    | PostgreSQL |  |  |   Redis    |
                    | durable DB |  |  | realtime   |
                    +------------+  |  | state      |
                                    |  +------------+
                                    |
                                    v
                           +----------------+
                           | Supabase Auth  |
                           | identity check |
                           +----------------+
```

## Frontend

The frontend is a React app built by Vite. It is responsible for:

- Rendering pages.
- Managing UI state.
- Calling REST APIs.
- Opening the WebSocket.
- Sending queue/match/call events.
- Creating WebRTC peer connections.
- Showing local and remote video streams.

The main frontend file is `frontend/src/App.jsx`. It contains:

- Route state.
- Auth state.
- Profile state.
- Queue state.
- Match state.
- Call state.
- WebSocket setup.
- WebRTC setup.
- Page components.

## Backend

The backend is a Spring Boot application. It is responsible for:

- Validating requests.
- Managing app users.
- Issuing SpeedLink JWTs.
- Connecting to Supabase for identity verification.
- Storing durable data in PostgreSQL.
- Storing real-time queue and match state in Redis.
- Handling WebSocket messages.
- Matching users.
- Creating call rooms.
- Forwarding WebRTC signaling messages.
- Recording conversation sessions.

## Database

PostgreSQL stores durable business data:

- User accounts.
- Conversation sessions.
- App settings.
- Suggestions.

Redis stores fast real-time state:

- Queue.
- Cached profiles.
- Pending matches.
- User-to-match mapping.
- Pair cooldowns.
- Distributed locks.

Why two data stores?

- PostgreSQL is reliable and good for permanent records.
- Redis is fast and good for temporary real-time data.

Simple explanation:

> PostgreSQL is the school record book. Redis is the teacher's sticky note board for things happening right now.

## Authentication

Authentication has two layers:

1. Supabase Auth proves the user owns the email/password account.
2. SpeedLink backend issues its own JWT after it trusts Supabase.

Why not use Supabase token everywhere?

- The backend wants its own app-level identity.
- WebSocket authentication becomes simple.
- Backend can use the SpeedLink user id as the JWT subject.

## Matching System

The matching system lives mostly in `MatchingService`.

Responsibilities:

- Keep connected sessions.
- Keep queue state.
- Save/load profile snapshots.
- Score compatibility.
- Create pending matches.
- Track accepted users.
- Start rooms.
- Expire matches and calls.
- Requeue users when needed.

## Real-Time Communication

SpeedLink uses WebSocket for:

- Queue status.
- Match offers.
- Accept/reject.
- Call started.
- WebRTC signaling.
- Chat.
- Reconnection state.

WebSocket is used because HTTP is request/response only, while matching and video signaling require instant server-to-client messages.

## Video Calling

Video calls use WebRTC.

The backend does not carry video/audio media. It only helps both browsers exchange setup messages:

- Offer.
- Answer.
- ICE candidates.

After setup, browsers send media directly peer-to-peer when possible.

## APIs

The backend exposes REST endpoints under `/api` and a WebSocket endpoint at `/ws`.

REST is used for:

- Signup.
- Login token exchange.
- Profile read/update.
- Admin actions.
- Suggestions.
- Health/stats.

WebSocket is used for:

- Everything that needs live push updates.

## Hosting And Deployment

Expected deployment model:

```text
Frontend static build -> Vercel / CDN / Nginx
Backend Spring Boot   -> Railway / Docker host
PostgreSQL            -> Managed PostgreSQL or Docker
Redis                 -> Managed Redis or Docker
Supabase              -> Hosted Supabase project
```

Production config highlights:

- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- `SPEEDLINK_JWT_SECRET` must be strong.
- `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` must be set.
- CORS should allow only SpeedLink domains.

## What Happens When A User Clicks A Button?

Example: Search button.

```text
User clicks Search
    |
React handler builds message:
{ type: "joinQueue", profile, matchingMode }
    |
WebSocket sends JSON to backend /ws
    |
SpeedLinkWebSocketHandler parses JSON into ClientMessage
    |
MatchingService.handle() switches on type
    |
joinQueue() validates window/profile/state. The user-facing label is Search, but the internal WebSocket event is still called `joinQueue`.
    |
User id added to Redis queue
    |
tryMatchQueuedUsers() scans queue
    |
If match exists, backend sends match-offer to both users
```

---

# Level 3: End-to-End Flow Analysis

## User Signup

### Technical Flow

```text
React Signup Form
    |
Supabase signUp / email verification
    |
Frontend receives Supabase access token
    |
POST /api/auth/signup
    |
AuthController.signup()
    |
AuthService.signup()
    |
SupabaseAuthClient.requireVerifiedEmail()
    |
UserAccountRepository checks duplicates
    |
Insert user_accounts row
    |
AppTokenService.issueToken()
    |
Return { token, profile }
```

### User Enters Details

The user enters:

- Email.
- Password.
- Phone.
- Display name.
- Role.
- Looking-for target.
- Expertise.
- Goals.
- Intent.
- Bio.
- Interests.
- Company type.
- Links and profile metadata.

Frontend validates basic form state. Backend validates important fields using DTO annotations:

- `@Email`
- `@NotBlank`
- `@Size`

### Authentication Process

Supabase handles email/password identity. SpeedLink backend does not store passwords.

Signup depends on a verified Supabase session:

- Frontend signs up/verifies through Supabase.
- Backend receives `supabaseAccessToken`.
- Backend calls Supabase `/auth/v1/user`.
- If the Supabase user matches the email, backend trusts the identity.

### Database Operations

Backend checks:

- Existing account by Supabase user id.
- Existing account by email.
- Existing account by phone.

If clear, backend creates a `UserAccount`.

### Session Creation

Backend creates a JWT:

```text
subject = SpeedLink user id
issuedAt = now
expiresAt = now + token TTL
signed with SPEEDLINK_JWT_SECRET
```

Frontend stores the token in local/session storage depending on remember-me behavior.

### Error Handling

Common errors:

- Email invalid.
- Email already exists.
- Phone already exists.
- Supabase session invalid.
- Required profile fields missing.
- Database unique constraint violation.

Errors are returned as `ApiError` objects with a user-readable message.

Simple explanation:

> Supabase checks "is this really you?" SpeedLink then writes your profile into its notebook and gives your browser a special pass card called a token.

## User Login

```text
User enters email/password
    |
Supabase signInWithPassword()
    |
Frontend receives Supabase access token
    |
POST /api/auth/supabase
    |
Backend verifies Supabase token
    |
Backend finds or creates SpeedLink account
    |
Backend returns SpeedLink JWT + profile
```

Important backend methods:

- `AuthController.supabase()`
- `AuthService.exchangeSupabaseToken()`
- `SupabaseAuthClient.fetchUser()`
- `AuthService.resolveSupabaseAccount()`
- `AppTokenService.issueToken()`

## Starting Search / Joining The Internal Networking Queue

```text
User clicks Search
    |
Frontend sends WebSocket event `joinQueue`
    |
Backend checks matching window
    |
Backend updates/saves profile if provided
    |
Backend checks profile readiness
    |
Backend checks user not already in match/room
    |
Backend adds user id to Redis queue
    |
Backend sends queue-status
    |
Backend runs tryMatchQueuedUsers()
```

Availability is stored in:

- Redis queue list: `speedlink:queue`
- In-memory `userQueuedAt`
- In-memory/Redis profile cache

Why Redis?

- Fast queue operations.
- Shared state across backend instances.
- Better production scalability than only in-memory lists.

## User Matching

Matching currently supports:

- Basic mode: random-ish compatibility score of `1`.
- Advanced mode: profile-based compatibility.

Compatibility checks:

- Role vs looking-for match.
- Whether one user is open/random.
- Shared interests.
- Shared intent.
- Shared company type.
- Rejected-pair cooldown.

Approximate scoring:

```text
Mutual role fit: +8
One-side role fit with open target: +5
Shared interests: +3 each
Shared intent: +4 each
Shared company type: +2 each
Both open intent: +6
```

Matching flow:

```text
tryMatchQueuedUsers()
    |
Acquire Redis lock speedlink:matchLock
    |
Read first 250 users from queue
    |
Score all pairs
    |
Pick highest score
    |
Remove both from queue
    |
Create PendingMatch
    |
Send match-offer to both
    |
Schedule 30-second expiry
```

## Starting A Video Call

```text
User A accepts
    |
Backend records A in acceptedUsers
    |
User B accepts
    |
Backend records B in acceptedUsers
    |
Both accepted?
    |
Create roomId
    |
Save ConversationSession
    |
Send call-started to both
    |
Frontend creates RTCPeerConnection
    |
Initiator sends offer
    |
Peer sends answer
    |
Both exchange ICE candidates
    |
Media flows peer-to-peer
```

The backend uses a per-match lock when accepting:

```text
speedlink:matchMutationLock:<matchId>
```

Why?

If both users accept at the exact same time, the lock prevents one accept from overwriting the other.

## Ending A Session

Session ends when:

- A user clicks end.
- Call timer expires.
- Peer disconnect grace expires.

Backend operations:

- Remove room from `activeRooms`.
- Remove users from `userToRoom`.
- Remove disconnected call markers.
- Send `call-ended`.
- Update `conversation_sessions` row with `ENDED`, `endedAt`, and reason.

Analytics:

- Admin dashboard reads recent `conversation_sessions`.
- It can show started/ended sessions, duration, users, status, and end reason.

---

# Level 4: Database Deep Dive

## ER Diagram

```text
+-------------------+        +-------------------------+
| user_accounts     |        | conversation_sessions   |
|-------------------|        |-------------------------|
| id PK             |<--.    | room_id PK              |
| email UNIQUE      |   |    | match_id                |
| supabase_user_id  |   |    | user_a_id               |
| phone UNIQUE      |   |    | user_b_id               |
| display_name      |   |    | user_a_name             |
| role              |   |    | user_b_name             |
| looking_for       |   |    | started_at              |
| ...profile fields |   |    | ended_at                |
| created_at        |   |    | status                  |
| updated_at        |   |    | end_reason              |
+-------------------+   |    +-------------------------+
                        |
                        |    +-------------------------+
                        '--- | user_suggestions        |
                             |-------------------------|
                             | id PK                   |
                             | user_id                 |
                             | category                |
                             | title                   |
                             | details                 |
                             | created_at              |
                             +-------------------------+

+-------------------+
| app_settings      |
|-------------------|
| id PK             |
| matching_window_* |
| created_at        |
| updated_at        |
+-------------------+
```

Note: The Java entities do not declare JPA foreign-key relationships with `@ManyToOne`. They store user ids as strings. That keeps the model simple but means referential integrity is mostly handled in application logic.

## Table: `user_accounts`

Purpose:

Stores SpeedLink user identity and profile data.

Primary key:

- `id`

Unique fields:

- `email`
- `supabase_user_id`
- `phone`
- PostgreSQL compatibility config also ensures a unique partial phone index for non-empty phone numbers.

Columns:

| Column | Purpose |
|---|---|
| `id` | SpeedLink internal user id. |
| `email` | User email. |
| `supabase_user_id` | Supabase Auth user id. |
| `phone` | WhatsApp phone number. |
| `email_verified` | Whether email was verified. |
| `phone_verified` | Whether phone exists/verified logically. |
| `display_name` | User-visible name. |
| `role` | What user is. |
| `looking_for` | Who user wants to meet. |
| `expertise` | Skills. |
| `goals` | Goals. |
| `intent` | Networking purpose. |
| `bio` | Short user bio. |
| `interests` | Interest tags/text. |
| `company_type` | Startup, enterprise, etc. |
| `age_range` | Optional age range. |
| `linkedin_url` | LinkedIn profile. |
| `portfolio_url` | Portfolio/project URL. |
| `location` | User location. |
| `experience_level` | Beginner/intermediate/senior etc. |
| `availability` | Availability text. |
| `profile_photo` | Photo URL/base64/string reference. |
| `created_at` | Creation timestamp. |
| `updated_at` | Last update timestamp. |

Inserted when:

- A new user signs up.
- A Supabase user logs in and no SpeedLink account exists.

Updated when:

- Profile is edited.
- Supabase account is linked.

Deleted when:

- No delete endpoint currently exists.

## Table: `conversation_sessions`

Purpose:

Stores durable records of video/networking sessions.

Primary key:

- `room_id`

Columns:

| Column | Purpose |
|---|---|
| `room_id` | Unique room id created by backend. |
| `match_id` | Match that produced the room. |
| `user_a_id` | First participant id. |
| `user_b_id` | Second participant id. |
| `user_a_name` | Name snapshot. |
| `user_a_role` | Role snapshot. |
| `user_b_name` | Name snapshot. |
| `user_b_role` | Role snapshot. |
| `started_at` | When call room started. |
| `ended_at` | When call ended. |
| `status` | `ACTIVE` or `ENDED`. |
| `end_reason` | Why the session ended. |

Inserted when:

- Both users accept and backend starts a room.

Updated when:

- Call ends or expires.

Deleted when:

- No normal delete path currently exists.

## Table: `user_suggestions`

Purpose:

Stores user feedback, feature requests, and modifications.

Primary key:

- `id`

Columns:

| Column | Purpose |
|---|---|
| `id` | Suggestion id. |
| `user_id` | User who submitted it. |
| `category` | Suggestion / Feature suggestion / Modification. |
| `title` | Short title. |
| `details` | Full details. |
| `created_at` | Submission time. |

Inserted when:

- Logged-in user submits suggestion.

Updated/deleted:

- No update/delete endpoint currently exists.

## Table: `app_settings`

Purpose:

Stores platform-wide settings, especially the matching window.

Primary key:

- `id`, currently usually `default`.

Columns:

| Column | Purpose |
|---|---|
| `id` | Settings row id. |
| `matching_window_enabled` | Whether time window is enforced. |
| `matching_window_start` | Start time. |
| `matching_window_end` | End time. |
| `matching_window_zone_id` | Timezone, default Asia/Kolkata. |
| `created_at` | Creation timestamp. |
| `updated_at` | Last update timestamp. |

Inserted when:

- Backend first needs settings and row does not exist.

Updated when:

- Admin changes matching window.

Deleted:

- No delete path currently exists.

## Redis Data Model

Redis is not SQL, but it is part of the data model.

| Key | Type | Purpose |
|---|---|---|
| `speedlink:queue` | List | Waiting user ids. |
| `speedlink:profile:<userId>` | String JSON | Cached profile. |
| `speedlink:pendingMatch:<matchId>` | String JSON | Pending match state. |
| `speedlink:userToMatch:<userId>` | String | Maps user to pending match. |
| `speedlink:rejectedPair:<pair>` | String with TTL | Prevents immediate rematch after rejection. |
| `speedlink:matchLock` | String with TTL | Prevents concurrent queue matching. |
| `speedlink:matchMutationLock:<matchId>` | String with TTL | Prevents concurrent accept overwrite. |

---

# Level 5: Frontend Deep Dive

## Folder Structure

```text
frontend/
    package.json
    vite.config.js
    vercel.json
    Dockerfile
    index.html
    src/
        main.jsx
        App.jsx
        styles.css
        assets/
            landing-network.png
            speedlink-pro/
                images and branding assets
```

## Main Frontend Responsibilities

`App.jsx` is currently a large single-file application. It contains:

- Routing.
- Auth flows.
- Profile form.
- Dashboard.
- Matching UI.
- Admin UI.
- WebSocket lifecycle.
- WebRTC lifecycle.
- Chat.
- Suggestions.

For a future redesign, you could split it into:

```text
src/
    app/
        App.jsx
        routes.js
    api/
        http.js
        realtime.js
    auth/
        AuthPage.jsx
        useAuth.js
    profile/
        ProfilePage.jsx
        profileModel.js
    matching/
        MatchingApp.jsx
        MatchDialog.jsx
        useMatchingSocket.js
    call/
        VideoCall.jsx
        useWebRtcCall.js
    admin/
        AdminPage.jsx
    components/
        BrandBlock.jsx
        buttons, inputs, cards
```

## Routing

Routing is implemented manually by reading `window.location.pathname` and storing route state.

Conceptually:

```text
/                 -> LandingPage
/signin           -> AuthPage sign in
/signup           -> AuthPage sign up
/reset-password   -> AuthPage reset password
/app              -> MatchingApp
/profile          -> ProfilePage
/admin            -> AdminPage
```

There is no React Router dependency. This keeps dependencies low but puts routing logic inside `App.jsx`.

## State Management

State is handled with React hooks:

- `useState` for UI and data.
- `useRef` for mutable live objects such as WebSocket, peer connection, media streams.
- `useEffect` for lifecycle side effects.
- `useCallback` for stable event handlers.
- `useMemo` for derived values.

Important state groups:

| State | Meaning |
|---|---|
| `token` | SpeedLink JWT. |
| `profile` | Current user profile. |
| `connected` | WebSocket connection state. |
| `queueStatus` | Queue state/message. |
| `matchingMode` | Basic or advanced. |
| `match` | Current pending match offer. |
| `accepted` | Backend confirmed accept. |
| `acceptPending` | Frontend is trying to send accept. |
| `call` | Current active call room. |
| `chatMessages` | In-call chat. |
| `events` | Activity log. |
| `matchingWindow` | Current open/closed schedule. |

## API Integration

REST calls use `fetch`.

Common pattern:

```text
apiRequest(path, options)
    |
adds base API URL
adds JSON headers
adds Authorization if token exists
parses JSON
throws readable errors
```

WebSocket integration uses:

```text
new WebSocket(`${WS_BASE_URL}?token=${token}`)
```

## Landing Page

Purpose:

- Explain product.
- Show brand.
- Lead user to signup/login.
- Show visual assets.

Important components:

- `LandingPage`
- `BrandBlock`

Backend communication:

- Loads stats/health indirectly through app state.

## Authentication Pages

Purpose:

- Signup.
- Sign in.
- Reset password.
- Email verification.

Main interactions:

- Supabase Auth methods.
- Backend `/api/auth/*` endpoints.

Flow:

```text
User submits auth form
    |
Supabase validates password/email/session
    |
Backend validates Supabase token
    |
Backend returns SpeedLink JWT
    |
Frontend stores token
    |
Navigate to app
```

## Dashboard / Search And Matching Screen

Purpose:

- Show profile readiness.
- Show matching window.
- Select basic/advanced mode.
- Start or stop Search. Internally, Search means joining or leaving the realtime queue.
- Display match offers.

Backend communication:

- WebSocket `joinQueue`
- WebSocket `leaveQueue`
- WebSocket `match-offer`
- WebSocket `queue-status`

## Waiting Room / Accept Dialog

When a match appears:

- Frontend shows peer profile.
- User can Accept or Reject.
- Accept sends `acceptMatch`.
- Reject sends `rejectMatch`.

Recent reliability improvement:

- Frontend displays `Joining` immediately.
- It retries `acceptMatch` every 300ms until backend confirms.
- It only shows confirmed accepted state after `match-accepted`.

## Video Call Screen

Purpose:

- Show local video.
- Show remote video.
- Toggle mic/camera.
- Chat.
- Continue/end call.

Backend communication:

- `signal`
- `chatMessage`
- `continueCall`
- `endCall`

Browser APIs:

- `navigator.mediaDevices.getUserMedia`
- `RTCPeerConnection`
- `MediaStream`

## Profile Page

Purpose:

- Edit profile fields.
- Upload/update profile photo.
- Save profile.

Backend communication:

- REST `PUT /api/auth/profile`
- WebSocket `updateProfile` can also update realtime profile state.

---

# Level 6: Backend Deep Dive

## Backend Folder Structure

```text
backend/src/main/java/com/speedlink/app/
    SpeedLinkApplication.java
    config/
        SecurityConfig.java
        WebSocketConfig.java
        RedisConfig.java
        CorsResponseFilter.java
        DatabaseCompatibilityConfig.java
    controller/
        AuthController.java
        AppController.java
        AdminController.java
    dto/
        request/response records
    entity/
        UserAccount.java
        ConversationSession.java
        UserSuggestion.java
        AppSetting.java
    model/
        WebSocket payload models
    repository/
        Spring Data repositories
    service/
        AuthService.java
        AppTokenService.java
        SupabaseAuthClient.java
        MatchingService.java
        MatchingWindowService.java
    websocket/
        SpeedLinkWebSocketHandler.java
```

## API Architecture

Controllers are thin. They:

- Receive HTTP requests.
- Validate request bodies.
- Read auth headers.
- Call services.
- Return DTOs/responses.
- Convert exceptions to API errors.

Services contain business logic:

- `AuthService`: user auth/profile logic.
- `AppTokenService`: JWT issue/verify.
- `SupabaseAuthClient`: calls Supabase.
- `MatchingService`: realtime matching and rooms.
- `MatchingWindowService`: schedule logic.

Repositories handle database access:

- `UserAccountRepository`
- `ConversationSessionRepository`
- `UserSuggestionRepository`
- `AppSettingRepository`

## Middleware / Configuration

### `SecurityConfig`

Configures:

- CORS.
- CSRF disabled.
- Public routes.
- Authenticated fallback.

Important note:

Some endpoints are marked `permitAll`, but controllers still manually authenticate where needed. For example `/api/suggestions` is permitted at the security layer but requires JWT inside `AppController`.

### `WebSocketConfig`

Registers:

```text
/ws -> SpeedLinkWebSocketHandler
```

It applies allowed origins/patterns from config.

### `CorsResponseFilter`

Adds CORS headers manually for allowed origins and handles OPTIONS preflight.

### `RedisConfig`

Creates `StringRedisTemplate`.

### `DatabaseCompatibilityConfig`

Applies small PostgreSQL compatibility changes:

- Drops legacy `password_hash` column if it exists.
- Ensures unique phone index for non-empty phone numbers.

## Authentication Flow

```text
HTTP Authorization: Bearer <SpeedLink JWT>
    |
AuthService.authenticate()
    |
AppTokenService.verifySubject()
    |
UserAccountRepository.findById(subject)
    |
Optional<UserAccount>
```

WebSocket authentication:

```text
Browser connects to /ws?token=<SpeedLink JWT>
    |
SpeedLinkWebSocketHandler.tokenFromUri()
    |
AuthService.authenticate(token)
    |
matchingService.connect(session, profile)
```

## REST Endpoints

### `GET /api/health`

Purpose:

- Health check.

Response:

```json
{ "status": "ok" }
```

Database:

- None.

### `GET /api/stats`

Purpose:

- Returns live matching snapshot.

Response includes:

- Online users.
- Profiles cached.
- Queue size.
- Pending matches.
- Active rooms.
- Matching window.

Database:

- Mostly in-memory/Redis state.
- Matching window may touch `app_settings`.

### `POST /api/suggestions`

Request:

```json
{
  "category": "feature",
  "title": "Better filters",
  "details": "Let me choose roles."
}
```

Validation:

- Category not blank, max 40.
- Title not blank, max 140.
- Details not blank, max 2000.
- Must have valid JWT.

Database:

- Inserts `user_suggestions`.

Response:

```json
{
  "id": "...",
  "message": "Suggestion submitted."
}
```

### `POST /api/auth/supabase`

Request:

```json
{ "accessToken": "<supabase access token>" }
```

Flow:

1. Controller validates request.
2. AuthService verifies Supabase user.
3. Finds or creates SpeedLink user.
4. Issues SpeedLink JWT.

Response:

```json
{
  "token": "<speedlink jwt>",
  "profile": { "...": "..." }
}
```

### `GET /api/auth/supabase-config`

Purpose:

- Lets frontend compare Supabase project ref with backend config.

Response:

```json
{ "projectRef": "..." }
```

### `POST /api/auth/verification-link`

Purpose:

- Pre-checks whether signup/reset should proceed.
- Checks duplicate email/phone.
- Checks Supabase existing email/phone using service role key when available.

Response:

- `204 No Content` if OK.

### `POST /api/auth/email-session`

Purpose:

- Confirms a Supabase session belongs to the expected verified email.

Response:

- `204 No Content`.

### `POST /api/auth/signup`

Request:

Contains:

- Email.
- Phone.
- Supabase access token.
- Required profile fields.
- Optional profile fields.

Flow:

1. Normalize email and phone.
2. Require email.
3. Require verified Supabase email.
4. Build Profile.
5. Ensure profile ready.
6. Check duplicate Supabase id/email/phone.
7. Save `UserAccount`.
8. Issue JWT.
9. Return token/profile.

### `POST /api/auth/password-reset`

Purpose:

- Confirms reset session and checks account exists.

Response:

- `204 No Content`.

### `GET /api/auth/me`

Purpose:

- Get current user's profile from JWT.

Response:

```json
{ "profile": { "...": "..." } }
```

Errors:

- `401 Unauthorized` if JWT invalid/missing.

### `PUT /api/auth/profile`

Purpose:

- Update profile.

Request:

- Same profile fields as signup except auth fields.

Validation:

- Display name, role, lookingFor required.
- Size limits enforced.

Database:

- Updates `user_accounts`.

### `GET /api/matching-window`

Purpose:

- Returns whether search is open now and next open/close times.

Database:

- Reads/creates `app_settings`.

### `PUT /api/admin/matching-window`

Purpose:

- Admin changes matching window.

Auth:

- Requires `X-SpeedLink-Admin-Key`.

Request:

```json
{
  "enabled": true,
  "startTime": "21:00",
  "endTime": "22:00",
  "zoneId": "Asia/Kolkata",
  "clearQueueOnClose": true
}
```

Database:

- Updates `app_settings`.

Side effect:

- Can clear realtime queue if closing window.

### `GET /api/admin/dashboard`

Purpose:

- Admin operational view.

Auth:

- Requires `X-SpeedLink-Admin-Key`.

Data:

- Online users.
- Queued users.
- Conversations.
- Suggestions.
- Counts.

## About The Example `POST /api/matchmaking/join`

Your current project does not implement `POST /api/matchmaking/join` as a REST endpoint. The user clicks Search, and internally that joins the realtime queue over WebSocket using:

```json
{
  "type": "joinQueue",
  "profile": { "...": "..." },
  "matchingMode": "advanced"
}
```

Why WebSocket instead of REST?

- The user is starting Search, which internally means entering a live queue.
- The backend may immediately find a match.
- The backend needs to push `queue-status` or `match-offer` without waiting for another HTTP request.

Equivalent logic lives in `MatchingService.joinQueue()`.

---

# Level 7: Real-Time System Deep Dive

## WebSocket Architecture

```text
Browser
    |
    | connects to /ws?token=<jwt>
    v
SpeedLinkWebSocketHandler
    |
    | authenticate token
    v
MatchingService.connect()
    |
    | store session
    | restore queue/match/call state
    v
Realtime events
```

## Client Events

| Event | Purpose |
|---|---|
| `ping` | Keepalive/check latency. |
| `updateProfile` | Save profile over realtime channel. |
| `joinQueue` | Enter matching queue. |
| `leaveQueue` | Leave matching queue. |
| `acceptMatch` | Accept pending match. |
| `rejectMatch` | Reject pending match. |
| `signal` | WebRTC signaling payload. |
| `chatMessage` | In-call chat. |
| `continueCall` | Continue call beyond normal timer. |
| `endCall` | End current call. |

## Server Events

| Event | Purpose |
|---|---|
| `connected` | WebSocket authenticated and ready. |
| `pong` | Response to ping. |
| `profile-updated` | Profile saved. |
| `queue-status` | Queue state changed. |
| `match-offer` | Candidate found. |
| `match-accepted` | Backend recorded accept. |
| `match-cancelled` | Match rejected/expired/unavailable. |
| `call-started` | Enter room and start WebRTC. |
| `signal` | Forwarded WebRTC offer/answer/ICE. |
| `chat-message` | In-call chat message. |
| `call-ended` | Room ended. |
| `call-continued` | Call extended. |
| `call-peer-reconnecting` | Peer disconnected temporarily. |
| `session-replaced` | Same user opened another active tab. |
| `auth-required` | WebSocket auth failed. |
| `error` | Realtime action failed. |

## Connection Lifecycle

```text
1. Frontend gets JWT after login.
2. Frontend opens WebSocket /ws?token=JWT.
3. Backend verifies JWT.
4. Backend stores userId -> WebSocketSession.
5. Backend sends connected.
6. Backend restores existing call/match/queue state.
7. User sends events.
8. Backend sends events back.
9. If connection closes, backend removes session and may start reconnect grace.
```

## Reconnection Handling

Frontend:

- Queues important messages when socket is reconnecting.
- Retries accept while pending.
- Uses BroadcastChannel to avoid multiple active realtime tabs.

Backend:

- `restoreRealtimeState()` sends current room/match/queue state when user reconnects.
- If user disconnects during a call, `scheduleCallReconnectGrace()` gives them time to return.

## Presence Tracking

Backend in-memory maps:

- `sessions`: user id -> WebSocket session.
- `sessionToUserId`: session id -> user id.
- `userConnectedAt`: timestamp.
- `userQueuedAt`: timestamp.

Redis:

- Queue and pending matches are shared.

## Event Diagram: Accept To Call

```text
User A Browser        Backend             User B Browser
     |                  |                       |
     | acceptMatch      |                       |
     |----------------->|                       |
     |                  | save accepted [A]     |
     |                  |---------------------->|
     |                  | match-accepted        |
     |<-----------------|                       |
     |                  |                       |
     |                  |       acceptMatch     |
     |                  |<----------------------|
     |                  | save accepted [A,B]   |
     |                  | create room           |
     | call-started     |                       |
     |<-----------------|---------------------->|
     |                  |       call-started    |
```

---

# Level 8: Video Calling Deep Dive

## Beginner Explanation

WebRTC lets two browsers call each other directly.

But browsers first need help finding each other. The SpeedLink backend is like a matchmaker and message carrier:

- It introduces the two browsers.
- It passes setup messages.
- It does not carry the actual video if direct peer-to-peer works.

## Advanced WebRTC Concepts

### Offer

The offer is a message created by one browser saying:

> "Here is what audio/video formats I support. Here is how I might be reachable."

In SpeedLink:

- Backend chooses an initiator user id.
- Frontend checks if current user is initiator.
- Initiator creates `peerConnection.createOffer()`.
- Initiator sends it through WebSocket as `signal`.

### Answer

The answer is the other browser's reply:

> "I accept these media settings, and here is how I might be reachable."

In SpeedLink:

- Peer receives offer through WebSocket.
- Peer calls `setRemoteDescription(offer)`.
- Peer creates `createAnswer()`.
- Peer sends answer through WebSocket.
- Initiator receives answer and calls `setRemoteDescription(answer)`.

### ICE Candidates

ICE candidates are possible network paths.

Examples:

- Local Wi-Fi address.
- Public address discovered by STUN.
- Relay address through TURN.

In SpeedLink:

- `peerConnection.onicecandidate` fires.
- Frontend sends candidate through WebSocket.
- Other browser calls `addIceCandidate()`.

### STUN

STUN helps a browser discover its public-facing network address.

SpeedLink uses:

```text
stun:stun.l.google.com:19302
```

### TURN

TURN is a relay server used when direct peer-to-peer fails.

Current SpeedLink code uses STUN but not a dedicated TURN server. For production-quality calls like Zoom/Meet, a TURN service should be added.

Why TURN matters:

- Some networks block peer-to-peer.
- Corporate Wi-Fi can be restrictive.
- Mobile networks can be difficult.
- TURN relays media when direct connection fails.

Recommended future service:

- coturn self-hosted.
- Twilio Network Traversal.
- Metered.ca TURN.
- Cloudflare Calls or similar.

## WebRTC Flow Diagram

```text
User A Browser                         User B Browser
     |                                       |
     | getUserMedia()                        | getUserMedia()
     | create RTCPeerConnection             | create RTCPeerConnection
     | createOffer()                         |
     | setLocalDescription(offer)            |
     |                                       |
     | ---- offer via SpeedLink WS --------> |
     |                                       | setRemoteDescription(offer)
     |                                       | createAnswer()
     |                                       | setLocalDescription(answer)
     | <--- answer via SpeedLink WS -------- |
     | setRemoteDescription(answer)          |
     |                                       |
     | ---- ICE via SpeedLink WS ----------> |
     | <--- ICE via SpeedLink WS ----------- |
     |                                       |
     | ===== peer-to-peer media stream ===== |
```

## Signaling Server

The signaling server is not a special separate server here. It is the SpeedLink backend WebSocket.

It forwards:

```json
{
  "type": "signal",
  "roomId": "room-123",
  "payload": {
    "kind": "offer",
    "sdp": "..."
  }
}
```

or:

```json
{
  "kind": "ice",
  "candidate": { "...": "..." }
}
```

The backend checks:

- Does the room exist?
- Is the sender a participant?

Then it forwards the payload to the other participant.

---

# Level 9: Source Code Walkthrough

This section focuses on major files and what they do. A literal line-by-line explanation of every line would be longer than the codebase, so this explains the important blocks, why they exist, and what would break if removed.

## Backend

### `SpeedLinkApplication.java`

Purpose:

- Starts Spring Boot.

Why it exists:

- It is the entrypoint that runs the backend application context.

If removed:

- Backend cannot start normally.

### `SecurityConfig.java`

Purpose:

- Defines HTTP security and CORS.

Important imports:

- `HttpSecurity`
- `SecurityFilterChain`
- `CorsConfiguration`

Important logic:

- Disables CSRF because this is token/API based rather than server-rendered form sessions.
- Allows auth endpoints publicly.
- Allows WebSocket endpoint publicly at HTTP layer because actual auth happens inside WebSocket connection.
- Configures allowed origins and headers.

If removed:

- Browser requests may fail CORS.
- Security defaults may block routes.

### `WebSocketConfig.java`

Purpose:

- Registers `/ws`.

If removed:

- Realtime queue, matching, chat, and WebRTC signaling stop working.

### `SpeedLinkWebSocketHandler.java`

Purpose:

- Handles low-level WebSocket lifecycle.

Key methods:

- `afterConnectionEstablished`: extracts token, authenticates, calls `matchingService.connect`.
- `handleTextMessage`: parses JSON into `ClientMessage`, delegates to `MatchingService`.
- `afterConnectionClosed`: notifies service.
- `tokenFromUri`: reads `?token=` query param.

If removed:

- Browser cannot communicate realtime with backend.

### `AuthController.java`

Purpose:

- Exposes auth/profile endpoints.

Important methods:

- `supabase`
- `supabaseConfig`
- `verificationLink`
- `emailSession`
- `signup`
- `passwordReset`
- `me`
- `updateProfile`

If removed:

- Users cannot sign up, login through backend, or manage profiles.

### `AuthService.java`

Purpose:

- Implements auth business rules.

Important functions:

- `exchangeSupabaseToken`: validates Supabase token and creates SpeedLink response.
- `signup`: creates a user after Supabase verification.
- `authenticate`: verifies SpeedLink JWT.
- `updateProfile`: saves profile.
- `resolveSupabaseAccount`: finds/creates account from Supabase identity.

Why it exists:

- Keeps controller thin and centralizes auth rules.

If removed:

- Auth logic would be duplicated or missing.

### `AppTokenService.java`

Purpose:

- Issues and verifies SpeedLink JWTs.

Important functions:

- `issueToken(userId)`
- `verifySubject(rawToken)`

If removed:

- Backend would not have its own app session token.
- WebSocket authentication would need a different approach.

### `SupabaseAuthClient.java`

Purpose:

- Calls Supabase Auth REST API.

Important functions:

- `fetchUser`
- `requireVerifiedEmail`
- `emailExists`
- `whatsappPhoneExists`

If removed:

- Backend could not verify Supabase users.

### `MatchingService.java`

Purpose:

- Core realtime engine.

Important state:

- `sessions`: connected users.
- `profiles`: profile cache.
- `activeRooms`: room participants.
- `userToRoom`: user room lookup.
- `callSessions`: active call metadata.
- `matchingModes`: basic/advanced.
- `localQueue`: fallback queue.
- Redis keys for shared queue/matches/locks.

Important methods:

- `connect`: stores WebSocket session and restores state.
- `disconnectSession`: handles closed socket.
- `restoreRealtimeState`: resumes room/match/queue after reconnect.
- `handle`: routes client WebSocket events.
- `joinQueue`: validates and queues user.
- `acceptMatch`: records accept safely with lock.
- `startCallIfReady`: creates room after both accept.
- `rejectMatch`: cancels match and requeues users.
- `expireMatch`: handles 30-second accept timeout.
- `forwardSignal`: forwards WebRTC signals.
- `forwardChatMessage`: forwards in-call chat.
- `endCall`: ends room and stores analytics.
- `tryMatchQueuedUsers`: scans queue and creates matches.
- `compatibilityScore`: scores users.

If removed:

- The main product stops working.

### `MatchingWindowService.java`

Purpose:

- Controls when matching is open.

Important methods:

- `current`
- `isOpenNow`
- `update`

If removed:

- Queue would not have schedule control.

### Entity Files

`UserAccount.java`

- User and profile table.

`ConversationSession.java`

- Call analytics table.

`UserSuggestion.java`

- Feedback table.

`AppSetting.java`

- Admin settings table.

### Repository Files

Purpose:

- Spring Data JPA interfaces.
- Provide methods like `findByEmail`, `findTop50ByOrderByStartedAtDesc`.

If removed:

- Services cannot query database easily.

## Frontend

### `main.jsx`

Purpose:

- Renders React app into the DOM.

Typical logic:

```jsx
createRoot(document.getElementById("root")).render(<App />);
```

If removed:

- Browser loads HTML but React app never starts.

### `App.jsx`

Purpose:

- Main single-file frontend application.

Important sections:

- Imports React hooks and icons.
- Defines route constants and URL config.
- Defines default profile and utility helpers.
- Defines `App` component.
- Defines auth functions.
- Defines API request helper.
- Defines WebSocket send/setup logic.
- Defines WebRTC setup logic.
- Defines page components.

Important hooks:

- `useState`: app state.
- `useRef`: socket, peer connection, streams, current call.
- `useEffect`: lifecycle tasks.
- `useCallback`: stable callbacks.
- `useMemo`: derived values.

Important event handlers:

- `joinQueue`
- `leaveQueue`
- `acceptMatch`
- `rejectMatch`
- `endCurrentCall`
- `continueCurrentCall`
- `sendChatMessage`
- `saveProfile`
- auth handlers

If removed:

- Entire frontend app disappears.

### `styles.css`

Purpose:

- All visual styling.

If removed:

- App still renders but looks unstyled.

### `assets/`

Purpose:

- Branding and visual images for landing and feature sections.

If removed:

- Images break or page looks empty.

## How To Write It From Memory

The core rebuild memory model:

1. Build auth first.
2. Create user profile table.
3. Issue app JWT.
4. Open authenticated WebSocket.
5. Store sessions.
6. Implement queue.
7. Implement pair scoring.
8. Send match offer.
9. Track accept state.
10. Create room after both accept.
11. Forward WebRTC signaling.
12. Record conversation session.

---

# Level 10: Rebuilding SpeedLink From Scratch

## Exact Build Order

### Step 1: Create Repository

```text
speedlink/
    frontend/
    backend/
    docker-compose.yml
```

### Step 2: Backend Setup

Create Spring Boot project with:

- Java 17.
- Spring Web.
- Spring WebSocket.
- Spring Data JPA.
- PostgreSQL driver.
- Redis.
- Validation.
- Security.
- java-jwt.

Create:

```text
SpeedLinkApplication.java
application.properties
application-local.properties
application-prod.properties
```

### Step 3: Database Setup

Create entities:

1. `UserAccount`
2. `ConversationSession`
3. `UserSuggestion`
4. `AppSetting`

Create repositories.

Use local `ddl-auto=update` while building. Use prod `validate` when schema is stable.

### Step 4: Authentication Setup

1. Create Supabase project.
2. Enable email/password auth.
3. Add frontend env:
   - `VITE_SUPABASE_URL`
   - `VITE_SUPABASE_PUBLISHABLE_KEY`
4. Add backend env:
   - `SUPABASE_URL`
   - `SUPABASE_PUBLISHABLE_KEY`
   - `SUPABASE_SERVICE_ROLE_KEY`
   - `SPEEDLINK_JWT_SECRET`
5. Implement `SupabaseAuthClient`.
6. Implement `AppTokenService`.
7. Implement `AuthService`.
8. Implement `AuthController`.

### Step 5: Frontend Setup

Create Vite React app.

Install:

```text
react
react-dom
vite
@vitejs/plugin-react
@supabase/supabase-js
lucide-react
```

Build pages:

1. Landing page.
2. Signup/signin/reset page.
3. Dashboard.
4. Profile page.
5. Match dialog.
6. Call screen.
7. Admin page.

### Step 6: REST API Integration

Implement frontend helper:

```text
apiRequest(path, options)
```

It should:

- Prefix API URL.
- Add JSON headers.
- Add Bearer JWT.
- Parse response.
- Throw readable errors.

### Step 7: WebSocket Setup

Backend:

- Create `ClientMessage`.
- Create `ServerMessage`.
- Create `SpeedLinkWebSocketHandler`.
- Register `/ws`.

Frontend:

- Open `/ws?token=<jwt>`.
- Parse server messages.
- Send client messages.
- Reconnect on close.

### Step 8: Matching System

Implement:

- Queue.
- Profile cache.
- Matching mode.
- Compatibility score.
- Pending match.
- Accept timeout.
- Reject cooldown.
- Redis lock for matching.
- Redis lock for match accept mutation.

### Step 9: Video Calling

Frontend:

1. Ask for camera/mic using `getUserMedia`.
2. Create `RTCPeerConnection`.
3. Add local tracks.
4. If initiator, create offer.
5. Send offer over WebSocket.
6. Peer creates answer.
7. Exchange ICE candidates.
8. Attach remote stream to video.

Backend:

- Forward `signal` messages only between room participants.

### Step 10: Call Lifecycle

Implement:

- `call-started`.
- `call-ended`.
- Call timer.
- Continue call.
- Peer reconnect grace.
- Save conversation start/end.

### Step 11: Admin

Implement:

- Matching window setting.
- Queue clear when window closes.
- Dashboard stats.
- Recent conversations.
- Suggestions.

### Step 12: Deployment

Production env:

```text
SPRING_PROFILES_ACTIVE=prod
DB_HOST=...
DB_NAME=...
DB_USERNAME=...
DB_PASSWORD=...
REDIS_URL=...
SPEEDLINK_JWT_SECRET=...
SUPABASE_URL=...
SUPABASE_PUBLISHABLE_KEY=...
SUPABASE_SERVICE_ROLE_KEY=...
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPEEDLINK_CORS_ALLOWED_ORIGINS=https://www.speedlink.in,https://speedlink.in
SPEEDLINK_CORS_ALLOWED_ORIGIN_PATTERNS=https://*.speedlink.in
```

Frontend env:

```text
VITE_API_URL=https://api.speedlink.in/api
VITE_WS_URL=wss://api.speedlink.in/ws
VITE_SUPABASE_URL=...
VITE_SUPABASE_PUBLISHABLE_KEY=...
```

### Step 13: Production Hardening

Before serious scale:

- Add database migrations with Flyway or Liquibase.
- Add TURN server for reliable WebRTC.
- Add backend tests for matching and accept race.
- Add frontend integration tests.
- Add rate limiting.
- Add structured logging.
- Add monitoring and alerts.
- Add admin authentication stronger than a static key.
- Add privacy/data deletion flows.
- Add indexes for dashboard query patterns.

---

# Interview-Ready Summary

SpeedLink is a full-stack real-time networking platform. The frontend is React/Vite, the backend is Spring Boot, PostgreSQL stores durable user/session data, Redis stores volatile matching state, Supabase handles identity, SpeedLink JWTs authorize app/API/WebSocket usage, WebSockets power real-time queue/matching/signaling/chat, and WebRTC powers browser-to-browser video calls.

The hardest parts technically are:

- Correct real-time state management.
- Preventing race conditions when matching/accepting.
- Reconnecting users without losing state.
- Coordinating WebRTC signaling.
- Separating durable database state from temporary Redis state.
- Production configuration and schema safety.

If an interviewer asks why this architecture is reasonable:

- REST is used for normal CRUD/auth.
- WebSocket is used for live bidirectional events.
- PostgreSQL is used for permanent relational data.
- Redis is used for fast shared realtime state.
- Supabase avoids storing passwords.
- WebRTC avoids routing video through the backend.
- Locks prevent race conditions in multi-user/multi-instance matching.

Child explanation:

> SpeedLink is like a smart teacher at a big classroom event. The teacher knows who everyone is, keeps a waiting line, picks two people who should talk, asks both if they agree, opens a private video table for them, and writes down that the meeting happened.
