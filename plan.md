# LAN/SMB Implementation Plan

## Goal
Add SMB-based LAN storage connections (mobile <-> laptop on same Wi-Fi) like Solid File Explorer, with multiple saved connections and full add/edit/delete flow in the existing navigation drawer.

## Requirements Mapping
- Add new `LAN/SMB` section directly below `Internal Storage` in drawer.
- Support multiple SMB connection profiles.
- Provide actions to `Add`, `Edit`, `Delete` profiles.
- New profile appears in drawer immediately after save.
- Offer nearby machine discovery with IP display.
- Support authentication modes: `Guest` and `Username/Password`.
- Prefer SMB2/SMB3 only (disable SMB1 negotiation).

## Architecture

### 1) Data + Persistence
- Create `SmbConnectionConfig` model:
  - `id`, `displayName`, `host`, `port`, `shareName` (optional), `authMode`, `username`, `password`, `domain`, `lastConnectedAt`.
- Create `AuthMode` enum: `GUEST`, `USERNAME_PASSWORD`.
- Create `SmbConnectionsManager` in `util`:
  - `getConnections(context)`, `saveConnection(context, config)`, `deleteConnection(context, id)`, `getConnection(context, id)`.
  - Store as SharedPreferences string set using compact safe serialization.

### 2) Nearby Discovery
- Create `LanDiscoveryManager`:
  - Resolve local IPv4 subnet.
  - Probe host port `445` with short timeout.
  - Return discovered hosts with `ip` + reverse DNS hostname (if available).
  - Keep manual host/IP input as fallback.

### 3) SMB Access Layer
- Create `SmbClientManager`:
  - Build SMBJ client and enforce SMB2/SMB3 negotiation only.
  - Connect + authenticate using selected mode.
  - APIs for:
    - `listShares(config)`
    - `listDirectory(config, shareName, path)`

### 4) UI/UX
- Add drawer section under internal storage:
  - Header: `LAN / SMB`
  - `Add SMB Connection` action item.
  - Dynamic list of saved SMB connections.
  - Optional per-item overflow menu for edit/delete.
- Add add/edit dialog:
  - Fields: display name, host/IP, port, auth mode, username, password, domain.
  - Nearby scan button with result picker.
- Add SMB browser screen:
  - Show share list first, then folder/file listing for selected share/path.
  - Breadcrumb/path at top.

### 5) Navigation Integration
- Extend `Screen` enum with SMB browser route.
- From drawer, tapping SMB profile opens SMB browser for that profile.
- Keep existing internal storage and collection navigation untouched.

### 6) Validation + Docs
- Build and compile check after implementation.
- Update `README.md` with LAN/SMB capability and constraints.

## Implementation Order
1. Add dependency and models/managers.
2. Implement SMB access + discovery utilities.
3. Add SMB browser screen.
4. Integrate drawer + add/edit/delete dialog flows.
5. Wire activity navigation.
6. Run compile/test and update README.

