import { useState, useEffect, useRef, useCallback, createContext, useContext, useMemo } from "react";

// ── Theme System ──────────────────────────────────────────────────────────────

const THEMES = {
  dark: {
    bg: "#1a1a1e",
    surface: "#232328",
    border: "#35353d",
    borderActive: "#55555f",
    text: "#b8b8c0",
    textDim: "#6e6e7a",
    textBright: "#e4e4ea",
    fiber: "#6baaff",
    fiberWaiting: "#f5a623",
    fiberPassed: "#34d399",
    fiberBroken: "#f87171",
    barrier: "#f5a623",
    barrierOpen: "#34d399",
    barrierBroken: "#f87171",
    action: "#c4b5fd",
    accent: "#6baaff",
    red: "#f87171",
    gold: "#f5a623",
    green: "#34d399",
    purple: "#c4b5fd",
    svgBg: "#1e1e23",
    fiberFill: "25",
    glowOpacity: 0.18,
    laneOpacity: 0.35,
    btnBg: "#2a2a30",
  },
  light: {
    bg: "#f7f7f8",
    surface: "#ffffff",
    border: "#dcdce0",
    borderActive: "#a0a0b0",
    text: "#4a4a55",
    textDim: "#8a8a98",
    textBright: "#1a1a22",
    fiber: "#2b7de9",
    fiberWaiting: "#d98e0a",
    fiberPassed: "#1da85e",
    fiberBroken: "#d43b2e",
    barrier: "#d98e0a",
    barrierOpen: "#1da85e",
    barrierBroken: "#d43b2e",
    action: "#9654d8",
    accent: "#2b7de9",
    red: "#d43b2e",
    gold: "#d98e0a",
    green: "#1da85e",
    purple: "#9654d8",
    svgBg: "#f0f0f3",
    fiberFill: "18",
    glowOpacity: 0.10,
    laneOpacity: 0.45,
    btnBg: "#ffffff",
  },
};

const ThemeContext = createContext(THEMES.dark);

function useSystemTheme() {
  const [isDark, setIsDark] = useState(() => {
    // Try media query first
    if (typeof window !== "undefined" && window.matchMedia) {
      if (window.matchMedia("(prefers-color-scheme: dark)").matches) return true;
    }
    return false;
  });

  useEffect(() => {
    if (typeof window === "undefined") return;

    // Strategy 1: media query listener
    const mq = window.matchMedia?.("(prefers-color-scheme: dark)");
    const mqHandler = (e) => setIsDark(e.matches);
    mq?.addEventListener?.("change", mqHandler);

    // Strategy 2: detect actual background luminance of the container
    // This catches cases where the host sets dark mode without prefers-color-scheme
    function detectFromBackground() {
      const el = document.documentElement;
      const bg = getComputedStyle(el).backgroundColor;
      if (!bg || bg === "transparent" || bg === "rgba(0, 0, 0, 0)") {
        // Check body as fallback
        const bodyBg = getComputedStyle(document.body).backgroundColor;
        if (!bodyBg || bodyBg === "transparent" || bodyBg === "rgba(0, 0, 0, 0)") {
          // No detectable background, rely on media query
          if (mq) setIsDark(mq.matches);
          return;
        }
        setIsDark(isColorDark(bodyBg));
        return;
      }
      setIsDark(isColorDark(bg));
    }

    function isColorDark(colorStr) {
      const m = colorStr.match(/\d+/g);
      if (!m || m.length < 3) return false;
      const [r, g, b] = m.map(Number);
      // Relative luminance
      const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
      return luminance < 0.45;
    }

    // Check on mount
    detectFromBackground();

    // Observe class/attribute changes on <html> and <body> that signal a theme switch
    const observer = new MutationObserver(detectFromBackground);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "data-theme", "style"] });
    observer.observe(document.body, { attributes: true, attributeFilter: ["class", "data-theme", "style"] });

    return () => {
      mq?.removeEventListener?.("change", mqHandler);
      observer.disconnect();
    };
  }, []);

  return isDark;
}

