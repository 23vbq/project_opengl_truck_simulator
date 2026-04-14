# 🚛 Scania V8 Simulator
 
> ETS3 in 1 night — vibecoded with GitHub Copilot Pro and Claude
 
A 3D truck driving simulator built from scratch in **Java + JOGL (OpenGL)** in immediate mode. Drive a detailed Scania tractor unit across a procedurally generated landscape with terrain following, real-time audio synthesis, dynamic weather, and a continuous day/night cycle.

<img width="1692" height="951" alt="image" src="https://github.com/user-attachments/assets/10b7e7a7-8e01-467b-a65b-cd4b208ef59a" />

---
 
## Features
 
- **Procedural terrain** — Perlin noise with multiple octaves and smoothing passes, height-based coloring (water / grass / rock / snow)
- **Terrain following** — bilinear interpolation under the truck, vehicle tilts with the slope in both pitch and roll
- **Detailed Scania model** — cabin, chassis, tandem rear axle (6 wheels), mirrors, exhaust pipe, DRL lights, brake/reverse lights, indicators, logo — built from GL2 primitives; optional GLTF model import
- **Curved road** — Catmull-Rom spline through the terrain, animated center lines, edge markings, wet surface overlay when raining
- **Trees** — randomly placed on terrain with no-spawn zone around the road, three-tier cone crowns with randomised green tint
- **Continuous day/night cycle** — smooth 2-minute cycle (dawn → day → sunset → night); sky colour, fog colour/density and sun position all interpolated continuously
- **Dynamic headlights** — spot light (`GL_LIGHT1`) toggled with `L`, intensity scales with time of night
- **Rain** — particle rain system (`GL_LINES`) with wet road overlay
- **Fog** — `glFog` depth fog, colour matched to time of day
- **Real-time V8 audio synthesis** — engine sound generated sample-by-sample: Scania cross-plane V8 firing pattern, turbo whistle, brake hiss, rain ambience, indicator tick
- **Realistic gearbox** — 6 forward / 3 reverse gears with individual gear ratios, max speeds, idle creep and launch traction per gear, RPM simulation
- **Tree collision** — truck bounces off tree trunks
- **HUD** — speed, RPM bar, gear indicator, controls overlay
 
---
 
## Controls
 
| Key | Action |
|---|---|
| `W` / `↑` | Throttle |
| `S` / `↓` | Brake |
| `A` / `←` | Turn left |
| `D` / `→` | Turn right |
| `Shift` | Gear up |
| `Ctrl` | Gear down |
| `R` | Toggle rain |
| `F` | Toggle fog |
| `C` | Toggle cabin / third-person camera |
| `L` | Toggle headlights |
| `M` | Toggle audio |
| `LMB drag` | Rotate camera |
 
---
 
## Tech stack
 
- Java 8 (JDK 8u202)
- [JOGAMP / JOGL](https://jogamp.org/) — OpenGL bindings for Java
- OpenGL 2 — immediate mode (`glBegin` / `glEnd`)
- `javax.sound.sampled` — real-time PCM audio synthesis
- Eclipse IDE

 ---
 
## Known Issues
 
- **No delta time in physics** — simulation speed is tied to frame rate. On slower machines everything (movement, gearbox, animations) runs proportionally slower than on a fast PC.

---
 
## Setup
 
1. Install **JDK 8u202**
2. Download [jogamp-all-platforms.7z](https://jogamp.org/deployment/archive/rc/)
3. In Eclipse: **Build Path → Add Library → User Library**, add `gluegen-rt.jar` and `jogl-all.jar` under **Classpath** (not Modulepath)
4. Set compiler compliance to **1.8** (**Project → Properties → Java Compiler**)
5. Move the `models` directory into the same directory as the compiled `.class` files (usually `bin/`)
6. Run `main.java`
