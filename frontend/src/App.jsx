import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowRight,
  Check,
  ChevronDown,
  Clock,
  LockKeyhole,
  LogIn,
  LogOut,
  Mail,
  PhoneOff,
  Save,
  Search,
  Send,
  Settings,
  ShieldCheck,
  Upload,
  UserPlus,
  UserRound,
  Users,
  Video,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import headImage from "./assets/speedlink-pro/headimg1.png";
import registrationImage from "./assets/speedlink-pro/registration1.png";
import validationImage from "./assets/speedlink-pro/validation1.png";
import videoCallImage from "./assets/speedlink-pro/vc1.png";

const defaultApiProtocol =
  window.location.protocol === "https:" ? "https" : "http";
const defaultWsProtocol = window.location.protocol === "https:" ? "wss" : "ws";
const API_URL =
  import.meta.env.VITE_API_URL ||
  `${defaultApiProtocol}://${window.location.hostname}:8080/api`;
const WS_BASE_URL =
  import.meta.env.VITE_WS_URL ||
  `${defaultWsProtocol}://${window.location.hostname}:8080/ws`;
const TOKEN_KEY = "speedlink_token";
const APP_TITLE = "SpeedLink";

const routes = {
  landing: "/",
  signup: "/signup",
  signin: "/signin",
  app: "/app",
};

const roles = [
  "Developer",
  "Designer",
  "Co-founder",
  "Freelancer",
  "Product Manager",
  "Founder",
  "Marketer",
  "Data Scientist",
  "Other / Random",
];

const companyTypes = ["MNC", "Startup", "Freelancer", "Student"];
const ageRanges = ["18-24", "25-34", "35-44", "45+"];
const interestOptions = [
  "Build projects",
  "Career advice",
  "Friend",
  "Hiring",
  "Investors",
  "Random Friendly Conversation",
  "Explore New People",
];

const defaultProfile = {
  userId: "",
  displayName: "",
  role: "Developer",
  lookingFor: "Designer",
  expertise: "React, Java, product MVPs",
  goals: "Find a collaborator for a focused build sprint",
  intent: "MVP build partner",
  bio: "",
  interests: "Build projects",
  companyType: "Startup",
  ageRange: "25-34",
  profilePhoto: "",
};

const defaultAuthForm = {
  email: "",
  password: "",
  displayName: "",
  role: "Developer",
  lookingFor: "Designer",
  expertise: "React, Java, product MVPs",
  goals: "Find a collaborator for a focused build sprint",
  intent: "MVP build partner",
  bio: "",
  interests: "Build projects",
  companyType: "Startup",
  ageRange: "25-34",
  profilePhoto: "",
};

function routeFromLocation() {
  const path = window.location.pathname.replace(/\/+$/, "") || "/";
  if (path === routes.signup) {
    return "signup";
  }
  if (path === routes.signin || path === "/login") {
    return "signin";
  }
  if (path === routes.app) {
    return "app";
  }
  return "landing";
}

function profilePayload(profile) {
  return {
    displayName: profile.displayName || "",
    role: profile.role || "",
    lookingFor: profile.lookingFor || "",
    expertise: profile.expertise || "",
    goals: profile.goals || "",
    intent: profile.intent || "",
    bio: profile.bio || "",
    interests: profile.interests || "",
    companyType: profile.companyType || "",
    ageRange: profile.ageRange || "",
    profilePhoto: profile.profilePhoto || "",
  };
}

function normalizeProfile(profile) {
  const merged = { ...defaultProfile, ...(profile || {}) };
  return Object.fromEntries(
    Object.entries(merged).map(([key, value]) => [
      key,
      value == null ? defaultProfile[key] || "" : value,
    ]),
  );
}