function useTheme(mode) {
  const isDark = useSystemTheme();
  return useMemo(() => {
    if (mode === "light") return THEMES.light;
    if (mode === "dark") return THEMES.dark;
    return isDark ? THEMES.dark : THEMES.light;
  }, [mode, isDark]);
}

const FONT = "'JetBrains Mono', 'Fira Code', 'SF Mono', monospace";

// ── Utilities ─────────────────────────────────────────────────────────────────

const easeOut = (t) => 1 - Math.pow(1 - t, 3);
const easeInOut = (t) =>
  t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

function useTick(running) {
  const [tick, setTick] = useState(0);
  const intervalRef = useRef(null);
  useEffect(() => {
    if (!running) return;
    const prefersReduced =
      typeof window !== "undefined" &&
      window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches;
    if (prefersReduced) return;
    const step = () => {
      if (!document.hidden) setTick((t) => t + 1);
    };
    intervalRef.current = setInterval(step, 33); // ~30fps
    return () => clearInterval(intervalRef.current);
  }, [running]);
  return tick;
}

// ── State ─────────────────────────────────────────────────────────────────────

function createFiber(id) {
  return { id, state: "idle", x: 0, targetX: 0, progress: 0, arrivalOrder: -1 };
}

function createBarrierState(parties) {
  return {
    fibers: Array.from({ length: parties }, (_, i) => createFiber(i)),
    phase: "waiting",
    waitingCount: 0,
    cycle: 0,
    barrierOpen: 0,
    actionProgress: 0,
    log: [
      {
        text: `CyclicBarrier created with ${parties} parties`,
        colorKey: "textDim",
        time: Date.now(),
      },
    ],
  };
}

// ── Theme Toggle ──────────────────────────────────────────────────────────────



// ── Button ────────────────────────────────────────────────────────────────────

function Button({ children, onClick, disabled, variant = "default", small }) {
  const C = useContext(ThemeContext);
  const baseStyle = {
    fontFamily: FONT,
    fontSize: small ? 11 : 12,
    padding: small ? "4px 10px" : "7px 16px",
    borderRadius: 6,
    border: "1px solid",
    cursor: disabled ? "not-allowed" : "pointer",
    opacity: disabled ? 0.4 : 1,
    transition: "all 0.15s ease",
    letterSpacing: "0.02em",
    fontWeight: 500,
  };
  const variants = {
    default: { background: C.btnBg, borderColor: C.border, color: C.text },
    primary: {
      background: `${C.accent}22`,
      borderColor: C.accent,
      color: C.accent,
    },
    danger: {
      background: `${C.red}18`,
      borderColor: `${C.red}88`,
      color: C.red,
    },
    success: {
      background: `${C.green}18`,
      borderColor: `${C.green}88`,
      color: C.green,
    },
    purple: {
      background: `${C.purple}18`,
      borderColor: `${C.purple}88`,
      color: C.purple,
    },
  };
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{ ...baseStyle, ...variants[variant] }}
    >
      {children}
    </button>
  );
}

// ── SVG Components ────────────────────────────────────────────────────────────

function FiberSVG({ fiber, x, y, radius }) {
  const C = useContext(ThemeContext);
  const stateColors = {
    idle: C.fiber,
    approaching: C.fiber,
    waiting: C.fiberWaiting,
    action: C.purple,
    passing: C.fiberPassed,
    done: C.fiberPassed,
    broken: C.fiberBroken,
  };
  const color = stateColors[fiber.state] || C.fiber;
  const pulse =
    fiber.state === "waiting"
      ? Math.sin(Date.now() / 400 + fiber.id) * 0.15 + 1
      : 1;
  const glow =
    fiber.state === "action" ? 12 : fiber.state === "waiting" ? 6 : 0;
  return (
    <g>
      {glow > 0 && (
        <circle
          cx={x}
          cy={y}
          r={radius + glow}
          fill={color}
          opacity={C.glowOpacity}
        />
      )}
      <circle
        cx={x}
        cy={y}
        r={radius * pulse}
        fill={`${color}${C.fiberFill}`}
        stroke={color}
        strokeWidth={2}
      />
      <text
        x={x}
        y={y + 1}
        textAnchor="middle"
        dominantBaseline="central"
        fill={color}
        fontSize={11}
        fontFamily={FONT}
        fontWeight={600}
      >
        F{fiber.id + 1}
      </text>
      <text
        x={x}
        y={y + radius + 14}
        textAnchor="middle"
        fill={C.textDim}
        fontSize={9}
        fontFamily={FONT}
      >
        {fiber.state === "idle" ? "" : fiber.state}
      </text>
    </g>
  );
}

