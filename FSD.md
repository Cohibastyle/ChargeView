# Functional Specification Document
## ChargeView
Version 1.0 | Target device: Samsung Galaxy Z Fold7

## 1. Purpose

Build an Android application, delivered as an installable APK, that provides an easy to use **dashboard** for monitoring battery charging.

The app shall show live measurements, large gauge bars, charging history, and interactive graphs. Tapping any dashboard reading shall open its detailed view.

The interface shall adapt to portrait, landscape, folded, and unfolded use.

This document defines the required behaviour. Access to optional Samsung charging measurements must be verified on the target phone.

## 2. Main requirements

The app shall provide:

1. Live battery and charging readings.
2. Large, clear gauge bars showing each parameter’s relative level.
3. Dashboard cards that open detailed views when tapped.
4. Graphs with selectable linear or logarithmic time axes.
5. A detailed graph with selectable legend items to show or hide individual data series.
6. Charging session recording and history.
7. Recording while the screen is off, where permitted by the operating system.
8. Responsive portrait and landscape layouts.
9. Clear labels separating measured, calculated, estimated, and unavailable values.
10. Local operation without requiring an account or internet connection.

## 3. Scope

### 3.1 First release

| Area | Required functionality |
|---|---|
| Dashboard | Live values, gauge bars, session summary, graph preview |
| Parameter detail | Large reading, gauge, statistics, individual graph |
| Detailed graph | Multiple series, selectable legend, selectable linear or logarithmic time axis |
| Recording | Manual and automatic session detection, with optional background capture |
| History | App-recorded sessions and, where supported, existing device battery history |
| Export | CSV data export |
| Diagnostics | Available measurement fields, raw values, support status |
| Settings | Sampling interval, gauge ranges, recording preferences, display preferences |

### 3.2 Outside the first release

* Controlling charging voltage or current.
* Changing Samsung battery protection settings.
* Claiming accurate USB input power without a suitable source reading.
* Cloud synchronisation.
* Remote monitoring.
* Root access.
* Advertising or subscription billing.

## 4. Measurements and data quality

### 4.1 Supported parameters

| Parameter | Unit | Required behaviour |
|---|---|---|
| Battery state of charge | % | Display live percentage, history, and the change in percentage points over the preceding 60 seconds when sufficient valid readings exist |
| Battery current | mA | Show charging and discharging direction |
| Battery voltage | V | Label explicitly as battery voltage |
| Battery power (signed) | W | Calculate from battery voltage and signed current; positive while charging and negative while discharging |
| Battery temperature | °C | Show reading and history |
| Remaining battery charge | mAh | Display only when valid data is available |
| Published typical capacity | mAh | Reference value, not a live measurement |
| Full charge capacity | mAh | Display reported value if available; otherwise show unavailable |
| Estimated full charge capacity | mAh | Optional estimate, separately labelled |
| Reported charging voltage limit | V | Optional, separate from actual USB voltage |
| Reported charging current limit | mA | Optional, separate from actual USB current |
| Actual USB input voltage | V | Display only if a verified source exposes it |
| Actual USB input current | mA | Display only if a verified source exposes it |
| USB input power | W | Calculate only when suitable USB voltage and current readings exist |
| Battery health | % or status | Display only when available and clearly identify the meaning |

### 4.2 Measurement labels

Every reading shall have one of these classifications:

| Classification | Meaning |
|---|---|
| Measured | Obtained directly from a supported device reading |
| Calculated | Derived from measured values |
| Estimated | Derived using an estimation method |
| Reference | Published or user entered reference value |
| Unavailable | No usable source exists |
| Stale | The app has not received a sufficiently recent reading |

The detailed view shall show the classification, source, last update time, and any limitations.

### 4.3 Important distinctions

The app shall never:

* Label battery voltage as USB voltage.
* Label battery power as charger output power.
* Label a reported charging limit as actual USB voltage or current.
* Present the published battery capacity as measured full charge capacity.
* Replace a missing value with zero.

### 4.4 Current and power conventions

The application shall use a consistent display convention:

* Positive current and power: energy entering the battery.
* Negative current and power: energy leaving the battery.
* Zero: a valid reading of approximately no net flow.

