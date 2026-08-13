# Grid Hydrogen Sim

A native Android (Kotlin + Jetpack Compose) app that simulates the model from
*"Network-Constrained Co-Optimization of Green Hydrogen and Energy Storage in
Electric Power System Operation"* (Mohammad & Barua, 2026).

## What it does

- Solves the paper's 24-hour DC-power-flow LP (generation, renewables,
  storage, electrolyzers) **on-device**, with no external solver dependency —
  the simplex algorithm is implemented from scratch in Kotlin.
- Recovers nodal **locational marginal prices (LMPs)** as the dual variables
  of the nodal power-balance constraints, exactly as in the paper's Eq. (7).
- **Demo mode**: the paper's PJM 5-bus system, with Case 1-4 toggles
  (conventional → +renewables → +hydrogen → +storage) and a storage
  technology picker (battery / pumped hydro / CAES).
- **Custom mode**: edit generator costs/capacities, renewable capacity, the
  4-5 tie-line rating, peak demand, carbon price, and storage sizing, then
  re-solve.
- Results screen: summary metrics table (penetration, curtailment,
  emissions, cost, hydrogen output, average LMP) plus hourly dispatch, LMP,
  electrolyzer, storage SOC, and line-flow charts.

## Project → paper mapping

| Paper | Code |
|---|---|
| Eq. (1) objective | `LpBuilder.solve` — objective section |
| Eq. (2)-(3) gen capacity/ramp | `LpBuilder` — "Generator capacity + ramp" |
| Eq. (4) renewable availability | `LpBuilder` — "Renewable availability" |
| Eq. (5)-(6) DC flow + limits | `LpBuilder` — "DC line flow definition" |
| Eq. (7) nodal balance / LMP duals | `LpBuilder` — "Nodal power balance"; duals read out in `Simplex.Result.duals` |
| Eq. (8)-(10) hydrogen | `LpBuilder` — "Electrolyzer capacity + hydrogen accumulation" |
| Eq. (11)-(13) storage | `LpBuilder` — "Storage bounds + dynamics" |
| Table 1 generators, Table 2 storage | `model/PjmDefaults.kt` |

## Building

### Option A: Android Studio
1. Open this folder in Android Studio (Koala/2024.1 or newer recommended).
2. Let it sync Gradle (it will fetch/regenerate the wrapper automatically;
   if it doesn't, use **File → Sync Project with Gradle Files**, or point
   Android Studio at a local Gradle 8.7 install).
3. Run on a device/emulator with API 26+.

There's no `gradle-wrapper.jar` committed (binary files aren't practical to
hand-author) — Android Studio will fetch one on first sync, or you can run
`gradle wrapper` yourself if you have Gradle installed locally.

### Option B: GitHub Actions (no local Android Studio needed)
This repo includes `.github/workflows/build-apk.yml`, which builds a debug
APK on every push and uploads it as a workflow artifact.

1. Push this project to a GitHub repository (e.g. `git init && git add . &&
   git commit -m "init" && git remote add origin <your-repo-url> && git push
   -u origin main`).
2. Go to the repo's **Actions** tab — the "Build APK" workflow runs
   automatically (or trigger it manually via **Run workflow**).
3. Once it finishes, open the run and download the `GridHydrogenSim-debug-apk`
   artifact — it's a zip containing `app-debug.apk`.
4. Transfer that APK to an Android device (API 26+) and install it (you'll
   need to allow "install unknown apps" for whatever app you use to open it,
   since it isn't signed for a store).

This produces a **debug** build, which is fine for sideloading/testing.
Publishing to the Play Store would additionally require setting up a release
signing key, which isn't included here.

## Solver notes

The LP solver (`solver/Simplex.kt`) is a Big-M standard-form simplex with
shadow-price (dual) recovery. Free variables (bus angles, line flows) are
represented as the difference of two non-negative variables since the
solver requires `x >= 0` — standard LP practice.

Before writing the Kotlin/Android version, the exact same algorithm was
validated standalone in plain Java against hand-checked LPs, including the
classic two-bus congestion example (cheap generator capped by a congested
line vs. an expensive local generator), which reproduced the textbook LMP
separation (cheap bus priced at the cheap generator's marginal cost,
expensive bus priced at the expensive generator's cost) exactly.

For a 24-hour, 5-bus, multi-case problem the dense tableau can have on the
order of 700-900 variables/constraints; solving happens on a background
thread with a progress indicator since it can take a few seconds on-device.

## Known limitations / good next steps

- The paper does not publish its exact hourly wind/solar/load time series or
  line reactances, so `PjmDefaults.kt` uses representative 24-hour shapes
  calibrated to match the qualitative pattern in the paper's Fig. 3 (midday
  solar, evening-peaking wind and load). These are all editable.
- Only one storage technology is active at a time — the paper's hybrid
  configurations (PHB, CAESB) aren't modeled; adding a second `Storage`
  entry at the same bus would approximate a hybrid.
- No daily min/max hydrogen production bound (paper's M, M̄ in Eq. 10) is
  enforced — the paper doesn't give explicit values, so hydrogen output
  emerges freely from the optimization, matching how the paper's own
  headline numbers were produced.
- No tornado/sensitivity-sweep screen yet (Section 4.5 of the paper) — a
  natural extension would be a screen that re-runs `LpBuilder.solve` across
  a parameter grid and plots the swings.
- The dense-tableau simplex is simple and correct but not the fastest
  possible; for much larger custom networks a sparse revised-simplex
  implementation would scale better.