function BarrierWall({ x, y1, y2, state, progress }) {
  const C = useContext(ThemeContext);
  const color =
    state === "broken"
      ? C.barrierBroken
      : state === "opening"
        ? C.barrierOpen
        : C.barrier;
  const gapSize = state === "opening" ? progress * (y2 - y1) * 0.5 : 0;
  const midY = (y1 + y2) / 2;
  const broken = state === "broken";
  return (
    <g>
      {/* Glow */}
      <line x1={x} y1={y1} x2={x} y2={midY - gapSize} stroke={color} strokeWidth={8} opacity={C.glowOpacity - 0.03} strokeLinecap="round" />
      <line x1={x} y1={midY + gapSize} x2={x} y2={y2} stroke={color} strokeWidth={8} opacity={C.glowOpacity - 0.03} strokeLinecap="round" />
      {/* Main */}
      <line x1={x} y1={y1} x2={x} y2={midY - gapSize} stroke={color} strokeWidth={3} strokeDasharray={broken ? "6 4" : "none"} strokeLinecap="round" opacity={broken ? 0.5 : 1} />
      <line x1={x} y1={midY + gapSize} x2={x} y2={y2} stroke={color} strokeWidth={3} strokeDasharray={broken ? "6 4" : "none"} strokeLinecap="round" opacity={broken ? 0.5 : 1} />
      <text x={x} y={y1 - 10} textAnchor="middle" fill={color} fontSize={10} fontFamily={FONT} fontWeight={600} letterSpacing="0.08em">
        BARRIER
      </text>
    </g>
  );
}

// ── Main Component ────────────────────────────────────────────────────────────