function splitProfessionValue(value) {
  return String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function joinProfessionValue(values) {
  return values.join(", ");
}

function formatDuration(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

function App() {
  const socketRef = useRef(null);
  const handlerRef = useRef(() => {});
  const pcRef = useRef(null);
  const localStreamRef = useRef(null);
  const remoteStreamRef = useRef(null);
  const pendingSignalsRef = useRef([]);
  const localVideoRef = useRef(null);
  const remoteVideoRef = useRef(null);
  const userIdRef = useRef("");
  const callRef = useRef(null);

  const [route, setRoute] = useState(routeFromLocation);
  const [authForm, setAuthForm] = useState(defaultAuthForm);
  const [authError, setAuthError] = useState("");
  const [authBusy, setAuthBusy] = useState(false);

  useEffect(() => {
    document.title = APP_TITLE;
  }, [route]);
  const [authChecked, setAuthChecked] = useState(false);
  const [token, setToken] = useState(
    () => localStorage.getItem(TOKEN_KEY) || "",
  );
  const [connected, setConnected] = useState(false);
  const [userId, setUserId] = useState("");
  const [profile, setProfile] = useState(defaultProfile);
  const [profileBusy, setProfileBusy] = useState(false);
  const [profileError, setProfileError] = useState("");
  const [profileSavedAt, setProfileSavedAt] = useState("");
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [platformStats, setPlatformStats] = useState(null);
  const [backendReady, setBackendReady] = useState(false);
  const [queueStatus, setQueueStatus] = useState({
    inQueue: false,
    queueSize: 0,
    message: "Offline",
  });
  const [match, setMatch] = useState(null);
  const [accepted, setAccepted] = useState(false);
  const [call, setCall] = useState(null);
  const [chatMessages, setChatMessages] = useState([]);
  const [chatDraft, setChatDraft] = useState("");
  const [events, setEvents] = useState([]);
  const [now, setNow] = useState(Date.now());

  const navigate = useCallback((nextRoute, options = {}) => {
    const path = routes[nextRoute] || routes.landing;
    if (window.location.pathname !== path) {
      const method = options.replace ? "replaceState" : "pushState";
      window.history[method]({}, "", path);
    }
    setRoute(nextRoute);
    window.scrollTo({ top: 0 });
  }, []);

  useEffect(() => {
    const handlePopState = () => setRoute(routeFromLocation());
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  useEffect(() => {
    userIdRef.current = userId;
  }, [userId]);

  useEffect(() => {
    callRef.current = call;
  }, [call]);

  const addEvent = useCallback((text) => {
    setEvents((current) =>
      [
        {
          id: `${Date.now()}-${Math.random()}`,
          time: new Date().toLocaleTimeString([], {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
          }),
          text,
        },
        ...current,
      ].slice(0, 8),
    );
  }, []);

  const resetLiveState = useCallback(() => {
    setConnected(false);
    setUserId("");
    setMatch(null);
    setAccepted(false);
    setCall(null);
    setEvents([]);
    setProfileBusy(false);
    setProfileError("");
    setProfileSavedAt("");
    setQueueStatus({ inQueue: false, queueSize: 0, message: "Offline" });
  }, []);

  const apiRequest = useCallback(async (path, options = {}) => {
    const response = await fetch(`${API_URL}${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
        ...(options.headers || {}),
      },
    });

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      throw new Error(data?.message || "Request failed");
    }
    return data;
  }, []);

  const cleanupCall = useCallback(() => {
    if (pcRef.current) {
      pcRef.current.close();
      pcRef.current = null;
    }

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }

    remoteStreamRef.current = null;
    pendingSignalsRef.current = [];

    if (localVideoRef.current) {
      localVideoRef.current.srcObject = null;
    }
    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = null;
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setToken("");
    setProfile(defaultProfile);
    cleanupCall();
    resetLiveState();
    if (socketRef.current) {
      socketRef.current.close();
      socketRef.current = null;
    }
    navigate("signin", { replace: true });
  }, [cleanupCall, navigate, resetLiveState]);

  useEffect(() => {
    let cancelled = false;

    async function loadSession() {
      if (!token) {
        setAuthChecked(true);
        return;
      }

      try {
        const result = await apiRequest("/auth/me", { token });
        if (cancelled) {
          return;
        }
        const loadedProfile = normalizeProfile(result.profile);
        setProfile(loadedProfile);
        setUserId(loadedProfile.userId);
      } catch (error) {
        if (!cancelled) {
          logout();
        }
      } finally {
        if (!cancelled) {
          setAuthChecked(true);
        }
      }
    }

    loadSession();
    return () => {
      cancelled = true;
    };
  }, [apiRequest, logout, token]);

  useEffect(() => {
    if (!authChecked) {
      return;
    }
    if (!token && route === "app") {
      navigate("signin", { replace: true });
    }
    if (token && (route === "signin" || route === "signup")) {
      navigate("app", { replace: true });
    }
  }, [authChecked, navigate, route, token]);

  useEffect(() => {
    let cancelled = false;

    async function loadPublicStats() {
      if (route !== "landing") {
        return;
      }

      try {
        const [health, stats] = await Promise.all([
          apiRequest("/health"),
          apiRequest("/stats"),
        ]);
        if (!cancelled) {
          setBackendReady(health?.status === "ok");
          setPlatformStats(stats);
        }
      } catch (error) {
        if (!cancelled) {
          setBackendReady(false);
          setPlatformStats(null);
        }
      }
    }

    loadPublicStats();
    return () => {
      cancelled = true;
    };
  }, [apiRequest, route]);

  const sendMessage = useCallback(
    (message) => {
      const socket = socketRef.current;
      if (!socket || socket.readyState !== WebSocket.OPEN) {
        addEvent("Connection is not ready");
        return false;
      }
      socket.send(JSON.stringify(message));
      return true;
    },
    [addEvent],
  );

  const attachStreamsToVideo = useCallback(() => {
    if (localVideoRef.current && localStreamRef.current) {
      localVideoRef.current.srcObject = localStreamRef.current;
    }
    if (remoteVideoRef.current && remoteStreamRef.current) {
      remoteVideoRef.current.srcObject = remoteStreamRef.current;
    }
  }, []);

  const applySignal = useCallback(
    async (signal) => {
      const peerConnection = pcRef.current;
      const activeCall = callRef.current;
      if (!peerConnection || !activeCall || !signal) {
        return;
      }

      if (signal.kind === "offer") {
        await peerConnection.setRemoteDescription({
          type: "offer",
          sdp: signal.sdp,
        });
        const answer = await peerConnection.createAnswer();
        await peerConnection.setLocalDescription(answer);
        sendMessage({
          type: "signal",
          roomId: activeCall.roomId,
          payload: { kind: "answer", sdp: answer.sdp },
        });
        return;
      }

      if (signal.kind === "answer") {
        await peerConnection.setRemoteDescription({
          type: "answer",
          sdp: signal.sdp,
        });
        return;
      }

      if (signal.kind === "ice" && signal.candidate) {
        await peerConnection.addIceCandidate(signal.candidate);
      }
    },
    [sendMessage],
  );

  const handleSignalEnvelope = useCallback(
    async (envelope) => {
      const signal = envelope?.payload;
      if (!signal) {
        return;
      }

      if (!pcRef.current) {
        pendingSignalsRef.current.push(signal);
        return;
      }

      try {
        await applySignal(signal);
      } catch (error) {
        addEvent("Video signaling needs a retry");
      }
    },
    [addEvent, applySignal],
  );

  const setupPeerConnection = useCallback(
    async (callPayload) => {
      if (pcRef.current) {
        return;
      }

      try {
        const localStream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: true,
        });
        localStreamRef.current = localStream;

        const peerConnection = new RTCPeerConnection({
          iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
        });
        pcRef.current = peerConnection;

        localStream
          .getTracks()
          .forEach((track) => peerConnection.addTrack(track, localStream));

        peerConnection.ontrack = (event) => {
          remoteStreamRef.current = event.streams[0];
          attachStreamsToVideo();
        };

        peerConnection.onicecandidate = (event) => {
          if (event.candidate) {
            sendMessage({
              type: "signal",
              roomId: callPayload.roomId,
              payload: { kind: "ice", candidate: event.candidate },
            });
          }
        };

        peerConnection.onconnectionstatechange = () => {
          if (peerConnection.connectionState === "connected") {
            addEvent("Video session connected");
          }
        };

        attachStreamsToVideo();

        const queuedSignals = [...pendingSignalsRef.current];
        pendingSignalsRef.current = [];
        for (const signal of queuedSignals) {
          await applySignal(signal);
        }

        if (callPayload.initiatorUserId === userIdRef.current) {
          const offer = await peerConnection.createOffer();
          await peerConnection.setLocalDescription(offer);
          sendMessage({
            type: "signal",
            roomId: callPayload.roomId,
            payload: { kind: "offer", sdp: offer.sdp },
          });
        }
      } catch (error) {
        addEvent("Camera or microphone permission is needed for the call");
      }
    },
    [addEvent, applySignal, attachStreamsToVideo, sendMessage],
  );

  const handleServerMessage = useCallback(
    (message) => {
      const payload = message.payload;

      if (message.type === "connected") {
        setConnected(true);
        setUserId(payload.userId);
        setProfile((current) =>
          normalizeProfile({ ...current, ...payload.profile, userId: payload.userId }),
        );
        setQueueStatus({ inQueue: false, queueSize: 0, message: "Ready" });
        addEvent("Connected");
        return;
      }

      if (message.type === "profile-updated") {
        setProfile((current) => normalizeProfile({ ...current, ...payload }));
        setProfileSavedAt("Saved");
        return;
      }

      if (message.type === "queue-status") {
        setQueueStatus(payload);
        return;
      }

      if (message.type === "match-offer") {
        setMatch(payload);
        setAccepted(false);
        setQueueStatus((current) => ({
          ...current,
          inQueue: false,
          message: "Match found",
        }));
        addEvent(`Matched with ${payload.candidate.displayName}`);
        return;
      }

      if (message.type === "match-accepted") {
        setAccepted(true);
        addEvent("Acceptance sent");
        return;
      }

      if (message.type === "match-cancelled") {
        setMatch(null);
        setAccepted(false);
        addEvent(payload.reason);
        return;
      }

      if (message.type === "call-started") {
        setMatch(null);
        setAccepted(false);
        setChatMessages([]);
        setChatDraft("");
        setCall({ ...payload, continueUntilDisconnected: false });
        addEvent(`Call started with ${payload.peer.displayName}`);
        return;
      }

      if (message.type === "call-continued") {
        setCall((current) =>
          current && current.roomId === payload.roomId
            ? { ...current, continueUntilDisconnected: true }
            : current,
        );
        addEvent("Call will continue until someone disconnects");
        return;
      }

      if (message.type === "signal") {
        handleSignalEnvelope(payload);
        return;
      }

      if (message.type === "chat-message") {
        setChatMessages((current) =>
          [
            ...current,
            {
              id: `${payload.sentAtEpochMillis}-${payload.senderUserId}-${current.length}`,
              mine: payload.senderUserId === userIdRef.current,
              senderUserId: payload.senderUserId,
              sentAtEpochMillis: payload.sentAtEpochMillis,
              text: payload.text,
            },
          ].slice(-80),
        );
        return;
      }

      if (message.type === "call-ended") {
        cleanupCall();
        setCall(null);
        setChatMessages([]);
        setChatDraft("");
        addEvent(payload.reason || "Call ended");
        return;
      }

      if (message.type === "auth-required") {
        setAuthError(payload.message || "Login is required.");
        logout();
        return;
      }

      if (message.type === "error") {
        setProfileError(payload.message || "Something went wrong");
        addEvent(payload.message || "Something went wrong");
      }
    },
    [addEvent, cleanupCall, handleSignalEnvelope, logout],
  );

  useEffect(() => {
    handlerRef.current = handleServerMessage;
  }, [handleServerMessage]);

  useEffect(() => {
    if (!token || !authChecked) {
      return undefined;
    }

    let cancelled = false;
    let reconnectTimer = null;
    let heartbeatTimer = null;
    let reconnectAttempt = 0;

    const stopHeartbeat = () => {
      if (heartbeatTimer) {
        window.clearInterval(heartbeatTimer);
        heartbeatTimer = null;
      }
    };

    const startHeartbeat = (socket) => {
      stopHeartbeat();
      heartbeatTimer = window.setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: "ping" }));
        }
      }, 20000);
    };

    const scheduleReconnect = () => {
      if (cancelled) {
        return;
      }

      reconnectAttempt += 1;
      const delay = Math.min(1000 * reconnectAttempt, 5000);
      reconnectTimer = window.setTimeout(connectSocket, delay);
    };

    function connectSocket() {
      if (cancelled) {
        return;
      }

      const socket = new WebSocket(
        `${WS_BASE_URL}?token=${encodeURIComponent(token)}`,
      );
      socketRef.current = socket;

      socket.onopen = () => {
        reconnectAttempt = 0;
        setConnected(true);
        startHeartbeat(socket);
        setQueueStatus((current) => ({
          ...current,
          message: current.inQueue ? current.message : "Ready",
        }));
      };
      socket.onmessage = (event) => {
        const message = JSON.parse(event.data);
        handlerRef.current(message);
      };
      socket.onclose = () => {
        stopHeartbeat();
        if (socketRef.current === socket) {
          socketRef.current = null;
        }
        setConnected(false);
        setQueueStatus((current) => ({
          ...current,
          inQueue: false,
          message: "Reconnecting",
        }));
        scheduleReconnect();
      };
      socket.onerror = () => addEvent("WebSocket connection failed");
    }

    connectSocket();

    return () => {
      cancelled = true;
      if (reconnectTimer) {
        window.clearTimeout(reconnectTimer);
      }
      stopHeartbeat();
      if (socketRef.current) {
        socketRef.current.close();
        socketRef.current = null;
      }
      socketRef.current = null;
      cleanupCall();
    };
  }, [addEvent, authChecked, cleanupCall, token]);

  useEffect(() => {
    if (!match && !call) {
      return undefined;
    }

    const timer = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(timer);
  }, [call, match]);

  useEffect(() => {
    if (call) {
      setupPeerConnection(call);
    }
  }, [call, setupPeerConnection]);

  useEffect(() => {
    attachStreamsToVideo();
  }, [call, attachStreamsToVideo]);

  const secondsLeft = useMemo(() => {
    if (!match) {
      return 0;
    }
    return Math.max(0, Math.ceil((match.expiresAtEpochMillis - now) / 1000));
  }, [match, now]);

  const callSecondsLeft = useMemo(() => {
    if (!call?.endsAtEpochMillis || call.continueUntilDisconnected) {
      return 0;
    }

    return Math.max(0, Math.ceil((call.endsAtEpochMillis - now) / 1000));
  }, [call, now]);

  const shouldShowContinuePrompt =
    Boolean(call) && !call.continueUntilDisconnected && callSecondsLeft <= 60;

  const canJoinQueue =
    connected &&
    profile.displayName.trim() &&
    profile.role &&
    profile.lookingFor;

  const updateProfileField = (field, value) => {
    setProfileError("");
    setProfileSavedAt("");
    setProfile((current) => ({ ...current, [field]: value }));
  };

  const handleProfilePhotoUpload = (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    const reader = new FileReader();
    reader.onload = () =>
      updateProfileField("profilePhoto", String(reader.result || ""));
    reader.readAsDataURL(file);
  };

  const updateAuthField = (field, value) => {
    setAuthError("");
    setAuthForm((current) => ({ ...current, [field]: value }));
  };

  const submitAuth = async (event, mode) => {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError("");

    try {
      const path = mode === "signup" ? "/auth/signup" : "/auth/login";
      const body =
        mode === "signup"
          ? authForm
          : { email: authForm.email, password: authForm.password };
      const result = await apiRequest(path, {
        method: "POST",
        body: JSON.stringify(body),
      });

      if (!result?.token) {
        throw new Error("Login response did not include a token.");
      }

      const profileData = normalizeProfile(result.profile);
      localStorage.setItem(TOKEN_KEY, result.token);
      setToken(result.token);
      setProfile(profileData);
      setUserId(profileData.userId || "");
      setAuthForm((current) => ({ ...current, password: "" }));
      setAuthChecked(true);
      navigate("app", { replace: true });
    } catch (error) {
      setAuthError(error.message);
    } finally {
      setAuthBusy(false);
    }
  };

  const persistProfile = async () => {
    setProfileBusy(true);
    setProfileError("");

    try {
      const result = await apiRequest("/auth/profile", {
        method: "PUT",
        token,
        body: JSON.stringify(profilePayload(profile)),
      });
      const savedProfile = normalizeProfile(result.profile);
      setProfile(savedProfile);
      setProfileSavedAt("Saved");
      return savedProfile;
    } catch (error) {
      setProfileError(error.message);
      throw error;
    } finally {
      setProfileBusy(false);
    }
  };

  const saveProfile = async (event) => {
    event.preventDefault();
    try {
      await persistProfile();
      addEvent("Profile saved to your account");
    } catch (error) {
      addEvent(error.message);
    }
  };

  const joinQueue = async (event) => {
    event.preventDefault();
    if (profileBusy) {
      return;
    }
    if (!connected) {
      addEvent("Realtime connection is reconnecting");
      return;
    }
    if (!canJoinQueue) {
      return;
    }

    try {
      const savedProfile = await persistProfile();
      sendMessage({ type: "joinQueue", profile: savedProfile });
    } catch (error) {
      addEvent(error.message);
    }
  };

  const leaveQueue = () => {
    sendMessage({ type: "leaveQueue" });
    setQueueStatus((current) => ({
      ...current,
      inQueue: false,
      message: "Leaving queue",
    }));
  };

  const acceptMatch = () => {
    if (!match) {
      return;
    }
    setAccepted(true);
    sendMessage({ type: "acceptMatch", matchId: match.matchId });
  };

  const rejectMatch = () => {
    if (!match) {
      return;
    }
    sendMessage({ type: "rejectMatch", matchId: match.matchId });
    setMatch(null);
    setAccepted(false);
  };

  const endCurrentCall = () => {
    if (callRef.current) {
      sendMessage({ type: "endCall", roomId: callRef.current.roomId });
    }
    cleanupCall();
    setCall(null);
  };

  const continueCurrentCall = () => {
    if (!callRef.current) {
      return;
    }
    sendMessage({ type: "continueCall", roomId: callRef.current.roomId });
    setCall((current) =>
      current ? { ...current, continueUntilDisconnected: true } : current,
    );
  };

  const sendChatMessage = (event) => {
    event.preventDefault();
    const text = chatDraft.trim();
    if (!text || !callRef.current) {
      return;
    }
    sendMessage({
      type: "chatMessage",
      roomId: callRef.current.roomId,
      payload: { text },
    });
    setChatDraft("");
  };

  if (!authChecked) {
    return (
      <main className="loading-shell">
        <BrandBlock subtitle="Checking your session" />
      </main>
    );
  }

  if (route === "signup" || route === "signin") {
    return (
      <AuthPage
        authBusy={authBusy}
        authError={authError}
        authForm={authForm}
        mode={route}
        navigate={navigate}
        onSubmit={submitAuth}
        token={token}
        updateAuthField={updateAuthField}
      />
    );
  }

  if (route === "app" && token) {
    return (
      <MatchingApp
        accepted={accepted}
        call={call}
        callSecondsLeft={callSecondsLeft}
        chatDraft={chatDraft}
        chatMessages={chatMessages}
        canJoinQueue={canJoinQueue}
        connected={connected}
        continueCurrentCall={continueCurrentCall}
        endCurrentCall={endCurrentCall}
        events={events}
        joinQueue={joinQueue}
        leaveQueue={leaveQueue}
        localVideoRef={localVideoRef}
        logout={logout}
        match={match}
        navigate={navigate}
        profile={profile}
        profileBusy={profileBusy}
        profileError={profileError}
        profileMenuOpen={profileMenuOpen}
        profileSavedAt={profileSavedAt}
        queueStatus={queueStatus}
        remoteVideoRef={remoteVideoRef}
        saveProfile={saveProfile}
        secondsLeft={secondsLeft}
        sendChatMessage={sendChatMessage}
        setChatDraft={setChatDraft}
        shouldShowContinuePrompt={shouldShowContinuePrompt}
        setProfileMenuOpen={setProfileMenuOpen}
        updateProfileField={updateProfileField}
        handleProfilePhotoUpload={handleProfilePhotoUpload}
        acceptMatch={acceptMatch}
        rejectMatch={rejectMatch}
      />
    );
  }

  return (
    <LandingPage
      backendReady={backendReady}
      navigate={navigate}
      platformStats={platformStats}
      token={token}
    />
  );
}

function BrandBlock({ subtitle = "Live professional matching" }) {
  return (
    <div className="brand">
      <div>
        <Wordmark />
        <p>{subtitle}</p>
      </div>
    </div>
  );
}

function Wordmark() {
  return (
    <span className="wordmark" aria-label={APP_TITLE}>
      {APP_TITLE}
    </span>
  );
}

function PublicHeader({ navigate, token }) {
  return (
    <header className="public-nav">
      <button
        className="brand-button logo-brand"
        type="button"
        onClick={() => navigate("landing")}
      >
        <Wordmark />
      </button>
      <nav className="public-links" aria-label="Primary navigation">
        <button
          className="nav-primary"
          type="button"
          onClick={() => navigate(token ? "app" : "signin")}
        >
          Login / Sign up
        </button>
      </nav>
    </header>
  );
}

function LandingPage({ backendReady, navigate, platformStats, token }) {
  return (
    <main className="public-shell marketing-shell">
      <PublicHeader navigate={navigate} token={token} />

      <section className="marketing-hero">
        <div className="marketing-copy">
          <p className="hero-kicker">Real-time professional matching</p>
          <h2>
            Connect with
            <span>Hustlers, Build Your Future</span>
          </h2>
          <p>
            SpeedLink is a platform for professionals, with personalized
            networking based on their preferences in real time, powered by a
            matching model.
          </p>

          <div className="marketing-actions">
            <button
              className="primary-button"
              type="button"
              onClick={() => navigate(token ? "app" : "signup")}
            >
              <UserPlus size={18} />
              <span>{token ? "Open dashboard" : "Create account"}</span>
            </button>
            <button
              className="secondary-button"
              type="button"
              onClick={() => navigate("signin")}
            >
              <LogIn size={18} />
              <span>Sign in</span>
            </button>
          </div>
        </div>
        <div className="marketing-visual">
          <img
            className="network"
            src={headImage}
            alt="Networking illustration"
          />
        </div>
      </section>

      <section className="pain-section">
        <div className="section-heading">
          <p className="eyebrow">Why SpeedLink</p>
          <h2>Pain points we are addressing</h2>
        </div>
        <div className="pain-grid">
          <div>Finding real and authentic connections is becoming hard.</div>
          <div>
            It takes too long to connect with relevant people on professional
            networks.
          </div>
          <div>
            Professionals can feel isolated while building ambitious projects.
          </div>
          <div>
            Entrepreneurs struggle to discover the right co-founder and team.
          </div>
          <div>
            Developers need a faster way to find the right project collaborator.
          </div>
        </div>
      </section>

      <section className="steps-section">
        <div className="section-heading">
          <p className="eyebrow">How it works</p>
          <h2>Profile, match, accept, talk.</h2>
        </div>
        <div className="steps-grid">
          <article className="step-card">
            <img src={validationImage} alt="Eligibility validation" />
            <h3>Step 1: Eligibility</h3>
            <p className="text-size">
              Create a verified profile with the professional details needed for
              meaningful matching.
            </p>
          </article>
          <article className="step-card">
            <img src={registrationImage} alt="Registration" />
            <h3>Step 2: Registration</h3>
            <p className="text-size">
              Accurately filling the form is very crucial.
            </p>
            <p className="text-size">
              SpeedLink uses your role, goals, expertise, and preferences to
              understand your profile.
            </p>
          </article>
          <article className="step-card">
            <img src={videoCallImage} alt="Video matching" />
            <h3>Step 3: Match live</h3>
            <p className="text-size">
              Join the live queue, find the right people within seconds, and
              move into a video session.
            </p>
          </article>
        </div>
      </section>

      <footer className="footer">
        <div className="footer-contact compact">
          <div className="contact-title">Contact</div>

          <div className="contact-item">
            <div
              className="contact-entry inline compact-entry"
              role="note"
              aria-label="Email address"
            >
              <Mail size={16} className="contact-icon" />
              <div className="contact-row">
                <span className="contact-label">Email:</span>
                <span className="contact-text">contact@speedlink.app</span>
              </div>
            </div>
          </div>

          <div className="contact-item">
            <div
              className="contact-entry inline compact-entry"
              role="note"
              aria-label="Telegram handle"
            >
              <Send size={16} className="contact-icon" />
              <div className="contact-row">
                <span className="contact-label">Telegram:</span>
                <span className="contact-text">@SpeedLink101</span>
              </div>
            </div>
          </div>
        </div>

        <p className="foot">Copyright &copy; 2026 by SpeedLink</p>
      </footer>
    </main>
  );
}

function AuthPage({
  authBusy,
  authError,
  authForm,
  mode,
  navigate,
  onSubmit,
  token,
  updateAuthField,
}) {
  const isSignup = mode === "signup";

  return (
    <main className="public-shell auth-shell">
      <PublicHeader navigate={navigate} token={token} />

      <section className="auth-layout">
        <aside className="auth-story">
          <p className="eyebrow">{isSignup ? "New account" : "Welcome back"}</p>
          <h2>
            {isSignup
              ? "Create a professional matching profile."
              : "Sign in and return to the live queue."}
          </h2>
          <div className="auth-proof-list">
            <ProofItem
              icon={<ShieldCheck size={18} />}
              title="Account-backed"
              text="Profiles are saved through the backend auth API."
            />
            <ProofItem
              icon={<Users size={18} />}
              title="Matching-ready"
              text="Role and looking-for fields feed the matching service."
            />
            <ProofItem
              icon={<Video size={18} />}
              title="Session-ready"
              text="Accepted matches move directly into the video workflow."
            />
          </div>
        </aside>

        <form className="auth-card" onSubmit={(event) => onSubmit(event, mode)}>
          <div className="auth-card-header">
            <span className="form-icon">
              {isSignup ? <UserPlus size={20} /> : <LockKeyhole size={20} />}
            </span>
            <div>
              <h2>{isSignup ? "Sign up" : "Sign in"}</h2>
              <p>
                {isSignup
                  ? "Start with the details used for matching."
                  : "Use your saved SpeedLink account."}
              </p>
            </div>
          </div>

          {authError && <p className="form-error">{authError}</p>}

          <label>
            Email
            <input
              autoComplete="email"
              type="email"
              value={authForm.email}
              onChange={(event) => updateAuthField("email", event.target.value)}
              placeholder="you@example.com"
              required
            />
          </label>

          <label>
            Password
            <input
              autoComplete={isSignup ? "new-password" : "current-password"}
              minLength={isSignup ? 8 : undefined}
              type="password"
              value={authForm.password}
              onChange={(event) =>
                updateAuthField("password", event.target.value)
              }
              placeholder={isSignup ? "At least 8 characters" : "Your password"}
              required
            />
          </label>

          {isSignup && (
            <>
              <label>
                Name
                <input
                  value={authForm.displayName}
                  onChange={(event) =>
                    updateAuthField("displayName", event.target.value)
                  }
                  placeholder="Aarav Sharma"
                  required
                />
              </label>

              <div className="field-grid">
                <RoleSelect
                  label="I am a"
                  value={authForm.role}
                  onChange={(value) => updateAuthField("role", value)}
                />
                <RoleSelect
                  includeAnyone
                  label="Looking for"
                  value={authForm.lookingFor}
                  onChange={(value) => updateAuthField("lookingFor", value)}
                />
              </div>

              <label>
                Expertise
                <textarea
                  value={authForm.expertise}
                  onChange={(event) =>
                    updateAuthField("expertise", event.target.value)
                  }
                  rows={3}
                />
              </label>

              <label>
                Goal
                <textarea
                  value={authForm.goals}
                  onChange={(event) =>
                    updateAuthField("goals", event.target.value)
                  }
                  rows={3}
                />
              </label>

              <label>
                Collaboration intent
                <input
                  value={authForm.intent}
                  onChange={(event) =>
                    updateAuthField("intent", event.target.value)
                  }
                />
              </label>
            </>
          )}

          <button className="primary-button auth-submit" disabled={authBusy}>
            {isSignup ? <UserPlus size={18} /> : <LogIn size={18} />}
            <span>
              {authBusy
                ? "Please wait"
                : isSignup
                  ? "Create account"
                  : "Sign in"}
            </span>
          </button>

          <p className="auth-alt">
            {isSignup ? "Already have an account?" : "Need an account?"}
            <button
              type="button"
              onClick={() => navigate(isSignup ? "signin" : "signup")}
            >
              {isSignup ? "Sign in" : "Sign up"}
              <ArrowRight size={15} />
            </button>
          </p>
        </form>
      </section>
    </main>
  );
}

function MatchingApp({
  accepted,
  acceptMatch,
  call,
  callSecondsLeft,
  chatDraft,
  chatMessages,
  canJoinQueue,
  connected,
  continueCurrentCall,
  endCurrentCall,
  events,
  joinQueue,
  leaveQueue,
  localVideoRef,
  logout,
  match,
  navigate,
  profile,
  profileBusy,
  profileError,
  profileMenuOpen,
  profileSavedAt,
  queueStatus,
  rejectMatch,
  remoteVideoRef,
  saveProfile,
  secondsLeft,
  sendChatMessage,
  setChatDraft,
  shouldShowContinuePrompt,
  setProfileMenuOpen,
  updateProfileField,
  handleProfilePhotoUpload,
}) {
  return (
    <main className="app-shell">
      <header className="topbar">
        <button
          className="brand-button"
          type="button"
          onClick={() => navigate("landing")}
        >
          <Wordmark />
        </button>
        <div className="topbar-actions">
          <div className={`connection ${connected ? "online" : "offline"}`}>
            {connected ? <Wifi size={16} /> : <WifiOff size={16} />}
            <span>{connected ? "Online" : "Offline"}</span>
          </div>
          <div className="profile-menu-wrap">
            <button
              className="avatar-button"
              type="button"
              onClick={() => setProfileMenuOpen((open) => !open)}
              aria-label="Open profile settings"
              title="Profile"
            >
              {profile.profilePhoto ? (
                <img src={profile.profilePhoto} alt="" />
              ) : (
                <span>{profile.displayName?.charAt(0) || "S"}</span>
              )}
            </button>
            {profileMenuOpen && (
              <div className="profile-popover">
                <div className="profile-popover-head">
                  <div className="avatar-preview">
                    {profile.profilePhoto ? (
                      <img src={profile.profilePhoto} alt="" />
                    ) : (
                      <span>{profile.displayName?.charAt(0) || "S"}</span>
                    )}
                  </div>
                  <div>
                    <strong>{profile.displayName || "Your profile"}</strong>
                    <small>{profile.role || "Profession"}</small>
                  </div>
                </div>
                <label className="upload-control">
                  <Upload size={16} />
                  <span>Upload photo</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={handleProfilePhotoUpload}
                  />
                </label>
                <label>
                  Bio/About
                  <textarea
                    value={profile.bio}
                    onChange={(event) =>
                      updateProfileField("bio", event.target.value)
                    }
                    rows={3}
                    placeholder="What kind of people do you want to meet?"
                  />
                </label>
                <button
                  className="secondary-button"
                  type="button"
                  onClick={saveProfile}
                  disabled={profileBusy}
                >
                  <Settings size={16} />
                  <span>{profileBusy ? "Saving" : "Save settings"}</span>
                </button>
              </div>
            )}
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={logout}
            aria-label="Logout"
            title="Logout"
          >
            <LogOut size={18} />
          </button>
        </div>
      </header>

      <section className="layout">
        <form className="panel profile-panel" onSubmit={joinQueue}>
          <div className="panel-heading split-heading">
            <span>
              <UserRound size={19} />
              <h2>Profile</h2>
            </span>
            {profileSavedAt && <small>{profileSavedAt}</small>}
          </div>

          {profileError && <p className="form-error">{profileError}</p>}

          <label>
            Name
            <input
              value={profile.displayName}
              onChange={(event) =>
                updateProfileField("displayName", event.target.value)
              }
              placeholder="Aarav Sharma"
            />
          </label>

          <MultiSelectChips
            label="Your profession"
            options={roles}
            value={profile.role}
            onChange={(value) => updateProfileField("role", value)}
          />
          <MultiSelectChips
            label="Profession to connect with"
            options={[...roles, "Anyone"]}
            value={profile.lookingFor}
            onChange={(value) => updateProfileField("lookingFor", value)}
          />
          <MultiSelectChips
            label="Age range"
            options={ageRanges}
            value={profile.ageRange}
            onChange={(value) => updateProfileField("ageRange", value)}
          />
          <MultiSelectChips
            label="Company type"
            options={companyTypes}
            value={profile.companyType}
            onChange={(value) => updateProfileField("companyType", value)}
          />
          <MultiSelectChips
            label="Interests / conversation purpose"
            options={interestOptions}
            value={profile.interests}
            onChange={(value) => {
              updateProfileField("interests", value);
              updateProfileField("intent", value);
            }}
          />

          <label>
            About / goals
            <textarea
              value={profile.goals}
              onChange={(event) =>
                updateProfileField("goals", event.target.value)
              }
              rows={3}
              placeholder="Share what you want from the next conversation"
            />
          </label>

          <div className="profile-actions">
            <button
              className="secondary-button"
              type="button"
              disabled={profileBusy}
              onClick={saveProfile}
            >
              <Save size={17} />
              <span>{profileBusy ? "Saving" : "Save"}</span>
            </button>
            <button
              className="primary-button"
              disabled={
                !canJoinQueue ||
                queueStatus.inQueue ||
                Boolean(call) ||
                profileBusy
              }
            >
              <Search size={17} />
              <span>{queueStatus.inQueue ? "Searching" : "Enter queue"}</span>
            </button>
          </div>

          <button
            className="quiet-button"
            type="button"
            onClick={leaveQueue}
            disabled={!queueStatus.inQueue}
          >
            <X size={17} />
            <span>Leave queue</span>
          </button>
        </form>

        <section className="workspace">
          {!call && (
            <>
              <section className="queue-surface">
                <div>
                  <p className="eyebrow">Queue</p>
                  <h2>{queueStatus.message}</h2>
                  <p className="surface-copy">
                    {connected
                      ? "Your profile is connected to the matching service."
                      : "The realtime matching connection is reconnecting."}
                  </p>
                </div>
                <div className="queue-meter">
                  <span>{queueStatus.queueSize}</span>
                  <small>waiting</small>
                </div>
              </section>

              <section className="event-panel">
                <div className="panel-heading">
                  <Send size={18} />
                  <h2>Activity</h2>
                </div>
                <div className="event-list">
                  {events.length === 0 && (
                    <p className="empty-state">No activity yet</p>
                  )}
                  {events.map((event) => (
                    <article className="event-row" key={event.id}>
                      <span>{event.time}</span>
                      <p>{event.text}</p>
                    </article>
                  ))}
                </div>
              </section>
            </>
          )}

          {call && (
            <section className="call-stage">
              <div className="call-main">
                <div className="call-toolbar">
                  <div>
                    <p className="eyebrow">Live room</p>
                    <h2>{call.peer.displayName}</h2>
                  </div>
                  <div
                    className={`call-timer pill ${callSecondsLeft <= 60 && !call.continueUntilDisconnected ? "ending-soon" : ""}`}
                  >
                    <Clock size={18} />
                    <div>
                      <span>
                        {call.continueUntilDisconnected
                          ? "Continues"
                          : formatDuration(callSecondsLeft)}
                      </span>
                      <small>
                        {call.continueUntilDisconnected
                          ? "Until disconnect"
                          : "Time left"}
                      </small>
                    </div>
                  </div>
                </div>
                <div className="video-grid">
                  <div className="video-frame remote-video">
                    <video ref={remoteVideoRef} autoPlay playsInline />
                    <span>{call.peer.displayName}</span>
                  </div>
                  <div className="video-frame local-video">
                    <video ref={localVideoRef} autoPlay playsInline muted />
                    <span>You</span>
                  </div>
                </div>
              </div>

              <aside className="call-sidebar">
                <div>
                  <p className="eyebrow">In session</p>
                  <h2>{call.peer.displayName}</h2>
                  <p>{call.peer.role}</p>
                </div>
                <dl>
                  <div>
                    <dt>Looking for</dt>
                    <dd>{call.peer.lookingFor}</dd>
                  </div>
                  <div>
                    <dt>Expertise</dt>
                    <dd>{call.peer.expertise}</dd>
                  </div>
                  <div>
                    <dt>Goal</dt>
                    <dd>{call.peer.goals}</dd>
                  </div>
                </dl>
                <section className="call-chat" aria-label="Call chat">
                  <div className="call-chat-head">
                    <div>
                      <p className="eyebrow">Chat</p>
                      <h3>Messages</h3>
                    </div>
                    <small>{chatMessages.length}</small>
                  </div>
                  <div className="call-chat-list">
                    {chatMessages.length === 0 && (
                      <p className="empty-state compact">No messages yet</p>
                    )}
                    {chatMessages.map((message) => (
                      <article
                        className={message.mine ? "chat-bubble mine" : "chat-bubble"}
                        key={message.id}
                      >
                        <p>{message.text}</p>
                        <time>
                          {new Date(message.sentAtEpochMillis).toLocaleTimeString([], {
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </time>
                      </article>
                    ))}
                  </div>
                  <form className="call-chat-form" onSubmit={sendChatMessage}>
                    <input
                      value={chatDraft}
                      maxLength={500}
                      onChange={(event) => setChatDraft(event.target.value)}
                      placeholder="Send a message"
                      aria-label="Chat message"
                    />
                    <button
                      className="icon-button send-chat-button"
                      type="submit"
                      disabled={!chatDraft.trim()}
                      aria-label="Send chat message"
                      title="Send"
                    >
                      <Send size={17} />
                    </button>
                  </form>
                </section>
                <button
                  className="danger-button"
                  type="button"
                  onClick={endCurrentCall}
                >
                  <PhoneOff size={18} />
                  <span>End session</span>
                </button>
              </aside>
            </section>
          )}
        </section>
      </section>

      {match && (
        <div className="modal-backdrop">
          <section className="match-dialog" role="dialog" aria-modal="true">
            <div className="match-header">
              <div>
                <p className="eyebrow">Match found</p>
                <h2>{match.candidate.displayName}</h2>
              </div>
              <div
                className="countdown"
                aria-label={`${secondsLeft} seconds left`}
              >
                <span>{secondsLeft}</span>
                <small>sec</small>
              </div>
            </div>

            <div className="candidate-grid">
              <div>
                <dt>Role</dt>
                <dd>{match.candidate.role}</dd>
              </div>
              <div>
                <dt>Looking for</dt>
                <dd>{match.candidate.lookingFor}</dd>
              </div>
              <div>
                <dt>Expertise</dt>
                <dd>{match.candidate.expertise}</dd>
              </div>
              <div>
                <dt>Goal</dt>
                <dd>{match.candidate.goals}</dd>
              </div>
            </div>

            <div className="match-actions">
              <button
                className="danger-button"
                type="button"
                onClick={rejectMatch}
              >
                <X size={18} />
                <span>Reject</span>
              </button>
              <button
                className="primary-button"
                type="button"
                onClick={acceptMatch}
                disabled={accepted}
              >
                {accepted ? <Video size={18} /> : <Check size={18} />}
                <span>{accepted ? "Waiting" : "Accept"}</span>
              </button>
            </div>
          </section>
        </div>
      )}

      {shouldShowContinuePrompt && (
        <section className="expiry-toast" role="status" aria-live="polite">
          <div>
            <p className="eyebrow">One minute left</p>
            <h2>Continue this call?</h2>
            <p>Continue keeps the session open until someone disconnects.</p>
          </div>
          <div className="expiry-toast-side">
            <div
              className="countdown compact-countdown"
              aria-label={`${callSecondsLeft} seconds left`}
            >
              <span>{callSecondsLeft}</span>
              <small>sec</small>
            </div>
            <div className="expiry-actions">
              <button
                className="danger-button"
                type="button"
                onClick={endCurrentCall}
              >
                <PhoneOff size={18} />
                <span>End now</span>
              </button>
              <button
                className="primary-button"
                type="button"
                onClick={continueCurrentCall}
              >
                <Video size={18} />
                <span>Continue</span>
              </button>
            </div>
          </div>
        </section>
      )}
    </main>
  );
}

function Metric({ label, value }) {
  return (
    <div className="metric-item">
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function FlowStep({ title, text }) {
  return (
    <article className="flow-step">
      <h3>{title}</h3>
      <p>{text}</p>
    </article>
  );
}

function ProofItem({ icon, text, title }) {
  return (
    <article className="proof-item">
      <span>{icon}</span>
      <div>
        <h3>{title}</h3>
        <p>{text}</p>
      </div>
    </article>
  );
}

function MultiSelectChips({ label, options, value, onChange }) {
  const selected = splitProfessionValue(value);
  const available = options.filter((option) => !selected.includes(option));

  const addValue = (nextValue) => {
    if (!nextValue) {
      return;
    }
    onChange(joinProfessionValue([...selected, nextValue]));
  };

  const removeValue = (nextValue) => {
    onChange(
      joinProfessionValue(selected.filter((item) => item !== nextValue)),
    );
  };

  return (
    <fieldset className="filter-select">
      <legend>{label}</legend>
      <div className="select-shell">
        <select value="" onChange={(event) => addValue(event.target.value)}>
          <option value="">Add filter</option>
          {available.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <ChevronDown size={16} />
      </div>
      <div className="chip-list">
        {selected.length === 0 && (
          <span className="empty-chip">No selection</span>
        )}
        {selected.map((item) => (
          <button
            className="filter-chip"
            key={item}
            type="button"
            onClick={() => removeValue(item)}
            title={`Remove ${item}`}
          >
            <span>{item}</span>
            <X size={13} />
          </button>
        ))}
      </div>
    </fieldset>
  );
}

function RoleSelect({ includeAnyone = false, label, value, onChange }) {
  const options = includeAnyone ? [...roles, "Anyone"] : roles;
  const selected = splitProfessionValue(value);

  const toggleRole = (role) => {
    const next = selected.includes(role)
      ? selected.filter((item) => item !== role)
      : [...selected, role];
    onChange(joinProfessionValue(next));
  };

  return (
    <fieldset className="profession-picker">
      <legend>{label}</legend>
      <div className="profession-options">
        {options.map((role) => {
          const isSelected = selected.includes(role);
          return (
            <button
              aria-pressed={isSelected}
              className={
                isSelected ? "profession-option selected" : "profession-option"
              }
              key={role}
              type="button"
              onClick={() => toggleRole(role)}
            >
              {role}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}

export default App;