Any device specific sign conversion shall be visible in diagnostics.

Battery power shall be calculated as:

\[
P_{\text{battery}}=\frac{I_{\text{battery,mA}}\times V_{\text{battery,V}}}{1000}
\]

## 5. Application structure

The app shall contain four main destinations:

1. Dashboard
2. Sessions
3. Diagnostics
4. Settings

The detailed graph shall be accessible from the dashboard and from any saved session.

Navigation position may change with available width:

* Narrow layout: bottom navigation.
* Wide layout: side navigation rail.

## 6. Dashboard

### 6.1 Header

The dashboard header shall show:

* Charging, discharging, full, or unknown status.
* Connection type, if available.
* Recording status and elapsed session time.
* Last update indicator.
* Start or stop recording control.

Charging, discharging, and recording status shall remain distinct. A phone can be charging or discharging without a session being recorded. The dashboard shall clearly identify when the battery is discharging and show battery power, current, and SoC change with their negative signs. A charging time-to-target estimate shall not be shown as if valid while discharging; the dashboard shall instead show an estimated time until the battery is empty (see 6.2.1).

### 6.2 Primary cards

The dashboard shall prioritize these readings:

1. Battery state of charge, including the change over the last 60 seconds.
2. Time to the configured charge target while charging; time until empty while discharging (see 6.2.1).
3. Signed battery power, with charging and discharging direction clear.
4. Signed SoC change per 10 minutes, positive while charging and negative while discharging.
5. Battery temperature.

#### 6.2.1 Time until empty

While the battery is discharging, the headline time estimate shall show the estimated time until the battery is empty, in the same position as the time-to-target estimate while charging.

* It shall be calculated from the recent rate of SoC decline (and recent battery power where available), and shall be labelled “Estimated”.
* It shall be shown as approximate (for example “~3 h 20 min left”) with a confidence indication or range, and may be updated as usage changes.
* Because usage varies (screen, apps, signal), the app shall explain that the estimate assumes recent usage continues.
* It shall show “Estimating…” or “Not enough recent data” until sufficient valid discharge readings exist; it shall not be extrapolated from a single instantaneous reading.
* “Empty” shall refer to 0% SoC unless a configurable low-battery threshold is set; the label shall state the threshold in use.
* While charging, the label shall revert to time to the charge target. If the SoC is stable (neither charging nor discharging significantly), the app shall show “Stable” rather than an estimate.
* The device’s own remaining-time prediction, if available, may be shown separately and labelled as device-provided.

Secondary cards shall include:

* Battery current.
* Battery voltage.
* Remaining charge.
* Full charge capacity or its availability status.
* USB measurements, where supported.

Users shall be able to reorder cards and hide optional cards.

### 6.3 Card contents

Each card shall include:

* Parameter name.
* Large numerical value.
* Unit.
* Large horizontal gauge bar.
* Range endpoints.
* Data quality indicator.
* A clear tap target for details.

The SoC display shall show the signed change over the preceding 60 seconds as percentage points, for example “+0.5% in the last minute” while charging or “−0.5% in the last minute” while discharging. It shall use valid readings spanning that interval, shall not extrapolate a shorter interval as a full-minute change, and shall show “Not enough recent data” when a reliable change cannot be calculated. This is distinct from the signed rolling 10-minute rate, which shall show the actual SoC percentage-point change over a rolling 10-minute window and remain unavailable until sufficient valid data exists.

Battery power and current shall follow the sign convention in section 4.4 throughout the dashboard, details, history, statistics, and graphs: positive means charging and negative means discharging. The display shall pair the sign with a clear direction label or status so direction is not communicated by color alone.

Example:

```text
Battery charging power                 Calculated

                  +12.4 W

[████████████████░░░░░░░░░░░░░░░░]

0 W                                      25 W

Tap for history and details
```

Example values are for interface illustration only.

### 6.4 Tap behaviour

Tapping a card shall open the relevant parameter detail screen.

The detail screen shall preserve:

* The selected session.
* The current live or historical mode.
* The relevant time selection.

Returning to the dashboard shall restore the previous screen state and selected session without requiring vertical scrolling.

### 6.5 Graph preview

The dashboard shall include a compact graph showing:

