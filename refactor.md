# Product Requirements Document (PRD)
## Architectural Pivot: 2-Screen "Smart Sync" Flow

### 1. Executive Summary & Pivot Rationale
The current application utilizes a rigid 4-step wizard and limits user creativity by removing standard video editing capabilities. To serve endurance athletes who are physically exhausted but still want to create engaging, narrative-driven social content, the app will pivot to a streamlined 2-screen architecture. 

This new flow combines the flexibility of a linear video editor (Screen 1) with the magic of automated telemetry overlays (Screen 2). By utilizing the principle of progressive disclosure, the app provides instant gratification with a default "Journey Summary" mode, while hiding advanced manual "Spatial Sync" tools until the user explicitly requests them.

### 2. Global Interface Elements
These actions must be accessible across both primary screens, positioned in the Top Bar, to ensure users can adapt their workspace without interrupting their flow.

#### Aspect Ratio Selector
A persistent toggle (e.g., `[ 9:16 ˅ ]`) allows the user to switch the canvas format (9:16, 16:9, 4:5, 1:1) at any time. When triggered, the video canvas resizes instantly, and the telemetry overlay layouts automatically dynamically reposition themselves to fit the new boundaries.

#### Activity Details (Info)
An information button (e.g., `[ i ]` or `[ Stats ]`) that, when tapped, triggers a bottom sheet displaying the overall statistics of the loaded GPX track (Total Distance, Total Elevation Gain, Total Time). 

### 3. Screen 1: Video Assembly ("The Cut")
This screen acts as a simplified, single-track linear video editor, similar to native TikTok or Instagram Reels editors. Users focus entirely on visual storytelling without worrying about data overlays yet.

#### Video Editing Capabilities
Users can import multiple clips onto a single timeline. Supported actions include trimming (adjusting the start/end of a clip), reordering clips via drag-and-drop, and inserting basic transitions (e.g., crossfade) between clips.

#### The Primary Call to Action
A prominent, floating or distinct button labeled **"+ Add Activity"** (or "+ GPX") serves as the bridge to the next phase. Tapping this prompts the user to upload a GPX file or manually enter activity data. Once the GPX is loaded, the app automatically transitions the user to Screen 2.

### 4. Screen 2: Style & Telemetry ("The Magic")
This screen acts as the "Director's Booth." Upon entering, the user immediately sees their edited video playing with telemetry overlays applied perfectly. The system defaults to the frictionless "Journey Summary" mode.

#### Aesthetic Customization
Users can swipe horizontally across the screen to cycle through predefined, uneditable layouts (e.g., *Hero*, *Pro Dashboard*, *Cinematic*). Tapping on specific text elements (like the Activity Title) allows for quick renaming, and a small color palette allows users to change the overlay's accent color.

#### The Sync Toggle
Located prominently below the video player, a two-state toggle dictates the rendering engine's logic: `[ ✓ Summary ]  [ Live ]`. 
By default, "Summary" is selected. The app proportionally maps the entire GPX track (0 to Max Km) across the total duration of the edited video, drawing the elevation chart continuously. This guarantees a crash-proof, visually stunning result even if the source videos lack metadata (e.g., downloaded from WhatsApp).

### 5. The Spatial Alignment Interface ("Live" Mode)
If the user switches the toggle to "Live", they demand exact, real-time data matching the specific moment the video was recorded. This triggers the Spatial Alignment Interface via a full-screen modal or expansive bottom sheet.

#### The Interactive Elevation Chart
The interface splits: the top half loops the currently selected video clip, while the bottom half displays the entire GPX elevation profile from start to finish. A draggable vertical pin sits on the elevation chart, acting as a spatial locator.

#### Visual Synchronization
As the user drags the pin horizontally across the mountain profile, large data readouts above the pin update in real-time (e.g., *Km 15.2 - Elev 800m*). The user visually matches the terrain in their video to the elevation chart (e.g., placing the pin at the peak of a climb). 

#### Metadata Automation vs. Manual Override
If the imported video contains original Exif creation timestamps, the app pre-places the pin at the exact correct location on the chart. The user merely confirms it. If metadata is missing, the pin defaults to the start, forcing the user to drag it to the visually correct spatial location. Once confirmed, the modal closes, and the overlay on Screen 2 now reflects real-time jump-cuts between clips.

Sources