export default function CyclicBarrierDiagram() {
  const C = useTheme("auto");
  const [parties, setParties] = useState(4);
  const [state, setState] = useState(() => createBarrierState(4));
  const tick = useTick(true);
  const logEndRef = useRef(null);

  const addLog = useCallback((text, colorKey = "text") => {
    setState((s) => ({
      ...s,
      log: [...s.log.slice(-30), { text, colorKey, time: Date.now() }],
    }));
  }, []);

  useEffect(() => {
    if (logEndRef.current)
      logEndRef.current.scrollTop = logEndRef.current.scrollHeight;
  }, [state.log.length]);

  const canAwait =
    state.phase !== "broken" &&
    state.phase !== "action" &&
    state.phase !== "opening" &&
    state.fibers.some((f) => f.state === "idle");

  // .await — sends the next idle fiber to call await on the barrier
  const callAwait = useCallback(() => {
    setState((prev) => {
      const idx = prev.fibers.findIndex((f) => f.state === "idle");
      if (idx === -1 || prev.phase === "broken") return prev;
      const newFibers = prev.fibers.map((f, i) =>
        i !== idx
          ? f
          : {
              ...f,
              state: "approaching",
              progress: 0,
              arrivalOrder: prev.fibers.filter((f) => f.state !== "idle").length,
            }
      );
      return { ...prev, fibers: newFibers };
    });
  }, []);

  // .reset — resets the barrier to initial state, breaks any waiting party
  const callReset = useCallback(() => {
    setState((prev) => {
      const hadWaiting = prev.waitingCount > 0;
      if (hadWaiting) {
        // Break waiting fibers first, then reset after animation
        const newFibers = prev.fibers.map((f) =>
          f.state === "waiting" || f.state === "action"
            ? { ...f, state: "broken" }
            : f
        );
        return {
          ...prev,
          fibers: newFibers,
          phase: "broken",
          log: [
            ...prev.log,
            {
              text: `.reset \u2014 broke ${prev.waitingCount} waiting fiber${prev.waitingCount > 1 ? "s" : ""}`,
              colorKey: "red",
              time: Date.now(),
            },
            {
              text: "Barrier resetting…",
              colorKey: "accent",
              time: Date.now(),
            },
          ],
        };
      }
      return createBarrierState(parties);
    });
  }, [parties]);

  // Auto-reset after broken state from .reset (give time to see the break)
  const brokenTimer = useRef(null);
  useEffect(() => {
    if (state.phase === "broken") {
      clearTimeout(brokenTimer.current);
      brokenTimer.current = setTimeout(() => {
        setState((prev) => {
          if (prev.phase !== "broken") return prev;
          const fresh = createBarrierState(parties);
          return {
            ...fresh,
            cycle: prev.cycle + 1,
            log: [
              ...prev.log,
              {
                text: `Cycle ${prev.cycle + 2} ready`,
                colorKey: "accent",
                time: Date.now(),
              },
            ],
          };
        });
      }, 1200);
    }
    return () => clearTimeout(brokenTimer.current);
  }, [state.phase, parties]);

  // ── Animation loop ────────────────────────────────────────────────────────
  useEffect(() => {
    setState((prev) => {
      let fibers = [...prev.fibers];
      let phase = prev.phase;
      let waitingCount = prev.waitingCount;
      let cycle = prev.cycle;
      let barrierOpen = prev.barrierOpen;
      let actionProgress = prev.actionProgress;
      let changed = false;
      let newLogs = [];

      fibers = fibers.map((f) => {
        if (f.state === "approaching") {
          const np = Math.min(f.progress + 0.035, 1);
          if (np >= 1) {
            newLogs.push({
              text: `Fiber ${f.id + 1} \u2192 await (waiting: ${waitingCount + 1}/${parties})`,
              colorKey: "gold",
            });
            waitingCount++;
            changed = true;
            return { ...f, state: "waiting", progress: 1 };
          }
          changed = true;
          return { ...f, progress: np };
        }
        return f;
      });

      if (waitingCount === parties && phase === "waiting") {
        phase = "action";
        actionProgress = 0;
        changed = true;
        newLogs.push({
          text: `All ${parties} fibers arrived \u2014 running barrier action`,
          colorKey: "purple",
        });
        fibers = fibers.map((f) =>
          f.state === "waiting" ? { ...f, state: "action" } : f
        );
      }

      if (phase === "action") {
        actionProgress = Math.min(actionProgress + 0.02, 1);
        changed = true;
        if (actionProgress >= 1) {
          phase = "opening";
          barrierOpen = 0;
          newLogs.push({
            text: "Action complete \u2014 barrier opening",
            colorKey: "green",
          });
          fibers = fibers.map((f) =>
            f.state === "action" ? { ...f, state: "passing", progress: 0 } : f
          );
        }
      }

      if (phase === "opening") {
        barrierOpen = Math.min(barrierOpen + 0.025, 1);
        changed = true;
        fibers = fibers.map((f) => {
          if (f.state === "passing") {
            const np = Math.min(f.progress + 0.02, 1);
            return np >= 1 ? { ...f, state: "done", progress: 1 } : { ...f, progress: np };
          }
          return f;
        });
        if (
          fibers.every(
            (f) =>
              f.state === "done" || f.state === "idle" || f.state === "broken"
          ) &&
          barrierOpen >= 1
        ) {
          phase = "resetting";
          changed = true;
        }
      }

      if (phase === "resetting") {
        barrierOpen = Math.max(barrierOpen - 0.04, 0);
        changed = true;
        if (barrierOpen <= 0) {
          const newCycle = cycle + 1;
          newLogs.push({
            text: `Barrier reset \u2014 cycle ${newCycle + 1} ready`,
            colorKey: "accent",
          });
          return {
            ...prev,
            fibers: Array.from({ length: parties }, (_, i) => createFiber(i)),
            phase: "waiting",
            waitingCount: 0,
            cycle: newCycle,
            barrierOpen: 0,
            actionProgress: 0,
            log: [
              ...prev.log,
              ...newLogs.map((l) => ({ ...l, time: Date.now() })),
            ],
          };
        }
      }

      if (!changed && newLogs.length === 0) return prev;

      return {
        ...prev,
        fibers,
        phase,
        waitingCount,
        cycle,
        barrierOpen,
        actionProgress,
        log:
          newLogs.length > 0
            ? [...prev.log, ...newLogs.map((l) => ({ ...l, time: Date.now() }))]
            : prev.log,
      };
    });
  }, [tick, parties]);

  // ── SVG layout ────────────────────────────────────────────────────────────
  const svgW = 700;
  const svgH = 320;
  const barrierX = svgW * 0.55;
  const fiberStartX = 60;
  const fiberEndX = svgW - 60;
  const fiberAreaTop = 50;
  const fiberAreaBot = svgH - 30;
  const fiberSpacing = Math.min(55, (fiberAreaBot - fiberAreaTop) / parties);
  const fiberR = 18;

  const getFiberY = (idx) => fiberAreaTop + 20 + idx * fiberSpacing;
  const getFiberX = (fiber) => {
    if (fiber.state === "idle") return fiberStartX;
    if (fiber.state === "approaching")
      return (
        fiberStartX +
        (barrierX - fiberR * 2 - fiberStartX) * easeOut(fiber.progress)
      );
    if (fiber.state === "waiting" || fiber.state === "action")
      return barrierX - fiberR * 2;
    if (fiber.state === "passing" || fiber.state === "done") {
      const waitX = barrierX - fiberR * 2;
      return waitX + (fiberEndX - waitX) * easeInOut(fiber.progress);
    }
    if (fiber.state === "broken") return barrierX - fiberR * 2;
    return fiberStartX;
  };

  const remainingForFiber = (fiber) => {
    if (
      fiber.state === "waiting" ||
      fiber.state === "action" ||
      fiber.state === "passing" ||
      fiber.state === "done"
    )
      return parties - (fiber.arrivalOrder + 1);
    return -1;
  };

  const resolveColor = (colorKey) => C[colorKey] || C.text;

  return (
    <ThemeContext.Provider value={C}>
      <div
        style={{
          background: "transparent",
          padding: "24px 16px",
          fontFamily: FONT,
          color: C.text,
          boxSizing: "border-box",
          transition: "background 0.3s ease, color 0.3s ease",
        }}
      >
        {/* Header */}
        <div style={{ maxWidth: 740, margin: "0 auto 20px" }}>
          <div
            style={{
              display: "flex",
              alignItems: "baseline",
              gap: 12,
              marginBottom: 4,
            }}
          >
            <span
              style={{
                fontSize: 11,
                fontWeight: 600,
                letterSpacing: "0.12em",
                color: C.accent,
                textTransform: "uppercase",
              }}
            >
              zio.concurrent
            </span>
            <span style={{ color: C.textDim, fontSize: 11 }}>·</span>
            <span style={{ color: C.textDim, fontSize: 11 }}>
              Cycle {state.cycle + 1}
            </span>
          </div>
          <h1
            style={{
              fontSize: 26,
              fontWeight: 700,
              color: C.textBright,
              margin: 0,
              letterSpacing: "-0.02em",
            }}
          >
            CyclicBarrier
          </h1>
          <p
            style={{
              fontSize: 12,
              color: C.textDim,
              margin: "6px 0 0",
              lineHeight: 1.6,
              maxWidth: 560,
            }}
          >
            A synchronization primitive where <em>N</em> fibers all wait for
            each other at a common barrier point. When the last fiber arrives,
            an optional action runs, then all are released. The barrier resets
            automatically for reuse.
          </p>
        </div>

        {/* Main viz */}
        <div
          style={{
            maxWidth: 740,
            margin: "0 auto",
            background: C.surface,
            border: `1px solid ${C.border}`,
            borderRadius: 10,
            overflow: "hidden",
            transition: "background 0.3s ease, border-color 0.3s ease",
          }}
        >
          <svg
            viewBox={`0 0 ${svgW} ${svgH}`}
            width="100%"
            role="img"
            aria-labelledby="cyclic-barrier-svg-title"
            style={{ display: "block" }}
          >
            <title id="cyclic-barrier-svg-title">
              CyclicBarrier animation: {state.fibers.length} fibers, phase {state.phase}, {state.waitingCount} waiting
            </title>
            <rect
              x={0}
              y={0}
              width={svgW}
              height={svgH}
              fill={C.svgBg}
              rx={0}
            />

            {/* Zone labels */}
            <text
              x={fiberStartX}
              y={28}
              fill={C.textDim}
              fontSize={9}
              fontFamily={FONT}
              textAnchor="middle"
              letterSpacing="0.08em"
            >
              FIBERS
            </text>
            <text
              x={fiberEndX}
              y={28}
              fill={C.textDim}
              fontSize={9}
              fontFamily={FONT}
              textAnchor="middle"
              letterSpacing="0.08em"
            >
              RELEASED
            </text>

            {/* Approach lane lines */}
            {state.fibers.map((f, i) => (
              <line
                key={`lane-${i}`}
                x1={fiberStartX}
                y1={getFiberY(i)}
                x2={barrierX - fiberR * 2}
                y2={getFiberY(i)}
                stroke={C.border}
                strokeWidth={0.5}
                strokeDasharray="3 6"
                opacity={C.laneOpacity}
              />
            ))}

            {/* Exit lane lines */}
            {state.fibers.map((f, i) => (
              <line
                key={`exit-${i}`}
                x1={barrierX + 10}
                y1={getFiberY(i)}
                x2={fiberEndX}
                y2={getFiberY(i)}
                stroke={C.border}
                strokeWidth={0.5}
                strokeDasharray="3 6"
                opacity={C.laneOpacity - 0.1}
              />
            ))}

            {/* Barrier wall */}
            <BarrierWall
              x={barrierX}
              y1={fiberAreaTop - 10}
              y2={fiberAreaTop + parties * fiberSpacing + 10}
              state={
                state.phase === "broken"
                  ? "broken"
                  : state.phase === "opening" ||
                      state.phase === "resetting"
                    ? "opening"
                    : "closed"
              }
              progress={state.barrierOpen}
            />

            {/* Action indicator */}
            {state.phase === "action" && (
              <g>
                <rect
                  x={barrierX - 50}
                  y={fiberAreaTop - 25}
                  width={100 * state.actionProgress}
                  height={4}
                  rx={2}
                  fill={C.purple}
                  opacity={0.8}
                />
                <rect
                  x={barrierX - 50}
                  y={fiberAreaTop - 25}
                  width={100}
                  height={4}
                  rx={2}
                  fill="none"
                  stroke={C.purple}
                  strokeWidth={0.5}
                  opacity={0.3}
                />
                <text
                  x={barrierX}
                  y={fiberAreaTop - 32}
                  textAnchor="middle"
                  fill={C.purple}
                  fontSize={9}
                  fontFamily={FONT}
                  fontWeight={600}
                >
                  ACTION
                </text>
              </g>
            )}

            {/* Waiting counter */}
            <text
              x={barrierX + 30}
              y={fiberAreaTop + parties * fiberSpacing + 30}
              textAnchor="start"
              fill={C.textDim}
              fontSize={10}
              fontFamily={FONT}
            >
              waiting:{" "}
              <tspan
                fill={
                  state.waitingCount === parties
                    ? C.green
                    : state.waitingCount > 0
                      ? C.gold
                      : C.textDim
                }
                fontWeight={600}
              >
                {state.waitingCount}
              </tspan>
              /{parties}
            </text>

            {/* Fibers */}
            {state.fibers.map((f, i) => (
              <FiberSVG
                key={f.id}
                fiber={f}
                x={getFiberX(f)}
                y={getFiberY(i)}
                radius={fiberR}
              />
            ))}

            {/* Remaining count badges */}
            {state.fibers.map((f, i) => {
              const r = remainingForFiber(f);
              if (r < 0 || f.state === "passing" || f.state === "done")
                return null;
              return (
                <text
                  key={`rem-${i}`}
                  x={getFiberX(f) + fiberR + 8}
                  y={getFiberY(i) - 6}
                  fill={C.textDim}
                  fontSize={8}
                  fontFamily={FONT}
                >
                  returns {r}
                </text>
              );
            })}
          </svg>

          {/* Controls — public API */}
          <div
            style={{
              padding: "14px 20px",
              borderTop: `1px solid ${C.border}`,
              display: "flex",
              flexWrap: "wrap",
              gap: 10,
              alignItems: "center",
            }}
          >
            {/* .await */}
            <Button variant="primary" onClick={callAwait} disabled={!canAwait}>
              .await
            </Button>

            {/* .reset */}
            <Button
              onClick={callReset}
              variant="danger"
              disabled={state.phase === "action" || state.phase === "opening" || state.phase === "resetting"}
            >
              .reset
            </Button>

            {/* Separator */}
            <div style={{ width: 1, height: 24, background: C.border, margin: "0 4px" }} />

            {/* .isBroken readout */}
            <div style={{
              display: "flex", alignItems: "center", gap: 6,
              fontFamily: FONT, fontSize: 11,
            }}>
              <span style={{ color: C.textDim }}>.isBroken</span>
              <span style={{
                padding: "2px 8px", borderRadius: 4,
                fontSize: 10, fontWeight: 600,
                background: state.phase === "broken" ? `${C.red}20` : `${C.green}15`,
                color: state.phase === "broken" ? C.red : C.green,
                border: `1px solid ${state.phase === "broken" ? `${C.red}40` : `${C.green}30`}`,
              }}>
                {state.phase === "broken" ? "true" : "false"}
              </span>
            </div>

            {/* .waiting readout */}
            <div style={{
              display: "flex", alignItems: "center", gap: 6,
              fontFamily: FONT, fontSize: 11,
            }}>
              <span style={{ color: C.textDim }}>.waiting</span>
              <span style={{
                padding: "2px 8px", borderRadius: 4,
                fontSize: 10, fontWeight: 600,
                background: state.waitingCount > 0 ? `${C.gold}20` : `${C.border}40`,
                color: state.waitingCount > 0 ? C.gold : C.textDim,
                border: `1px solid ${state.waitingCount > 0 ? `${C.gold}40` : C.border}`,
              }}>
                {state.waitingCount}
              </span>
            </div>

            {/* make(n) — party selector */}
            <div
              style={{
                marginLeft: "auto",
                display: "flex",
                alignItems: "center",
                gap: 8,
              }}
            >
              <span style={{ fontSize: 10, color: C.textDim }}>make(</span>
              {[3, 4, 5, 6].map((n) => (
                <button
                  key={n}
                  aria-label={`Set parties to ${n}`}
                  onClick={() => {
                    setParties(n);
                    setState(createBarrierState(n));
                    addLog(`CyclicBarrier.make(${n})`, "accent");
                  }}
                  style={{
                    fontFamily: FONT,
                    fontSize: 11,
                    width: 28,
                    height: 28,
                    borderRadius: 6,
                    border: `1px solid ${n === parties ? C.accent : C.border}`,
                    background:
                      n === parties ? `${C.accent}22` : "transparent",
                    color: n === parties ? C.accent : C.textDim,
                    cursor: "pointer",
                    transition: "all 0.15s ease",
                  }}
                >
                  {n}
                </button>
              ))}
              <span style={{ fontSize: 10, color: C.textDim }}>)</span>
            </div>
          </div>
        </div>

        {/* Legend + Log */}
        <div
          style={{
            maxWidth: 740,
            margin: "16px auto 0",
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: 12,
          }}
        >
          {/* Legend */}
          <div
            style={{
              background: C.surface,
              border: `1px solid ${C.border}`,
              borderRadius: 10,
              padding: "14px 18px",
              transition: "background 0.3s ease, border-color 0.3s ease",
            }}
          >
            <div
              style={{
                fontSize: 10,
                fontWeight: 600,
                color: C.textDim,
                letterSpacing: "0.08em",
                marginBottom: 10,
                textTransform: "uppercase",
              }}
            >
              Fiber States
            </div>
            {[
              { color: C.fiber, label: "idle", desc: "Not yet called await" },
              {
                color: C.fiber,
                label: "approaching",
                desc: "Calling await, acquiring permit",
              },
              {
                color: C.gold,
                label: "waiting",
                desc: "At barrier, waiting for others",
              },
              {
                color: C.purple,
                label: "action",
                desc: "Barrier action executing",
              },
              {
                color: C.green,
                label: "passing / done",
                desc: "Released, barrier opened",
              },
              {
                color: C.red,
                label: "broken",
                desc: "Barrier broken by interrupt",
              },
            ].map(({ color, label, desc }) => (
              <div
                key={label}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  marginBottom: 6,
                }}
              >
                <div
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: "50%",
                    background: `${color}40`,
                    border: `1.5px solid ${color}`,
                    flexShrink: 0,
                  }}
                />
                <span
                  style={{ fontSize: 10, color: C.textBright, minWidth: 80 }}
                >
                  {label}
                </span>
                <span style={{ fontSize: 10, color: C.textDim }}>{desc}</span>
              </div>
            ))}
          </div>

          {/* Event log */}
          <div
            style={{
              background: C.surface,
              border: `1px solid ${C.border}`,
              borderRadius: 10,
              padding: "14px 18px",
              maxHeight: 200,
              display: "flex",
              flexDirection: "column",
              transition: "background 0.3s ease, border-color 0.3s ease",
            }}
          >
            <div
              style={{
                fontSize: 10,
                fontWeight: 600,
                color: C.textDim,
                letterSpacing: "0.08em",
                marginBottom: 10,
                textTransform: "uppercase",
              }}
            >
              Event Log
            </div>
            <div
              ref={logEndRef}
              style={{ flex: 1, overflowY: "auto", fontSize: 10, lineHeight: 1.8 }}
            >
              {state.log.map((entry, i) => (
                <div
                  key={`${entry.time}-${entry.text}`}
                  style={{
                    color: resolveColor(entry.colorKey),
                    opacity: i === state.log.length - 1 ? 1 : 0.7,
                  }}
                >
                  <span style={{ color: C.textDim, marginRight: 6 }}>›</span>
                  {entry.text}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* How it works */}
        <div
          style={{
            maxWidth: 740,
            margin: "16px auto 0",
            background: C.surface,
            border: `1px solid ${C.border}`,
            borderRadius: 10,
            padding: "18px 20px",
            transition: "background 0.3s ease, border-color 0.3s ease",
          }}
        >
          <div
            style={{
              fontSize: 10,
              fontWeight: 600,
              color: C.textDim,
              letterSpacing: "0.08em",
              marginBottom: 12,
              textTransform: "uppercase",
            }}
          >
            How CyclicBarrier Works
          </div>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(4, 1fr)",
              gap: 14,
              fontSize: 11,
              lineHeight: 1.6,
            }}
          >
            {[
              {
                n: "1",
                title: "Fibers Arrive",
                desc: "Each fiber calls await and blocks at the barrier. A semaphore serializes access, incrementing the waiting count.",
                color: C.accent,
              },
              {
                n: "2",
                title: "Last Triggers Action",
                desc: "When waiting count equals parties, the optional action runs. The barrier won't open until the action completes.",
                color: C.purple,
              },
              {
                n: "3",
                title: "Barrier Opens",
                desc: "The shared Promise completes with success, unblocking all waiting fibers. Each receives its remaining count.",
                color: C.green,
              },
              {
                n: "4",
                title: "Cyclic Reset",
                desc: "A new Promise is allocated, counters reset to zero, and the barrier is ready for the next cycle of awaits.",
                color: C.gold,
              },
            ].map(({ n, title, desc, color }) => (
              <div key={n}>
                <div
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: "50%",
                    border: `1.5px solid ${color}`,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: 10,
                    color,
                    fontWeight: 700,
                    marginBottom: 8,
                  }}
                >
                  {n}
                </div>
                <div
                  style={{
                    fontWeight: 600,
                    color: C.textBright,
                    marginBottom: 4,
                  }}
                >
                  {title}
                </div>
                <div style={{ color: C.textDim, fontSize: 10 }}>{desc}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </ThemeContext.Provider>
  );
}
