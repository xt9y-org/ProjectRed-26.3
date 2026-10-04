package dev.xt9y.projectred.integration;

public enum GateType {
    OR("or_gate"),
    NOR("nor_gate"),
    NOT("not_gate"),
    AND("and_gate"),
    NAND("nand_gate"),
    XOR("xor_gate"),
    XNOR("xnor_gate"),
    BUFFER("buffer_gate"),
    MULTIPLEXER("multiplexer"),
    PULSE("pulse_former"),
    REPEATER("repeater"),
    RANDOMIZER("randomizer"),
    SR_LATCH("rs_latch"),
    TOGGLE_LATCH("toggle_latch"),
    TRANSPARENT_LATCH("transparent_latch"),
    LIGHT_SENSOR("light_sensor"),
    RAIN_SENSOR("rain_sensor"),
    TIMER("timer"),
    SEQUENCER("sequencer"),
    COUNTER("counter"),
    STATE_CELL("state_cell"),
    SYNCHRONIZER("synchronizer"),
    BUS_TRANSCEIVER("bus_transceiver"),
    NULL_CELL("null_cell"),
    INVERT_CELL("invert_cell"),
    BUFFER_CELL("buffer_cell"),
    COMPARATOR("projectred_comparator"),
    AND_CELL("and_cell"),
    BUS_RANDOMIZER("bus_randomizer"),
    BUS_CONVERTER("bus_converter"),
    BUS_INPUT_PANEL("bus_input_panel"),
    TRANSPARENT_LATCH_CELL("stacking_latch"),
    SEGMENT_DISPLAY("segment_display"),
    DEC_RANDOMIZER("dec_randomizer");

    private final String id;

    GateType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GateType byId(String id) {
        for (GateType type : values()) if (type.id.equals(id)) return type;
        return null;
    }
}