* Signed battery power by default.
* The selected linear or logarithmic elapsed time axis.
* The current session.
* A labelled Y axis with readable values and units.
* A clearly marked zero baseline, with positive charging values on one side and negative discharging values on the other.
* Touch inspection that displays the timestamp and value at the selected point.
* A control to open the detailed graph.

### 6.6 Session summary

A compact summary shall show:

* Session duration.
* Starting and current SoC.
* Average and peak charging power, and peak discharging power.
* Average and peak charging current, and peak discharging current.
* Highest battery temperature.
* Estimated net energy added.

Statistics shall identify whether they cover the full session or the currently selected interval.

## 7. Gauge bars

### 7.1 Appearance

Gauge bars shall be large enough to read at a glance. A thin progress line is not sufficient.

Each gauge shall show:

* Filled section.
* Current position.
* Minimum and maximum labels.
* Optional reference marker.
* Clear numeric value.
* Status text where relevant.

### 7.2 Gauge types

| Parameter | Gauge behaviour |
|---|---|
| SoC | Fixed 0 to 100% |
| Battery current | Centre zero, with discharge on one side and charge on the other |
| Battery power | Centre zero, with discharge and charge clearly separated |
| Temperature | Configurable display range |
| Battery voltage | Configurable display range |
| Remaining charge | Relative to a clearly identified capacity reference |
| Full charge capacity | Relative to the configured published capacity |
| USB input voltage | Configurable range, only when available |

### 7.3 Range handling

* Gauge ranges shall be configurable.
* Changing a gauge range shall not alter stored data.
* Out of range values shall remain visible numerically.
* An overflow indicator shall appear if a value exceeds the displayed range.
* Unavailable readings shall show an empty or neutral gauge, not a zero reading.
* Estimated values shall retain their estimate label.

Colour shall not be the only way to communicate a level or warning.

Temperature colours shall not imply a manufacturer approved safety limit unless such a limit is explicitly configured and sourced.

## 8. Parameter detail screen

Each parameter detail screen shall contain:

1. Parameter name and description.
2. Large live or selected value.
3. Large gauge.
4. Individual parameter graph.
5. Minimum, maximum, and time weighted average.
6. Latest reading timestamp.
7. Data source and classification.
8. Relevant explanation or limitation.

Controls shall include:

* Live view.
* Time range.
* Open in detailed graph.
* Export session data.

For unsupported parameters, the screen shall explain what is unavailable rather than showing an empty chart without context.

## 9. Graph requirements

### 9.1 Time axis

All time graphs shall use elapsed session time on a selectable linear or logarithmic X axis. Linear shall be the default for easier reading; the selected scale shall persist.

The axis shall represent:

\[
x=\text{elapsed time in seconds since session start}
\]

When logarithmic scale is selected, because zero cannot appear on a logarithmic axis:

* The visible logarithmic axis shall begin at one second.
* The session start reading shall appear as a separate start marker.
* Its original timestamp and elapsed time of zero shall remain unchanged in storage and exports.

Suggested tick positions include:

* 1 second.
* 10 seconds.
* 1 minute.
* 10 minutes.
* 1 hour.

Only ticks appropriate to the displayed interval shall appear.

When logarithmic scale is selected, an on-screen note shall explain: “Log time scale: early readings take more space.”

### 9.2 Time selection

The user shall be able to select:

* Last 5 minutes.
* Last 30 minutes.
* Last hour.
* Full session.
* Custom interval.

When a shorter interval is selected, the axis shall still use elapsed time from session start. It shall not silently reset the session clock.

### 9.3 Y axes

Y axes shall remain linear by default.

Different units shall not share an unlabelled numerical scale.

The detailed graph shall provide:

| Mode | Behaviour |
|---|---|
| Actual values | Up to two different unit groups overlaid, with clearly labelled left and right axes |
| Relative levels | Multiple parameters shown on a common 0 to 100% display scale |
| Separate panels | Multiple graphs stacked with a shared time axis |

Separate panels shall be the default when more than two different unit groups are enabled.

Relative mode tooltips shall still show the original measurement and unit. Relative scaling shall use the configured ranges and be labelled as a display comparison, not a physical equivalence.

