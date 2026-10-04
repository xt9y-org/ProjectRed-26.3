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
    MULTIPLEXER("multiplexer_gate"),
    PULSE("pulse_gate"),
    REPEATER("repeater_gate"),
    RANDOMIZER("randomizer_gate"),
    SR_LATCH("sr_latch_gate"),
    TOGGLE_LATCH("toggle_latch_gate"),
    TRANSPARENT_LATCH("transparent_latch_gate"),
    LIGHT_SENSOR("light_sensor_gate"),
    RAIN_SENSOR("rain_sensor_gate"),
    TIMER("timer_gate"),
    SEQUENCER("sequencer_gate"),
    COUNTER("counter_gate"),
    STATE_CELL("state_cell_gate"),
    SYNCHRONIZER("synchronizer_gate"),
    BUS_TRANSCEIVER("bus_transceiver_gate"),
    NULL_CELL("null_cell_gate"),
    INVERT_CELL("invert_cell_gate"),
    BUFFER_CELL("buffer_cell_gate"),
    COMPARATOR("comparator_gate"),
    AND_CELL("and_cell_gate"),
    BUS_RANDOMIZER("bus_randomizer_gate"),
    BUS_CONVERTER("bus_converter_gate"),
    BUS_INPUT_PANEL("bus_input_panel_gate"),
    TRANSPARENT_LATCH_CELL("stacking_latch_gate"),
    SEGMENT_DISPLAY("segment_display_gate"),
    DEC_RANDOMIZER("dec_randomizer_gate");

    private final String id;

    GateType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GateType byId(String id) {
        for (GateType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}
