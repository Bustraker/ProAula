# Modernization Plan: Map UI Quality Polish

**Project**: ProAula

## Technical Framework

- **Language**: Java 25 with browser JavaScript
- **Framework**: Spring Boot 3.5.16 and Thymeleaf
- **Build Tool**: Maven
- **Frontend assets**: HTML templates, CSS, JavaScript, and Leaflet

## Overview

Polish the existing public and private route-planning map experiences without changing application source during this planning phase. Work is limited to the reported public-map JavaScript style warnings, verified Font Awesome resource integrity, and the private map presentation. Existing route behavior and map features remain intact; barrio text overlays stay removed wherever that behavior is shared.

## Scope and Boundaries

- Target the public map assets in `aula/src/main/resources/templates/Usuario/viajar_public.html`, `aula/src/main/resources/static/css/viajar_public.css`, and `aula/src/main/resources/static/js/viajar_public.js`. Keep the matching script under `aula/scripts/` synchronized only if it is the maintained source for the served asset.
- Target the private map view in `aula/src/main/resources/templates/Usuario/viajar.html` and `aula/src/main/resources/static/css/viajar.css`; touch `viajar.js` or `common.js` only when required to preserve or repair an existing interaction.
- Do not change controllers, endpoint contracts, authentication/authorization, unrelated pages, or unrelated code style.
- Do not add a guessed SRI digest. Use a digest verified against the exact selected CDN resource, or choose an established project-conventional asset delivery option if it cannot be verified.
- Do not restore barrio text overlays on the map.

## Planned Tasks

1. **Resolve public-map JavaScript style warnings**: identify the warnings produced by the existing JavaScript check after the recent public map polish, fix only those warnings, and preserve runtime behavior and any maintained source/served-asset synchronization.
2. **Secure the Font Awesome resource reference**: verify the exact Font Awesome CDN URL/version and resource bytes; configure a matching integrity value and `crossorigin` only from a trustworthy published/verified source, or use a project-conventional alternative. Confirm icons still load.
3. **Polish the private route/map view**: bring its visual quality and responsive behavior up to the public map's standard while retaining origin/destination selection, location detection, route results, stop visibility control, route details, CSRF metadata, and existing navigation/route behavior. Keep barrio text overlays absent if shared behavior controls them.

## Validation Gates and Results

| Gate | Acceptance check | Planning result |
|---|---|---|
| Public JavaScript style | Run the same targeted JavaScript lint/style check that reported the warnings; the touched public-map files have no remaining reported warnings and unrelated files are unchanged. | Pending implementation; not run during planning. |
| CDN integrity | Verify the selected URL/version and its served bytes against an authoritative published digest; ensure `integrity` and `crossorigin` match. If no trustworthy digest is available, verify the chosen project-conventional alternative loads the same required icons. | Pending implementation; no hash has been invented. |
| Map behavior and routes | Exercise public and authenticated private route views: select origin/destination, view route results/details, toggle intermediate stops, and test location detection when permission is available. Confirm existing URLs/route behavior and that map barrio text overlays remain absent. | Pending implementation. |
| Responsive visual quality | Inspect both map views at desktop and narrow/mobile viewport sizes; confirm controls remain usable and readable without obscuring the map or overlapping. | Pending implementation. |
| Planning artifacts | Parse `.metadata/tasks.json`, check required task structure, and confirm this plan contains only the three requested task scopes. | Passed: JSON parsing, three unique transform task IDs, Java/ProAula metadata, and required success criteria verified. |

## Open Questions & Questionnaire

None. The task scope and constraints were specified in the request; no infrastructure, deployment, or integration-test scope was requested.