Every graph shall show readable Y-axis tick values and the unit for each axis. Overlaid unit groups shall have separately labelled left and right axes; separate panels shall label each panel's Y axis.

For signed battery current, battery power, and SoC change, graphs shall make the zero baseline prominent and show negative discharging values distinctly below zero and positive charging values above zero. Direction shall also be identified by signs, labels, or line patterns and never by color alone.

### 9.4 Interaction

Graphs shall support:

* Pinch to zoom.
* Drag to pan.
* Tap or hold to inspect a reading.
* Touch and drag to move the inspection cursor across readings.
* Shared cursor across separate panels.
* Reset view.
* Return to live.
* Full screen mode.

Inspecting a point shall show:

* Timestamp.
* Elapsed time.
* Enabled parameter values.
* Units.
* Data quality.
* Whether the displayed point represents an aggregated group.

The selected point shall be clearly marked on the graph. Its values shall remain visible in a legible tooltip or value panel while the user touches or drags across the graph. A tap on the graph preview shall also reveal its selected value and timestamp.

Tooltips and legends for signed series shall retain the plus or minus sign and identify charging or discharging direction.

Panning away from the newest data shall pause automatic scrolling without stopping recording.

### 9.5 Missing and invalid readings

* Missing readings shall appear as gaps.
* Invalid readings shall not be plotted as zero.
* Long gaps shall not be connected as if continuous measurements existed.
* State changes such as charger connection and disconnection shall appear as event markers.

## 10. Detailed graph and selectable legend

### 10.1 Legend behaviour

Each available data series shall have a legend item containing:

* Colour or line pattern.
* Parameter name.
* Unit.
* Enabled or disabled state.

Tapping a legend item shall show or hide that entire data series and its points.

The user shall also have:

* Show all available series.
* Hide all series.
* Restore default selection.

Disabled legend items shall remain visible so they can be enabled again.

If all series are hidden, the graph shall display: “Select a parameter below to show its graph.”

### 10.2 Selection persistence

Legend selections shall persist when:

* Rotating the phone.
* Folding or unfolding the phone.
* Opening and closing the detail screen.
* Moving between live and saved sessions.

A parameter unavailable in the selected session shall be disabled with an explanation.

### 10.3 Individual samples

Showing or hiding a series shall not delete or modify recorded samples.

Individual sample exclusion or manual data editing is outside the first release.

## 11. Responsive layouts

Layout shall depend on available window width, not orientation alone.

Every primary destination and detail screen shall fit within the available app window without vertical scrolling at the standard display and font settings, on both the folded cover display and unfolded inner display. If a screen's content does not fit, the app shall simplify or reflow the layout, or divide content into focused subpages; it shall not clip content or require scrolling. History shall be browsable by date or page, and settings shall be grouped into compact categories. Essential controls and information shall remain reachable on the cover display.

### 11.1 Narrow portrait

Suitable for the cover screen or a narrow app window.

* Two primary cards per row where readable.
* One card per row where space is insufficient.
* Compact priority readings and a full width graph preview, with secondary readings in detail screens.
* Bottom navigation.
* Detailed graph with controls arranged within the available screen or split across focused subpages.

### 11.2 Wide portrait

Suitable for the unfolded phone.

* Three or four cards per row where readable.
* Larger graph preview.
* More session statistics visible without scrolling.
* Optional split view between a selected reading and its graph.

### 11.3 Landscape

* Compact header.
* Readings and gauges on one side.
* Large graph on the other.
* Legend beside or beneath the graph, based on available space.
* Controls arranged to maximise graph height.

### 11.4 Full screen graph

* Hide main navigation and nonessential summaries.
* Keep the legend accessible.
* Show a clear exit control.
* Preserve zoom, selected series, and inspected timestamp during rotation.

### 11.5 Fold and rotation continuity

The app shall work on both the folded cover screen and the unfolded inner display. When the phone is unfolded while the app is running, the layout shall adapt to the newly available window size without requiring the app to be restarted, closed, or relaunched.

Folding, unfolding, resizing, or rotating shall not:

* Stop recording.
* Create a duplicate session.
* Reset graph selections.
* Lose unsaved readings.
* Return the user to the dashboard unexpectedly.
* Lose the current screen, selected session, or relevant navigation state.

