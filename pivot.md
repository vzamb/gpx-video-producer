# Product Requirements Document (PRD)
## Telemetry Story Generator for Endurance Athletes

### 1. Executive Summary
**Current State:** The application is structured as a traditional multi-track video editor. It requires users to manually drag, drop, and align GPX telemetry overlays onto video clips using a complex timeline.
**The Problem:** The target audience consists of ultra-endurance athletes (runners, cyclists, hikers). After a grueling 5-to-10-hour activity, users suffer from extreme physical and mental fatigue. Navigating a complex timeline on a small smartphone screen to manually synchronize a 50km GPX track with fragmented video clips is frustrating and time-consuming.
**The Vision:** The product is pivoting from a "Manual Video Editor" to an "Automated Story Generator." The app will leverage file metadata and proportional algorithms to automatically synchronize space-time data. This reduces video creation from minutes of tedious editing to a seamless, one-click process.

### 2. Core Concept: Dual-Engine Auto-Sync
To provide maximum storytelling flexibility and ensure technical stability, the application's core rendering engine must natively support two distinct synchronization approaches. Users can toggle between these modes based on their creative intent or technical constraints.

### 2.1 Mode A: Documentary Mode 
This mode synchronizes data based on absolute real-world time.

*   **How it works:** The engine extracts the Exif timestamp (`creation_time` or `datetime_original`) from the imported video files. It then parses the `.gpx` file to read the `<time>` tags within each trackpoint (`<trkpt>`). The app matches the video's exact timestamp with the corresponding GPX timestamp.
*   **Visual Effect:** Telemetry data updates in real-time within a single clip but jumps instantly at the exact moment of a scene cut. If Clip 1 was recorded at km 12 and Clip 2 at km 40, the on-screen distance instantly snaps from 12 to 40 when the scene changes.
*   **Primary Use Case:** Vlogs, daily multi-clip recaps, and authentic storytelling where viewers need to see the athlete's exact pace and heart rate at specific moments of exhaustion or triumph.

### 2.2 Mode B: Hyper-Lapse Mode 
This mode synchronizes data based on relative, proportional time.

*   **How it works:** The engine ignores the video's original Exif timestamp. Instead, it maps the entire duration of the GPX track (e.g., from 0 to 50 km) proportionally across the total duration of the exported video (e.g., 15 seconds).
*   **Visual Effect:** Telemetry numbers spin rapidly like a slot machine. The elevation chart draws itself continuously from start to finish as the short video plays.
*   **Primary Use Case:** Cinematic summaries, such as mapping a full 10-hour activity over a single 15-second slow-motion drone shot or a continuous hyper-lapse.
*   **Crucial Technical Fallback:** This mode acts as the automatic fallback if the imported video lacks Exif creation data. Videos downloaded from WhatsApp or social media platforms routinely have metadata stripped, making Mode A impossible. Mode B ensures the app never crashes and always delivers a usable output.

### 3. UI/UX Requirements
The interface must remove the cognitive load of video editing and mimic premium sports broadcasting aesthetics.

*   **Hide the Timeline:** Remove the complex multi-track timeline from the primary user view. Users should not adjust clip lengths or overlay durations manually. The engine handles alignment automatically.
*   **Glassmorphism Design:** Telemetry data must not be raw text placed over the video, as it blends into bright backgrounds. Data should be housed within semi-transparent, blurred cards (opacity 40-50%) to ensure legibility without fully blocking the footage.
*   **Athletic Typography:** Replace standard Android fonts with bold, condensed, or monospaced fonts (e.g., Oswald, Roboto Condensed) that evoke digital sports watches.
*   **Visual Hierarchy:** Establish a "Hero Metric" (e.g., Distance) that dominates the screen. Secondary metrics (Pace, Elevation Gain, HR) should be significantly smaller and grouped together.
*   **Progressive Elevation Chart:** The elevation profile should display as a static, semi-transparent background outline. A brightly colored "fill" or glowing dot progresses along the path, dynamically matching either the timestamp (Mode A) or the proportional video length (Mode B).

### 4. One-Swipe Templates 
Instead of allowing users to manually place individual data points, the app will provide pre-configured, uneditable aesthetic layouts.

*   **Cinematic:** Minimalist, small data cards nestled in the bottom left corner.
*   **Hero:** Massive distance tracking centered on the screen, optimized for short vertical videos.
*   **Pro Dashboard:** A vertical side-panel displaying comprehensive metrics alongside a 2D minimal map trace.

### 5. The User Flow
The new user journey must be friction-free and achievable in under 30 seconds.

1.  **Import:** User selects video clips from the gallery and imports the day's GPX file.
2.  **Select Sync Mode:** A simple toggle prompts the user to choose "Real-Time Sync" (Mode A) or "Journey Summary" (Mode B).
3.  **App Processing:** The app calculates the overlay behavior in the background without exposing the timeline.
4.  **Swipe to Style:** The user swipes left or right to preview the pre-configured templates (Cinematic, Hero, Pro).
5.  **Export:** The user taps export, generating a ready-to-share video with burned-in telemetry.

Sources
