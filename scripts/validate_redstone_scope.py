#!/usr/bin/env python3
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/projectred"
DATA = ROOT / "src/main/resources/data/projectred"

COLORS = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
    "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
]

WIRES = {
    "red_alloy_wire",
    "neutral_bundled_wire",
    "framed_red_alloy_wire",
    "neutral_framed_bundled_wire",
}
WIRES |= {f"{c}_insulated_wire" for c in COLORS}
WIRES |= {f"{c}_bundled_wire" for c in COLORS}
WIRES |= {f"{c}_framed_insulated_wire" for c in COLORS}
WIRES |= {f"{c}_framed_bundled_wire" for c in COLORS}

GATES = {
    "or_gate", "nor_gate", "not_gate", "and_gate", "nand_gate", "xor_gate",
    "xnor_gate", "buffer_gate", "multiplexer_gate", "pulse_gate", "repeater_gate",
    "randomizer_gate", "sr_latch_gate", "toggle_latch_gate",
    "transparent_latch_gate", "light_sensor_gate", "rain_sensor_gate", "timer_gate",
    "sequencer_gate", "counter_gate", "state_cell_gate", "synchronizer_gate",
    "bus_transceiver_gate", "null_cell_gate", "invert_cell_gate", "buffer_cell_gate",
    "comparator_gate", "and_cell_gate", "bus_randomizer_gate", "bus_converter_gate",
    "bus_input_panel_gate", "stacking_latch_gate", "segment_display_gate",
    "dec_randomizer_gate",
}

CORE = {
    "red_ingot", "plate", "conductive_plate", "wired_plate", "bundled_plate",
    "platformed_plate", "anode", "cathode", "pointer", "silicon_chip",
    "energized_silicon_chip", "sand_coal_comp", "red_iron_comp", "boule",
    "silicon", "red_silicon_comp", "glow_silicon_comp", "infused_silicon",
    "energized_silicon",
}
CORE |= {f"{c}_illumar" for c in COLORS}

FORBIDDEN = (
    "low_load", "power_wire", "generator", "battery", "charging_bench",
    "inductive_furnace", "block_breaker", "item_importer", "block_placer",
    "filtered_importer", "fire_starter", "teleposer", "frame_motor",
    "frame_actuator", "project_bench", "auto_crafter", "router", "transport",
    "exploration", "illumination", "fabrication", "ic_gate", "ic_workbench",
    "ic_printer", "solar_panel",
)

errors = []

# Parse every JSON resource/data file.
for path in list(ASSETS.rglob("*.json")) + list(DATA.rglob("*.json")):
    try:
        json.loads(path.read_text())
    except Exception as exc:
        errors.append(f"invalid JSON: {path.relative_to(ROOT)}: {exc}")

# Validate ProjectRed item model references and texture references. This catches
# client-only missing-model/missing-texture failures that a dedicated server
# cannot see.
for item_path in (ASSETS / "items").glob("*.json"):
    try:
        item_data = json.loads(item_path.read_text())
    except Exception:
        continue

    model = item_data.get("model", {}).get("model")
    if isinstance(model, str) and model.startswith("projectred:"):
        model_path = ASSETS / "models" / (model.split(":", 1)[1] + ".json")
        if not model_path.is_file():
            errors.append(
                f"missing ProjectRed model {model} referenced by "
                f"{item_path.relative_to(ROOT)}"
            )

for model_path in (ASSETS / "models").rglob("*.json"):
    try:
        model_data = json.loads(model_path.read_text())
    except Exception:
        continue

    for texture in model_data.get("textures", {}).values():
        if not isinstance(texture, str) or not texture.startswith("projectred:"):
            continue
        texture_path = ASSETS / "textures" / (texture.split(":", 1)[1] + ".png")
        if not texture_path.is_file():
            errors.append(
                f"missing ProjectRed texture {texture} referenced by "
                f"{model_path.relative_to(ROOT)}"
            )

# All resource paths must be legal Minecraft identifiers.
legal_path = re.compile(r"^[a-z0-9/._-]+$")
for base in (ASSETS, DATA):
    for path in base.rglob("*"):
        if path.is_file():
            relative = path.relative_to(base).as_posix()
            if not legal_path.fullmatch(relative):
                errors.append(f"illegal resource path: {path.relative_to(ROOT)}")

item_dir = ASSETS / "items"
item_ids = {p.stem for p in item_dir.glob("*.json")}
expected_items = WIRES | GATES | CORE | {"screwdriver"}
missing = expected_items - item_ids
unexpected = item_ids - expected_items
if missing:
    errors.append("missing item definitions: " + ", ".join(sorted(missing)))
if unexpected:
    errors.append("unexpected item definitions: " + ", ".join(sorted(unexpected)))

for item_id in WIRES | GATES | CORE | {"screwdriver"}:
    if not (ASSETS / "models/item" / f"{item_id}.json").is_file():
        errors.append(f"missing item model: {item_id}")

# GateType must contain exactly the 34 modern ProjectRed Integration IDs.
gate_source = (ROOT / "src/main/java/dev/xt9y/projectred/integration/GateType.java").read_text()
actual_gates = set(re.findall(r'\("[a-z0-9_]+"\)', gate_source))
actual_gates = {x[2:-2] for x in actual_gates}
if actual_gates != GATES:
    errors.append(
        "GateType IDs differ: missing="
        + repr(sorted(GATES - actual_gates))
        + " extra="
        + repr(sorted(actual_gates - GATES))
    )

wire_source = (ROOT / "src/main/java/dev/xt9y/projectred/transmission/WireSpec.java").read_text()
if "WireFamily.POWER" in wire_source or "low_load" in wire_source:
    errors.append("power-wire implementation leaked into WireSpec")
if "DyeColor.values()" not in wire_source:
    errors.append("WireSpec no longer generates all 16 dye variants")

# Fail on forbidden content paths/names.
for path in ROOT.rglob("*"):
    if not path.is_file() or ".git" in path.parts or "build" in path.parts:
        continue
    lower = str(path.relative_to(ROOT)).lower()
    if any(token in lower for token in FORBIDDEN):
        errors.append(f"forbidden non-redstone content path: {lower}")

if len(WIRES) != 68:
    errors.append(f"validator wire manifest broken: {len(WIRES)} != 68")
if len(GATES) != 34:
    errors.append(f"validator gate manifest broken: {len(GATES)} != 34")

if errors:
    print("\n".join(f"ERROR: {e}" for e in errors), file=sys.stderr)
    raise SystemExit(1)

print(f"validated {len(WIRES)} wires, {len(GATES)} gates, {len(CORE)} core crafting items")