## 12. Recording and sessions

### 12.1 Sampling

Default sampling interval: 0.5 seconds.

Selectable intervals:

* 1 second.
* 2 seconds.
* 5 seconds.
* 10 seconds.

The interface shall distinguish the app’s sampling interval from the device sensor’s actual update rate.

Repeated identical values shall not be described as fresh sensor updates unless the source provides that information.

### 12.2 Session controls

Users shall be able to:

* Start recording manually.
* Stop recording manually.
* Enable automatic recording on charger connection.
* Continue a manual session through charging and discharging.
* Name a session.
* Add notes to a session.

Automatic session behaviour shall be configurable.

By default, charger disconnection shall end an automatically started charging session after a short configurable grace period, preventing brief cable interruptions from creating unnecessary sessions.

### 12.3 Background recording

Background recording shall be optional and independently configurable. When enabled, ChargeView shall be able to record state of charge (SoC), charging state, and supported charge-level measurements while the app is not visible, subject to Android and device restrictions. Users shall be able to enable or disable this behavior in settings.

The app shall request only the permissions needed for selected features.

When background recording is enabled:

* Show an ongoing recording notification where required.
* Provide a stop recording action.
* Explain any operating system restrictions affecting continuity.
* Record interruptions as gaps or events.

Closing the interface shall not be treated as a request to stop recording.

Recording shall not require the display to remain on.

### 12.4 Session history

The History destination shall distinguish between:

* **Recorded sessions:** samples collected and stored by ChargeView, including sessions recorded in the background when enabled.
* **Device battery history:** existing historical battery or charging records that Android or Samsung makes available to ChargeView through supported interfaces.

The History destination shall provide a way to check for or refresh device battery history where a supported interface is available. Availability and historical depth shall be validated on the target Fold7. If the device does not expose existing records, the app shall say so clearly and continue to show ChargeView-recorded sessions; it shall not imply that app recording can recover data from before recording was enabled.

Device-provided history shall be read-only and kept distinguishable from ChargeView-recorded data. The interface shall identify the source and any limits in coverage or detail. The app shall not invent samples or silently fill gaps in either source.

Any permission or special access required to retrieve device history shall be explained before the user is asked to grant it. Retrieved data shall remain local to the device.

Each saved session shall show:

* Date and start time.
* Duration.
* Starting and ending SoC.
* Peak battery charging power.
* Highest temperature.
* Notes, if present.

Users shall be able to open, rename, export, and delete sessions.

Deletion shall require confirmation.

## 13. Statistics and calculations

Statistics shall exclude invalid readings and account for actual time between samples.

| Statistic | Requirement |
|---|---|
| Minimum and maximum | Use valid recorded readings |
| Average current and power | Time weighted |
| Net battery energy | Integrate signed battery power over time |
| Positive charging energy | Integrate positive battery power only |
| SoC change | Ending percentage minus starting percentage |
| Session duration | Actual elapsed time |
| Missing data | Show coverage or gap information |

Energy calculations shall not bridge long missing intervals without identifying the resulting uncertainty.

Estimated full charge capacity shall not be calculated from one instantaneous current reading. Any capacity estimation feature shall describe its method and retain an estimate label.

## 14. Diagnostics

The diagnostics screen shall show:

* Device model.
* Android version.
* App version.
* Available battery fields.
* Raw values and units.
* Converted values.
* Current sign handling.
* Optional USB field availability.
* Last successful update.
* Unsupported or rejected readings.

Users shall be able to export a diagnostic report for troubleshooting.

This screen is required for validating which optional readings the Fold7 exposes.

## 15. Data storage and export

### 15.1 Storage

All sessions shall be stored locally.

Each sample shall include:

* Session identifier.
* Timestamp.
* Monotonic elapsed time.
* Parameter values.
* Validity and quality flags.
* Source classification.

Session metadata shall include gauge ranges, relevant settings, app version, and device information.

### 15.2 CSV export

CSV export shall include:

* Timestamp.
* Elapsed seconds.
* Each recorded parameter with its unit in the column name.
* Data quality indicators.
* Charging status.
* Connection events where applicable.

Missing values shall be blank or explicitly marked unavailable, never silently exported as zero.

