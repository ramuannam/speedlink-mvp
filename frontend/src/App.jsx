import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createClient } from "@supabase/supabase-js";
import {
  ArrowRight,
  Check,
  ChevronDown,
  Clock,
  LockKeyhole,
  LogIn,
  LogOut,
  Mail,
  Mic,
  MicOff,
  PhoneOff,
  RefreshCcw,
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
  VideoOff,
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
const SUPABASE_URL = import.meta.env.VITE_SUPABASE_URL || "";
const SUPABASE_KEY =
  import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY ||
  import.meta.env.VITE_SUPABASE_ANON_KEY ||
  "";
const supabase =
  SUPABASE_URL && SUPABASE_KEY
    ? createClient(SUPABASE_URL, SUPABASE_KEY)
    : null;

const routes = {
  landing: "/",
  signup: "/signup",
  signin: "/signin",
  resetPassword: "/reset-password",
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
const ANYONE_RANDOM = "Anyone/Random";
const connectRoles = roles.filter((role) => role !== "Other / Random");
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
  ageRange: "",
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
  if (path === routes.resetPassword) {
    return "resetPassword";
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

function normalizeOpenLabel(value) {
  if (value == null) {
    return "";
  }
  return String(value)
    .split(",")
    .map((item) => (item.trim() === "Anyone" ? ANYONE_RANDOM : item.trim()))
    .filter(Boolean)
    .join(", ");
}

function normalizeProfile(profile) {
  const merged = { ...defaultProfile, ...(profile || {}) };
  return Object.fromEntries(
    Object.entries(merged).map(([key, value]) => {
      const nextValue = value == null ? defaultProfile[key] || "" : value;
      return [
        key,
        key === "lookingFor" ? normalizeOpenLabel(nextValue) : nextValue,
      ];
    }),
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

function passwordStrength(password) {
  const value = String(password || "");
  const checks = [
    value.length >= 8,
    /[a-z]/.test(value),
    /[A-Z]/.test(value),
    /\d/.test(value),
    /[^A-Za-z0-9]/.test(value),
  ];
  const score = checks.filter(Boolean).length;
  if (score <= 2) {
    return { label: "Weak", score, valid: false };
  }
  if (score <= 4) {
    return { label: "Good", score, valid: score >= 4 };
  }
  return { label: "Strong", score, valid: true };
}

function normalizePhoneNumber(phone) {
  const value = String(phone || "").trim();
  if (value.startsWith("+")) {
    return `+${value.slice(1).replace(/\D/g, "")}`;
  }
  return value.replace(/\D/g, "");
}

function hasSupabaseAuthCallback() {
  const url = new URL(window.location.href);
  const hash = new URLSearchParams(url.hash.replace(/^#/, ""));
  return Boolean(
    url.searchParams.get("code") ||
      url.searchParams.get("token_hash") ||
      hash.get("access_token") ||
      hash.get("refresh_token"),
  );
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
  const [authError, setAuthError] = useState("");
  const [authNotice, setAuthNotice] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [authStep, setAuthStep] = useState("start");
  const [authForm, setAuthForm] = useState({
    email: "",
    phone: "",
    verificationCode: "",
    supabaseAccessToken: "",
    supabaseRefreshToken: "",
    password: "",
    confirmPassword: "",
    acceptTerms: false,
    rememberMe: true,
    displayName: "",
    role: "Developer",
    lookingFor: "Designer",
    expertise: "",
    goals: "",
    intent: "MVP build partner",
    bio: "",
    interests: "Build projects",
    companyType: "Startup",
    ageRange: "",
    profilePhoto: "",
  });

  useEffect(() => {
    document.title = APP_TITLE;
  }, [route]);
  const [authChecked, setAuthChecked] = useState(false);
  const [token, setToken] = useState(
    () => localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || "",
  );
  const [connected, setConnected] = useState(false);
  const [userId, setUserId] = useState("");
  const [profile, setProfile] = useState(defaultProfile);
  const [profileBusy, setProfileBusy] = useState(false);
  const [profileError, setProfileError] = useState("");
  const [profileSavedAt, setProfileSavedAt] = useState("");
  const [profileMenuOpen, setProfileMenuOpen] = useState(false);
  const [platformStats, setPlatformStats] = useState(null);
  const [liveStats, setLiveStats] = useState({
    onlineUsers: 0,
    queuedUsers: 0,
  });
  const [backendReady, setBackendReady] = useState(false);
  const [queueStatus, setQueueStatus] = useState({
    inQueue: false,
    queueSize: 0,
    message: "Offline",
  });
  const [matchingMode, setMatchingMode] = useState("advanced");
  const [match, setMatch] = useState(null);
  const [accepted, setAccepted] = useState(false);
  const [call, setCall] = useState(null);
  const [audioEnabled, setAudioEnabled] = useState(true);
  const [videoEnabled, setVideoEnabled] = useState(true);
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
    setAudioEnabled(true);
    setVideoEnabled(true);

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
    sessionStorage.removeItem(TOKEN_KEY);
    if (supabase) {
      supabase.auth.signOut();
    }
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
      if (route === "signin" && hasSupabaseAuthCallback()) {
        localStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(TOKEN_KEY);
        if (supabase) {
          await supabase.auth.signOut();
        }
        if (!cancelled) {
          window.history.replaceState({}, "", routes.signin);
          setToken("");
          setAuthNotice("Email verified successfully. Please sign in with your email and password.");
          setAuthChecked(true);
        }
        return;
      }

      if (route === "resetPassword") {
        localStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(TOKEN_KEY);
        if (token) {
          setToken("");
        }
        setAuthChecked(true);
        return;
      }

      if (!token) {
        if (supabase) {
          try {
            const { data } = await supabase.auth.getSession();
            const accessToken = data?.session?.access_token;
            if (accessToken) {
              const result = await apiRequest("/auth/supabase", {
                method: "POST",
                body: JSON.stringify({ accessToken }),
              });
              if (cancelled) {
                return;
              }
              const profileData = normalizeProfile(result.profile);
              localStorage.setItem(TOKEN_KEY, result.token);
              setToken(result.token);
              setProfile(profileData);
              setUserId(profileData.userId || "");
              return;
            }
          } catch (error) {
            await supabase.auth.signOut();
            localStorage.removeItem(TOKEN_KEY);
            sessionStorage.removeItem(TOKEN_KEY);
          }
        }
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
        if (cancelled) {
          return;
        }
        try {
          const { data } = supabase ? await supabase.auth.getSession() : { data: null };
          const accessToken = data?.session?.access_token;
          if (!accessToken) {
            throw error;
          }
          const result = await apiRequest("/auth/supabase", {
            method: "POST",
            body: JSON.stringify({ accessToken }),
          });
          if (cancelled) {
            return;
          }
          const profileData = normalizeProfile(result.profile);
          localStorage.setItem(TOKEN_KEY, result.token);
          sessionStorage.removeItem(TOKEN_KEY);
          setToken(result.token);
          setProfile(profileData);
          setUserId(profileData.userId || "");
        } catch (sessionError) {
          if (!cancelled) {
            logout();
          }
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
  }, [apiRequest, logout, route, token]);

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
    if (!authChecked || !token || route !== "app") {
      return undefined;
    }

    let cancelled = false;

    async function refreshLiveStats() {
      try {
        const stats = await apiRequest("/stats", { token });
        if (!cancelled) {
          setLiveStats({
            onlineUsers: Number(stats?.onlineUsers || 0),
            queuedUsers: Number(stats?.queuedUsers || 0),
          });
        }
      } catch (error) {
        if (!cancelled) {
          setLiveStats((current) => ({ ...current, onlineUsers: connected ? current.onlineUsers : 0 }));
        }
      }
    }

    refreshLiveStats();
    const timer = window.setInterval(refreshLiveStats, 5000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [apiRequest, authChecked, connected, route, token]);

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
        setAudioEnabled(true);
        setVideoEnabled(true);

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
        setLiveStats((current) => ({
          ...current,
          onlineUsers: Math.max(current.onlineUsers, 1),
        }));
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
        setLiveStats((current) => ({
          ...current,
          queuedUsers: Number(payload.queueSize || 0),
        }));
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
        addEvent(`Call started with ${payload.peer?.displayName || "your match"}`);
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
    let offlineTimer = null;
    let reconnectAttempt = 0;

    const clearOfflineTimer = () => {
      if (offlineTimer) {
        window.clearTimeout(offlineTimer);
        offlineTimer = null;
      }
    };

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
        clearOfflineTimer();
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
        clearOfflineTimer();
        offlineTimer = window.setTimeout(() => {
          if (!cancelled && socketRef.current == null) {
            setConnected(false);
            setQueueStatus((current) => ({
              ...current,
              inQueue: false,
              message: "Reconnecting",
            }));
          }
        }, 1200);
        scheduleReconnect();
      };
      socket.onerror = () => handlerRef.current({ type: "error", payload: { message: "WebSocket connection failed" } });
    }

    connectSocket();

    return () => {
      cancelled = true;
      if (reconnectTimer) {
        window.clearTimeout(reconnectTimer);
      }
      clearOfflineTimer();
      stopHeartbeat();
      if (socketRef.current) {
        socketRef.current.close();
        socketRef.current = null;
      }
      socketRef.current = null;
      cleanupCall();
    };
  }, [authChecked, cleanupCall, token]);

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

  const completeSupabaseSession = async (accessToken) => {
    const result = await apiRequest("/auth/supabase", {
      method: "POST",
      body: JSON.stringify({ accessToken }),
    });
    if (!result?.token) {
      throw new Error("Supabase exchange did not return an app session.");
    }

    const profileData = normalizeProfile(result.profile);
    if (authForm.rememberMe) {
      localStorage.setItem(TOKEN_KEY, result.token);
      sessionStorage.removeItem(TOKEN_KEY);
    } else {
      sessionStorage.setItem(TOKEN_KEY, result.token);
      localStorage.removeItem(TOKEN_KEY);
    }
    setToken(result.token);
    setProfile(profileData);
    setUserId(profileData.userId || "");
    setAuthChecked(true);
    navigate("app", { replace: true });
  };

  const updateAuthField = (field, value) => {
    setAuthError("");
    setAuthNotice("");
    if (field === "email") {
      setAuthForm((current) => ({
        ...current,
        email: value,
        verificationCode: "",
        supabaseAccessToken: "",
        supabaseRefreshToken: "",
      }));
      return;
    }
    setAuthForm((current) => ({ ...current, [field]: value }));
  };

  const signupWithPassword = async (event) => {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError("");
    setAuthNotice("");

    try {
      if (!supabase) {
        throw new Error("Supabase is not configured. Add VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY.");
      }
      const normalizedPhone = normalizePhoneNumber(authForm.phone);
      if (!authForm.displayName.trim()) {
        throw new Error("Full name is required.");
      }
      if (normalizedPhone.length < 10) {
        throw new Error("Enter a valid WhatsApp phone number.");
      }
      if (!authForm.acceptTerms) {
        throw new Error("Please accept the Terms of Service to continue.");
      }
      if (authForm.password !== authForm.confirmPassword) {
        throw new Error("Passwords do not match.");
      }
      if (!passwordStrength(authForm.password).valid) {
        throw new Error("Use at least 8 characters with uppercase, lowercase, number, and symbol.");
      }
      await apiRequest("/auth/verification-code", {
        method: "POST",
        body: JSON.stringify({
          email: authForm.email,
          phone: normalizedPhone,
          purpose: "signup",
        }),
      });
      const { data, error } = await supabase.auth.signUp({
        email: authForm.email,
        password: authForm.password,
        options: {
          emailRedirectTo: `${window.location.origin}${routes.signin}`,
          data: {
            full_name: authForm.displayName.trim(),
            whatsapp_phone: normalizedPhone,
          },
        },
      });
      if (error) {
        throw error;
      }
      if (data?.user && (!Array.isArray(data.user.identities) || data.user.identities.length === 0)) {
        throw new Error("This email is already signed up. Please sign in instead.");
      }
      setAuthNotice("Check your inbox and spam folder for the verification email before signing in.");
      setAuthForm((current) => ({ ...current, password: "", confirmPassword: "" }));
    } catch (error) {
      setAuthError(
        /already exists|registered/i.test(error.message)
          ? "This email is already signed up. Please sign in instead."
          : error.message,
      );
    } finally {
      setAuthBusy(false);
    }
  };

  const resendSignupVerification = async () => {
    setAuthBusy(true);
    setAuthError("");
    setAuthNotice("");

    try {
      if (!supabase) {
        throw new Error("Supabase is not configured. Add VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY.");
      }
      if (!authForm.email.trim()) {
        throw new Error("Enter your email first.");
      }
      if (!authForm.displayName.trim()) {
        throw new Error("Full name is required.");
      }
      if (normalizePhoneNumber(authForm.phone).length < 10) {
        throw new Error("Enter a valid WhatsApp phone number.");
      }
      if (!authForm.acceptTerms) {
        throw new Error("Please accept the Terms of Service to continue.");
      }
      if (authForm.password !== authForm.confirmPassword) {
        throw new Error("Passwords do not match.");
      }
      if (!passwordStrength(authForm.password).valid) {
        throw new Error("Use at least 8 characters with uppercase, lowercase, number, and symbol.");
      }
      const { error } = await supabase.auth.resend({
        type: "signup",
        email: authForm.email,
        options: {
          emailRedirectTo: `${window.location.origin}${routes.signin}`,
        },
      });
      if (error) {
        throw error;
      }
      setAuthNotice("Verification email requested again. Check your inbox and spam folder; delivery is handled by Supabase.");
    } catch (error) {
      setAuthError(
        /invalid login credentials|email not confirmed|user not found/i.test(error.message)
          ? "No verified account was found for this email. Please sign up first."
          : error.message,
      );
    } finally {
      setAuthBusy(false);
    }
  };

  const sendPasswordResetLink = async (event) => {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError("");
    setAuthNotice("");

    try {
      if (!supabase) {
        throw new Error("Supabase is not configured. Add VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY.");
      }
      await apiRequest("/auth/verification-code", {
        method: "POST",
        body: JSON.stringify({
          email: authForm.email,
          purpose: "reset",
        }),
      }).catch(() => null);
      const { error } = await supabase.auth.resetPasswordForEmail(authForm.email, {
        redirectTo: `${window.location.origin}${routes.resetPassword}`,
      });
      if (error) {
        throw error;
      }
      setAuthNotice("If this email exists, a reset link has been sent.");
    } catch (error) {
      setAuthNotice("If this email exists, a reset link has been sent.");
    } finally {
      setAuthBusy(false);
    }
  };

  const preparePasswordResetSession = async () => {
    if (!supabase) {
      throw new Error("Supabase is not configured. Add VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY.");
    }
    const url = new URL(window.location.href);
    const code = url.searchParams.get("code");
    if (code) {
      const { error } = await supabase.auth.exchangeCodeForSession(code);
      if (error) {
        throw error;
      }
      window.history.replaceState({}, "", routes.resetPassword);
    }
    const { data, error } = await supabase.auth.getSession();
    if (error) {
      throw error;
    }
    if (!data?.session?.access_token) {
      throw new Error("Reset link is invalid or expired. Please request a new one.");
    }
    return data.session.access_token;
  };

  const resetPassword = async (event) => {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError("");
    setAuthNotice("");

    try {
      if (authForm.password !== authForm.confirmPassword) {
        throw new Error("Passwords do not match.");
      }
      if (!passwordStrength(authForm.password).valid) {
        throw new Error("Use at least 8 characters with uppercase, lowercase, number, and symbol.");
      }
      const resetAccessToken = await preparePasswordResetSession();
      const { error: passwordError } = await supabase.auth.updateUser({
        password: authForm.password,
      });
      if (passwordError) {
        throw passwordError;
      }
      const { data } = await supabase.auth.getUser();
      await apiRequest("/auth/password-reset", {
        method: "POST",
        body: JSON.stringify({
          email: data?.user?.email || authForm.email,
          supabaseAccessToken: resetAccessToken,
        }),
      }).catch(() => null);
      await supabase.auth.signOut();
      setAuthStep("start");
      setAuthNotice("Password reset successfully. Go to sign in and use your new password.");
      navigate("signin", { replace: true });
      setAuthForm((current) => ({ ...current, password: "", confirmPassword: "" }));
    } catch (error) {
      setAuthError(error.message);
    } finally {
      setAuthBusy(false);
    }
  };

  const loginWithPassword = async (event) => {
    event.preventDefault();
    setAuthBusy(true);
    setAuthError("");
    setAuthNotice("");

    try {
      if (!supabase) {
        throw new Error("Supabase is not configured. Add VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY.");
      }
      localStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(TOKEN_KEY);
      setToken("");
      const { data, error } = await supabase.auth.signInWithPassword({
          email: authForm.email,
          password: authForm.password,
      });
      if (error) {
        throw error;
      }
      const accessToken = data?.session?.access_token;
      if (!accessToken) {
        throw new Error("Supabase did not return a session.");
      }
      await completeSupabaseSession(accessToken);
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
      sendMessage({
        type: "joinQueue",
        profile: savedProfile,
        matchingMode,
      });
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

  const toggleLocalAudio = () => {
    const tracks = localStreamRef.current?.getAudioTracks() || [];
    if (tracks.length === 0) {
      addEvent("Microphone is not available");
      return;
    }
    const nextEnabled = !tracks.some((track) => track.enabled);
    tracks.forEach((track) => {
      track.enabled = nextEnabled;
    });
    setAudioEnabled(nextEnabled);
  };

  const toggleLocalVideo = () => {
    const tracks = localStreamRef.current?.getVideoTracks() || [];
    if (tracks.length === 0) {
      addEvent("Camera is not available");
      return;
    }
    const nextEnabled = !tracks.some((track) => track.enabled);
    tracks.forEach((track) => {
      track.enabled = nextEnabled;
    });
    setVideoEnabled(nextEnabled);
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

  if (route === "signup" || route === "signin" || route === "resetPassword") {
    return (
      <AuthPage
        authBusy={authBusy}
        authError={authError}
        authForm={authForm}
        authNotice={authNotice}
        authStep={authStep}
        mode={route}
        navigate={navigate}
        onLogin={loginWithPassword}
        onPasswordReset={route === "signin" ? sendPasswordResetLink : resetPassword}
        onResendSignupVerification={resendSignupVerification}
        onSignup={signupWithPassword}
        setAuthStep={setAuthStep}
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
        audioEnabled={audioEnabled}
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
        liveStats={liveStats}
        localVideoRef={localVideoRef}
        logout={logout}
        match={match}
        matchingMode={matchingMode}
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
        setMatchingMode={setMatchingMode}
        shouldShowContinuePrompt={shouldShowContinuePrompt}
        setProfileMenuOpen={setProfileMenuOpen}
        toggleLocalAudio={toggleLocalAudio}
        toggleLocalVideo={toggleLocalVideo}
        updateProfileField={updateProfileField}
        videoEnabled={videoEnabled}
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
  authNotice,
  authStep,
  mode,
  navigate,
  onLogin,
  onPasswordReset,
  onResendSignupVerification,
  onSignup,
  setAuthStep,
  token,
  updateAuthField,
}) {
  const isSignup = mode === "signup";
  const isReset = mode === "resetPassword";
  const isForgot = !isSignup && !isReset && authStep === "reset-start";
  const strength = passwordStrength(authForm.password);
  const canResendSignupVerification =
    Boolean(authForm.email.trim()) &&
    Boolean(authForm.displayName.trim()) &&
    normalizePhoneNumber(authForm.phone).length >= 10 &&
    authForm.acceptTerms &&
    authForm.password === authForm.confirmPassword &&
    strength.valid;

  return (
    <main className="public-shell auth-shell">
      <PublicHeader navigate={navigate} token={token} />

      <section className="auth-layout">
        <aside className="auth-story">
          <p className="eyebrow">
            {isSignup ? "New account" : isReset ? "Password reset" : "Welcome back"}
          </p>
          <h2>
            {isSignup
              ? "Create your account with verified email."
              : isReset
                ? "Choose a new secure password."
                : "Sign in and return to the live queue."}
          </h2>
          <div className="auth-proof-list">
            <ProofItem
              icon={<ShieldCheck size={18} />}
              title="Verified email"
              text="Supabase handles verification links, reset links, and password security."
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

        <section className="auth-card">
          <div className="auth-card-header">
            <span className="form-icon">
              {isSignup ? <UserPlus size={20} /> : isReset ? <ShieldCheck size={20} /> : <LockKeyhole size={20} />}
            </span>
            <div>
              <h2>{isSignup ? "Sign up" : isReset ? "Reset password" : "Sign in"}</h2>
              <p>
                {isSignup
                  ? "Use a strong password and verify your email before signing in."
                  : isReset
                    ? "This page works only from a valid reset email link."
                    : isForgot
                      ? "Enter your email and we will send reset instructions."
                      : "Use your verified email and password."}
              </p>
            </div>
          </div>

          {authError && <p className="form-error">{authError}</p>}
          {authNotice && <p className="form-notice">{authNotice}</p>}

          {isSignup && (
            <form className="email-auth-form" onSubmit={onSignup}>
              <SignupProfileFields authForm={authForm} updateAuthField={updateAuthField} />
              <EmailField authForm={authForm} updateAuthField={updateAuthField} />
              <PasswordFields authForm={authForm} updateAuthField={updateAuthField} />
              <div className={`password-meter score-${strength.score}`}>
                <span />
                <strong>{strength.label}</strong>
              </div>
              <label className="check-row">
                <input
                  checked={authForm.acceptTerms}
                  onChange={(event) => updateAuthField("acceptTerms", event.target.checked)}
                  type="checkbox"
                />
                <span>I accept the Terms of Service and Privacy Policy.</span>
              </label>
              <button className="primary-button auth-submit" disabled={authBusy}>
                <UserPlus size={18} />
                <span>{authBusy ? "Creating account" : "Create account"}</span>
              </button>
              <button
                className="text-button"
                disabled={authBusy || !canResendSignupVerification}
                type="button"
                onClick={onResendSignupVerification}
              >
                Resend verification email
              </button>
            </form>
          )}

          {!isSignup && !isReset && !isForgot && (
            <form className="email-auth-form" onSubmit={onLogin}>
              <EmailField authForm={authForm} updateAuthField={updateAuthField} />
              <label>
                Password
                <input
                  autoComplete="current-password"
                  type="password"
                  value={authForm.password}
                  onChange={(event) => updateAuthField("password", event.target.value)}
                  placeholder="Your password"
                  required
                />
              </label>
              <label className="check-row">
                <input
                  checked={authForm.rememberMe}
                  onChange={(event) => updateAuthField("rememberMe", event.target.checked)}
                  type="checkbox"
                />
                <span>Remember me</span>
              </label>
              <button className="primary-button auth-submit" disabled={authBusy}>
                <LogIn size={18} />
                <span>{authBusy ? "Signing in" : "Sign in"}</span>
              </button>
              <button
                className="text-button"
                type="button"
                onClick={() => {
                  setAuthStep("reset-start");
                  updateAuthField("verificationCode", "");
                }}
              >
                Forgot password?
              </button>
            </form>
          )}

          {isForgot && (
            <form className="email-auth-form" onSubmit={onPasswordReset}>
              <EmailField authForm={authForm} updateAuthField={updateAuthField} />
              <button className="primary-button auth-submit" disabled={authBusy}>
                <Mail size={18} />
                <span>{authBusy ? "Sending" : "Send reset link"}</span>
              </button>
            </form>
          )}

          {isReset && (
            <form className="email-auth-form" onSubmit={onPasswordReset}>
              <PasswordFields authForm={authForm} updateAuthField={updateAuthField} />
              <div className={`password-meter score-${strength.score}`}>
                <span />
                <strong>{strength.label}</strong>
              </div>
              <button className="primary-button auth-submit" disabled={authBusy}>
                <LockKeyhole size={18} />
                <span>{authBusy ? "Updating password" : "Update password"}</span>
              </button>
            </form>
          )}

          <p className="auth-helper">
            {isSignup
              ? "You must verify your email before dashboard access is allowed."
              : isReset
                ? "After resetting, sign in again with your new password."
                : isForgot
                  ? "For privacy, we do not reveal whether an email is registered."
                  : "Only verified users can access the dashboard."}
          </p>

          <p className="auth-alt">
            {isSignup ? "Already have an account?" : isReset || isForgot ? "Remembered it?" : "New to SpeedLink?"}
            <button
              type="button"
              onClick={() => {
                setAuthStep("start");
                navigate(isSignup || isReset || isForgot ? "signin" : "signup");
              }}
            >
              {isSignup || isReset || isForgot ? "Sign in" : "Sign up"}
              <ArrowRight size={15} />
            </button>
          </p>
        </section>
      </section>
    </main>
  );
}

function EmailField({ authForm, updateAuthField }) {
  return (
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
  );
}

function SignupProfileFields({ authForm, updateAuthField }) {
  return (
    <>
      <label>
        Full name
        <input
          autoComplete="name"
          type="text"
          value={authForm.displayName}
          onChange={(event) => updateAuthField("displayName", event.target.value)}
          placeholder="Your full name"
          required
        />
      </label>
      <label>
        WhatsApp phone number
        <input
          autoComplete="tel"
          inputMode="tel"
          type="tel"
          value={authForm.phone}
          onChange={(event) => updateAuthField("phone", event.target.value)}
          placeholder="+91 98765 43210"
          required
        />
      </label>
    </>
  );
}

function PasswordFields({ authForm, updateAuthField }) {
  return (
    <div className="auth-field-grid">
      <label>
        Password
        <input
          autoComplete="new-password"
          minLength={8}
          type="password"
          value={authForm.password}
          onChange={(event) => updateAuthField("password", event.target.value)}
          placeholder="At least 8 characters"
          required
        />
      </label>
      <label>
        Confirm password
        <input
          autoComplete="new-password"
          minLength={8}
          type="password"
          value={authForm.confirmPassword}
          onChange={(event) => updateAuthField("confirmPassword", event.target.value)}
          placeholder="Repeat password"
          required
        />
      </label>
    </div>
  );
}

function MatchingApp({
  accepted,
  acceptMatch,
  audioEnabled,
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
  liveStats,
  localVideoRef,
  logout,
  match,
  matchingMode,
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
  setMatchingMode,
  shouldShowContinuePrompt,
  setProfileMenuOpen,
  toggleLocalAudio,
  toggleLocalVideo,
  updateProfileField,
  videoEnabled,
  handleProfilePhotoUpload,
}) {
  const [isLocalVideoPrimary, setIsLocalVideoPrimary] = useState(false);
  const [sessionDetailsOpen, setSessionDetailsOpen] = useState(false);
  const [mobileCallView, setMobileCallView] = useState("video");
  const [mobileDashboardView, setMobileDashboardView] = useState("search");

  useEffect(() => {
    setIsLocalVideoPrimary(false);
    setSessionDetailsOpen(false);
    setMobileCallView("video");
  }, [call?.roomId]);

  const callPeer = call?.peer || {};
  const callPeerName = callPeer.displayName || "Your match";
  const callPeerRole = callPeer.role || "Profession";
  const callPeerLookingFor = callPeer.lookingFor || "Open conversation";
  const callPeerExpertise = callPeer.expertise || "Not shared yet";
  const callPeerGoals = callPeer.goals || "Not shared yet";

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
          <div className={`topbar-actions ${call ? "in-call" : ""}`}>
          <div className="live-stats" aria-label="Live platform activity">
            <div className="live-stat">
              <Users size={16} />
              <span>{liveStats.onlineUsers}</span>
              <small>online</small>
            </div>
            <div className="live-stat waiting">
              <Search size={16} />
              <span>{liveStats.queuedUsers}</span>
              <small>waiting</small>
            </div>
          </div>
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

      <section
        className={`layout ${call ? "in-call" : ""} mobile-dashboard-${mobileDashboardView}`}
      >
        <form className="panel profile-panel" onSubmit={joinQueue}>
          <div className="panel-heading split-heading">
            <span className="panel-title-wrap">
              <UserRound size={19} />
              <h2>{mobileDashboardView === "search" ? "Search" : "Profile"}</h2>
            </span>
            {profileSavedAt && <small>{profileSavedAt}</small>}
          </div>

          {profileError && <p className="form-error">{profileError}</p>}

          <section className="mobile-profile-details" aria-label="Profile details">
            <div className="mobile-profile-identity">
              <div className="avatar-preview">
                {profile.profilePhoto ? (
                  <img src={profile.profilePhoto} alt="" />
                ) : (
                  <span>{profile.displayName?.charAt(0) || "S"}</span>
                )}
              </div>
              <div>
                <strong>{profile.displayName || "Your profile"}</strong>
                <span>{profile.role || "Profession"}</span>
              </div>
            </div>
            <dl className="mobile-profile-facts">
              <div>
                <dt>Looking for</dt>
                <dd>{profile.lookingFor || "Anyone/Random"}</dd>
              </div>
              <div>
                <dt>Company</dt>
                <dd>{profile.companyType || "Not shared"}</dd>
              </div>
              <div>
                <dt>Interests</dt>
                <dd>{profile.interests || profile.intent || "Not shared"}</dd>
              </div>
            </dl>
          </section>

          <section className="profile-filter-controls" aria-label="Search filters">
            <MatchingModeControl
              disabled={queueStatus.inQueue || Boolean(call)}
              value={matchingMode}
              onChange={setMatchingMode}
            />

            {matchingMode === "basic" && (
              <section className="basic-filter-card" aria-label="Basic filtering">
                <div className="random-match-badge">
                  <RefreshCcw size={18} />
                  <div>
                    <strong>Random matches</strong>
                    <span>Connect with anyone available now.</span>
                  </div>
                </div>
              </section>
            )}

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

            {matchingMode === "advanced" && (
              <>
                <MultiSelectChips
                  label="Your profession"
                  options={roles}
                  value={profile.role}
                  onChange={(value) => updateProfileField("role", value)}
                />
                <MultiSelectChips
                  label="Profession to connect with"
                  options={[...connectRoles, ANYONE_RANDOM]}
                  value={profile.lookingFor}
                  onChange={(value) => updateProfileField("lookingFor", value)}
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
              </>
            )}

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
                className="primary-button search-queue-button"
                disabled={
                  !canJoinQueue ||
                  queueStatus.inQueue ||
                  Boolean(call) ||
                  profileBusy
                }
              >
                <Search size={17} />
                <span>{queueStatus.inQueue ? "Searching" : "Search"}</span>
              </button>
            </div>

            <button
              className="quiet-button leave-queue-button"
              type="button"
              onClick={leaveQueue}
              disabled={!queueStatus.inQueue}
            >
              <X size={17} />
              <span>Stop</span>
            </button>
          </section>
        </form>

        <section className={`workspace mobile-dashboard-${mobileDashboardView}`}>
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
                <MatchingModeControl
                  compact
                  disabled={queueStatus.inQueue || Boolean(call)}
                  value={matchingMode}
                  onChange={setMatchingMode}
                />
                {matchingMode === "basic" && (
                  <section className="basic-filter-card mobile-basic-filter" aria-label="Basic filtering">
                    <div className="random-match-badge">
                      <RefreshCcw size={18} />
                      <div>
                        <strong>Random matches</strong>
                        <span>Basic mode connects with anyone available now.</span>
                      </div>
                    </div>
                  </section>
                )}
                <div className="queue-meter">
                  <span>{queueStatus.queueSize}</span>
                  <small>waiting</small>
                </div>
                <div className="mobile-queue-actions">
                  <button
                    className="primary-button search-queue-button"
                    type="button"
                    onClick={joinQueue}
                    disabled={
                      !canJoinQueue ||
                      queueStatus.inQueue ||
                      Boolean(call) ||
                      profileBusy
                    }
                  >
                    <Search size={17} />
                    <span>{queueStatus.inQueue ? "Searching" : "Search"}</span>
                  </button>
                  <button
                    className="quiet-button leave-queue-button"
                    type="button"
                    onClick={leaveQueue}
                    disabled={!queueStatus.inQueue}
                  >
                    <X size={17} />
                    <span>Stop</span>
                  </button>
                </div>
              </section>

              <section className="event-panel">
                <div className="panel-heading mobile-section-heading">
                  <Send size={18} />
                  <div>
                    <p className="eyebrow">Updates</p>
                    <h2>Activity</h2>
                  </div>
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
            <section className={`call-stage mobile-${mobileCallView}`}>
              <nav className="mobile-call-nav" aria-label="Call sections">
                {[
                  ["video", Video, "Video"],
                  ["info", UserRound, "Info"],
                ].map(([view, Icon, label]) => (
                  <button
                    className={mobileCallView === view ? "active" : ""}
                    key={view}
                    type="button"
                    onClick={() => setMobileCallView(view)}
                    aria-pressed={mobileCallView === view}
                  >
                    <Icon size={16} />
                    <span>{label}</span>
                  </button>
                ))}
              </nav>
              <section className="mobile-call-status" aria-label="Call status">
                <div
                  className={`call-timer mobile-status-timer ${callSecondsLeft <= 60 && !call.continueUntilDisconnected ? "ending-soon" : ""}`}
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
                {shouldShowContinuePrompt && (
                  <div className="mobile-status-expiry" role="status" aria-live="polite">
                    <span>{callSecondsLeft}s left</span>
                    <button
                      className="primary-button"
                      type="button"
                      onClick={continueCurrentCall}
                    >
                      <Video size={17} />
                      <span>Continue</span>
                    </button>
                  </div>
                )}
              </section>
              <div className="call-main">
                <div
                  className={`video-grid ${isLocalVideoPrimary ? "local-primary" : "remote-primary"}`}
                >
                  <button
                    className={`video-frame remote-video ${isLocalVideoPrimary ? "is-pip" : "is-primary"}`}
                    type="button"
                    onClick={() => setIsLocalVideoPrimary(false)}
                    aria-label={
                      isLocalVideoPrimary
                        ? `Show ${callPeerName} as the main video`
                        : `${callPeerName} is the main video`
                    }
                  >
                    <video ref={remoteVideoRef} autoPlay playsInline />
                    <span>{callPeerName}</span>
                  </button>
                  <button
                    className={`video-frame local-video ${isLocalVideoPrimary ? "is-primary" : "is-pip"}`}
                    type="button"
                    onClick={() => setIsLocalVideoPrimary(true)}
                    aria-label={
                      isLocalVideoPrimary
                        ? "You are the main video"
                        : "Show yourself as the main video"
                    }
                  >
                    <video ref={localVideoRef} autoPlay playsInline muted />
                    <span>You</span>
                  </button>
                  <div className="call-controls" aria-label="Call controls">
                    <button
                      className={`call-control-button ${audioEnabled ? "" : "is-off"}`}
                      type="button"
                      onClick={toggleLocalAudio}
                      aria-pressed={!audioEnabled}
                      aria-label={
                        audioEnabled
                          ? "Turn microphone off"
                          : "Turn microphone on"
                      }
                      title={audioEnabled ? "Mute microphone" : "Unmute microphone"}
                    >
                      {audioEnabled ? <Mic size={20} /> : <MicOff size={20} />}
                    </button>
                    <button
                      className={`call-control-button ${videoEnabled ? "" : "is-off"}`}
                      type="button"
                      onClick={toggleLocalVideo}
                      aria-pressed={!videoEnabled}
                      aria-label={
                        videoEnabled ? "Turn camera off" : "Turn camera on"
                      }
                      title={videoEnabled ? "Turn camera off" : "Turn camera on"}
                    >
                      {videoEnabled ? <Video size={20} /> : <VideoOff size={20} />}
                    </button>
                    <button
                      className="call-control-button end-call-control"
                      type="button"
                      onClick={endCurrentCall}
                      aria-label="End session"
                      title="End session"
                    >
                      <PhoneOff size={20} />
                    </button>
                  </div>
                </div>
                <section className="mobile-video-chat">
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
                </section>
              </div>

              <aside className="call-sidebar">
                <div className="call-sidebar-head">
                  <div className="session-menu">
                    <button
                      className="session-menu-button"
                      type="button"
                      onClick={() => setSessionDetailsOpen((open) => !open)}
                      aria-expanded={sessionDetailsOpen}
                    >
                      <span>
                        <small>In session</small>
                        <strong>{callPeerName}</strong>
                      </span>
                      <ChevronDown size={18} />
                    </button>
                    {sessionDetailsOpen && (
                      <div className="session-details-dropdown">
                        <p>{callPeerRole}</p>
                        <dl>
                          <div>
                            <dt>Looking for</dt>
                            <dd>{callPeerLookingFor}</dd>
                          </div>
                          <div>
                            <dt>Expertise</dt>
                            <dd>{callPeerExpertise}</dd>
                          </div>
                          <div>
                            <dt>Goal</dt>
                            <dd>{callPeerGoals}</dd>
                          </div>
                        </dl>
                      </div>
                    )}
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
                {shouldShowContinuePrompt && (
                  <section className="expiry-sidebar-alert" role="status" aria-live="polite">
                    <div className="expiry-sidebar-copy">
                      <p className="eyebrow">One minute left</p>
                      <h3>Continue this call?</h3>
                    </div>
                    <div className="expiry-sidebar-actions">
                      <div
                        className="countdown compact-countdown"
                        aria-label={`${callSecondsLeft} seconds left`}
                      >
                        <span>{callSecondsLeft}</span>
                        <small>sec</small>
                      </div>
                      <button
                        className="primary-button"
                        type="button"
                        onClick={continueCurrentCall}
                      >
                        <Video size={18} />
                        <span>Continue</span>
                      </button>
                      <button
                        className="danger-button"
                        type="button"
                        onClick={endCurrentCall}
                      >
                        <PhoneOff size={18} />
                        <span>End</span>
                      </button>
                    </div>
                  </section>
                )}
                <section className="mobile-session-card" aria-label="Session details">
                  <p>{callPeerRole}</p>
                  <dl>
                    <div>
                      <dt>Looking for</dt>
                      <dd>{callPeerLookingFor}</dd>
                    </div>
                    <div>
                      <dt>Expertise</dt>
                      <dd>{callPeerExpertise}</dd>
                    </div>
                    <div>
                      <dt>Goal</dt>
                      <dd>{callPeerGoals}</dd>
                    </div>
                  </dl>
                </section>
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

      {!call && (
        <nav className="mobile-app-nav" aria-label="Dashboard sections">
          {[
            ["profile", UserRound, "Profile"],
            ["search", Search, "Search"],
            ["activity", Send, "Activity"],
          ].map(([view, Icon, label]) => (
            <button
              className={mobileDashboardView === view ? "active" : ""}
              key={view}
              type="button"
              onClick={() => setMobileDashboardView(view)}
              aria-pressed={mobileDashboardView === view}
            >
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </nav>
      )}

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

function MatchingModeControl({ compact = false, disabled = false, onChange, value }) {
  return (
    <fieldset className={`match-mode-control ${compact ? "compact" : ""}`}>
      <legend>Filtering</legend>
      <div className="match-mode-options">
        {[
          ["basic", RefreshCcw, "Basic", "Random"],
          ["advanced", Settings, "Advanced", "Profile filters"],
        ].map(([mode, Icon, label, helper]) => (
          <button
            aria-pressed={value === mode}
            className={value === mode ? "active" : ""}
            disabled={disabled}
            key={mode}
            type="button"
            onClick={() => onChange(mode)}
          >
            <Icon size={16} />
            <span>
              <strong>{label}</strong>
              <small>{helper}</small>
            </span>
          </button>
        ))}
      </div>
    </fieldset>
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
            className={`filter-chip ${item === ANYONE_RANDOM ? "random-chip" : ""}`}
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
  const options = includeAnyone ? [...connectRoles, ANYONE_RANDOM] : roles;
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
                [
                  "profession-option",
                  role === ANYONE_RANDOM ? "random-option" : "",
                  isSelected ? "selected" : "",
                ]
                  .filter(Boolean)
                  .join(" ")
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