Export shall preserve raw recorded resolution, regardless of graph zoom or display aggregation.

## 16. Settings

Settings shall include:

* Sampling interval.
* Automatic recording.
* Optional background and screen-off recording.
* Default graph series.
* Gauge ranges.
* Dashboard card order and visibility.
* Temperature display unit.
* Theme selection: Light, Dark, or Follow system. Dark mode shall be selectable independently of the device theme, and the selection shall persist between app launches.
* Optional keep screen awake while viewing.
* Storage use and session cleanup.

Both linear and logarithmic graph time scales shall be available. Linear shall be selected by default.

## 17. Usability and performance

* Main readings shall be readable at a glance.
* Each primary screen shall fit without vertical scrolling on the folded cover display and unfolded inner display at standard display and font settings.
* No essential action shall require a long press.
* Tap targets shall be at least 48 dp.
* Text shall respect system font scaling.
* Values shall always include units.
* Colour shall be supported by labels, signs, or line styles.
* Live readings should appear within two seconds of opening the dashboard when the operating system supplies data.
* Graphs shall remain responsive for long sessions using display aggregation.
* Aggregation shall preserve visible peaks and shall not change stored data.
* Recording overhead shall be measured during testing and documented, rather than assumed negligible.

## 18. Acceptance criteria

| Test | Pass condition |
|---|---|
| Dashboard | Live supported readings appear with units and large gauges |
| Last-minute SoC change | Dashboard shows valid SoC percentage-point change across the preceding 60 seconds, or clearly says when there is not enough recent data |
| Rolling 10-minute SoC change | Dashboard shows actual signed SoC percentage-point change over the rolling 10-minute window (negative while discharging) and does not extrapolate when the required data is unavailable |
| Discharging dashboard | When the battery is discharging, the dashboard says “Discharging,” shows negative battery power/current and negative SoC changes where available, and does not show a charging ETA; it shows a labelled estimated time until empty, or “Not enough recent data” when it cannot be reliably calculated |
| Card interaction | Tapping a card opens the matching parameter detail |
| Graph time scales | Linear is the default; logarithmic is selectable and handles the zero second sample correctly |
| Graph axes and inspection | Every graph shows labelled Y-axis values and units; touching or dragging on a graph identifies the selected point and displays its timestamp and measured values |
| Charging and discharging graphs | Signed power/current and SoC-change graphs show a prominent zero baseline, positive charging values above zero, negative discharging values below zero, and signed directional values on inspection |
| Legend | Each legend item independently shows or hides its series |
| Mixed units | No misleading shared numerical scale |
| Gauge overflow | Numeric reading remains visible with an overflow indicator |
| Unsupported USB values | Displayed as unavailable, not guessed |
| Capacity labels | Reference, reported, and estimated capacities remain distinct |
| Optional background recording | When enabled, supported SoC and charging data continue to be recorded while the app is not visible, where Android permits; interruptions are recorded and disabling the option stops background capture |
| Existing device history | Where supported, device-provided battery history is shown with its source and coverage; when unavailable, the app explains this and does not imply it can recover unrecorded history |
| Rotation | No lost session, reset selection, or duplicate recording |
| Fold and unfold | With the app running on the cover screen, unfolding updates the layout in place without restarting or relaunching the app and without losing the current screen, session, recording, graph selections, or unsaved readings |
| No-scroll layouts | Every primary destination and detail screen fits without vertical scrolling on both cover and unfolded displays at standard display and font settings; additional content is available through focused subpages |
| History | ChargeView-recorded sessions reopen with their data intact and remain distinguishable from any device-provided history |
| Export | CSV retains timestamps, units, valid values, and missing data |
| Long session | A 12 hour recording remains usable and exportable |
| Offline use | Core functions work without internet access |

## 19. Deliverables

1. Signed installable APK.
2. Complete source project and build instructions.
3. Finalised FSD reflecting implemented behaviour.
4. Fold7 compatibility report identifying supported and unavailable readings.
5. Test report covering portrait, landscape, folded, unfolded, and screen off recording.
6. Example CSV export and diagnostic report.

The first development milestone shall be a measurement validation build. Its purpose is to confirm the Fold7’s available readings before the full dashboard and graph interface are completed.