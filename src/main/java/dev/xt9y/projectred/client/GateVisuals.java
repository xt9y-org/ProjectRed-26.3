package dev.xt9y.projectred.client;

import dev.xt9y.projectred.integration.GateType;

final class GateVisuals {
    record WirePlan(
            String family,
            int count,
            int onMask,
            int disabledMask,
            boolean reflect
    ) {
        static final WirePlan NONE = new WirePlan("", 0, 0, 0, false);
    }

    static WirePlan wires(
            GateType type,
            int shape,
            int state,
            int state2,
            int arraySignalA,
            int arraySignalB
    ) {
        int on = 0;
        int disabled = 0;
        boolean reflect = false;
        String family;
        int count;

        switch (type) {
            case OR -> {
                family = "OR"; count = 4;
                on = bit((state & 0x10) == 0, 0)
                        | bit((state & 2) != 0, 1)
                        | bit((state & 4) != 0, 2)
                        | bit((state & 8) != 0, 3);
                disabled = bit((shape & 1) != 0, 1)
                        | bit((shape & 2) != 0, 2)
                        | bit((shape & 4) != 0, 3);
            }
            case NOR -> {
                family = "NOR"; count = 4;
                on = bit((state & 0x11) != 0, 0)
                        | bit((state & 2) != 0, 1)
                        | bit((state & 4) != 0, 2)
                        | bit((state & 8) != 0, 3);
                disabled = bit((shape & 1) != 0, 1)
                        | bit((shape & 2) != 0, 2)
                        | bit((shape & 4) != 0, 3);
            }
            case NOT -> {
                family = "NOT"; count = 4;
                on = bit((state & 0x11) != 0, 0)
                        | bit((state & 0x22) != 0, 1)
                        | bit((state & 4) != 0, 2)
                        | bit((state & 0x88) != 0, 3);
                disabled = bit((shape & 2) != 0, 0)
                        | bit((shape & 1) != 0, 1)
                        | bit((shape & 4) != 0, 3);
            }
            case AND -> {
                family = "AND"; count = 4;
                on = bit((state & 0x11) == 0, 0)
                        | bit((state & 4) != 0, 1)
                        | bit((state & 8) != 0, 2)
                        | bit((state & 2) != 0, 3);
                disabled = bit((shape & 2) != 0, 1)
                        | bit((shape & 4) != 0, 2)
                        | bit((shape & 1) != 0, 3);
            }
            case NAND -> {
                family = "NAND"; count = 4;
                on = bit((state & 0x11) != 0, 0)
                        | bit((state & 4) != 0, 1)
                        | bit((state & 8) != 0, 2)
                        | bit((state & 2) != 0, 3);
                disabled = bit((shape & 2) != 0, 1)
                        | bit((shape & 4) != 0, 2)
                        | bit((shape & 1) != 0, 3);
            }
            case XOR -> {
                family = "XOR"; count = 4;
                boolean w3 = (state & 2) != 0;
                boolean w2 = (state & 8) != 0;
                boolean w1 = !w3 && !w2;
                on = bit((state & 0x11) != 0, 0)
                        | bit(w1, 1) | bit(w2, 2) | bit(w3, 3);
            }
            case XNOR -> {
                family = "XNOR"; count = 5;
                boolean w2 = (state & 8) != 0;
                boolean w3 = (state & 2) != 0;
                on = bit((state & 2) != 0 && (state & 8) == 0, 0)
                        | bit((state & 8) != 0 && (state & 2) == 0, 1)
                        | bit(w2, 2) | bit(w3, 3)
                        | bit(!w3 && !w2, 4);
            }
            case BUFFER -> {
                family = "BUFFER"; count = 4;
                on = bit((state & 4) == 0, 0)
                        | bit((state & 0x22) != 0, 1)
                        | bit((state & 0x44) != 0, 2)
                        | bit((state & 0x88) != 0, 3);
                disabled = bit((shape & 1) != 0, 1)
                        | bit((shape & 2) != 0, 3);
            }
            case MULTIPLEXER -> {
                family = "MULTIPLEXER"; count = 6;
                boolean w2 = (state & 4) == 0;
                boolean w3 = (state & 4) != 0;
                boolean w4 = (state & 8) != 0;
                boolean w5 = (state & 2) != 0;
                boolean w0 = (state & 8) == 0 && w3;
                boolean w1 = !w3;
                on = bit(w0,0)|bit(w1,1)|bit(w2,2)|bit(w3,3)|bit(w4,4)|bit(w5,5);
            }
            case PULSE -> {
                family = "PULSE"; count = 3;
                on = bit((state & 4) == 0,0)
                        | bit((state & 4) != 0,1)
                        | bit((state & 0x14) == 4,2);
            }
            case REPEATER -> {
                family = "REPEATER"; count = 2;
                on = bit((state & 0x10) == 0,0)
                        | bit((state & 4) != 0,1);
            }
            case RANDOMIZER -> {
                family = "RAND"; count = 7;
                boolean w2 = (state & 4) != 0;
                on = bit((state & 0x11) != 0,0)
                        | bit((state & 0x22) != 0,1)
                        | bit(w2,2)
                        | bit((state & 0x88) != 0,3)
                        | bit(w2,4)|bit(w2,5)|bit(w2,6);
                disabled = bit((shape & 2) != 0,0)
                        | bit((shape & 1) != 0,1)
                        | bit((shape & 4) != 0,3)
                        | bit((shape & 2) != 0,4)
                        | bit((shape & 1) != 0,5)
                        | bit((shape & 4) != 0,6);
            }
            case SR_LATCH -> {
                reflect = (shape & 1) != 0;
                int s = reflect
                        ? (flipMaskZ(state >> 4) << 4) | flipMaskZ(state)
                        : state;
                if ((shape >> 1) == 0) {
                    family = "RSLATCH"; count = 2;
                    on = bit((s & 0x88) != 0,0)
                            | bit((s & 0x22) != 0,1);
                } else {
                    family = "RSLATCH2"; count = 4;
                    on = bit((s & 0x40) != 0,0)
                            | bit((s & 2) != 0,1)
                            | bit((s & 0x10) != 0,2)
                            | bit((s & 8) != 0,3);
                }
            }
            case TOGGLE_LATCH -> {
                family = "TOGLATCH"; count = 2;
                on = bit((state & 8) != 0,0)
                        | bit((state & 2) != 0,1);
            }
            case TRANSPARENT_LATCH -> {
                family = "TRANSLATCH"; count = 5;
                reflect = shape == 1;
                boolean high = (state & 0x10) != 0;
                on = bit(!high,0)
                        | bit((state & 4) != 0,1)
                        | bit((state & 4) == 0,2)
                        | bit(high,3)
                        | bit((state & 0xA) != 0,4);
            }
            case LIGHT_SENSOR -> {
                family = "LIGHTSENSOR"; count = 1;
                on = bit((state & 0xF4) != 0,0);
            }
            case RAIN_SENSOR -> {
                family = "RAINSENSOR"; count = 1;
                on = bit((state & 0x44) != 0,0);
            }
            case TIMER -> {
                family = "TIME"; count = 3;
                on = bit((state & 0x88) != 0,0)
                        | bit((state & 0x22) != 0,1)
                        | bit((state & 4) != 0,2);
            }
            case SEQUENCER -> {
                return WirePlan.NONE;
            }
            case COUNTER -> {
                family = "COUNT"; count = 2;
                reflect = shape == 1;
                on = bit((state & 8) != 0,0)
                        | bit((state & 2) != 0,1);
            }
            case STATE_CELL -> {
                family = "STATECELL"; count = 5;
                reflect = shape == 1;
                int s = reflect
                        ? (flipMaskZ(state >> 4) << 4) | flipMaskZ(state)
                        : state;
                on = bit((s & 0x10) != 0,0)
                        | bit((s & 4) != 0,1)
                        | bit(state2 == 0 || (s & 4) != 0,2)
                        | bit((s & 0x88) != 0,3)
                        | bit((s & 2) != 0,4);
            }
            case SYNCHRONIZER -> {
                family = "SYNC"; count = 6;
                boolean left = (state2 & 1) != 0;
                boolean right = (state2 & 2) != 0;
                on = bit(!left,0)|bit(!right,1)
                        | bit((state & 4) != 0,2)
                        | bit(left && right,3)
                        | bit((state & 8) != 0,4)
                        | bit((state & 2) != 0,5);
            }
            case BUS_TRANSCEIVER -> {
                family = "BUSXCVR"; count = 2;
                reflect = shape != 0;
                int s = reflect ? flipMaskZ(state) : state;
                on = bit((s & 2) != 0,0)|bit((s & 8) != 0,1);
            }
            case NULL_CELL -> {
                return WirePlan.NONE;
            }
            case INVERT_CELL -> {
                family = "INVCELL"; count = 1;
                on = bit(arraySignalA != 0,0);
            }
            case BUFFER_CELL -> {
                family = "BUFFCELL"; count = 2;
                on = bit(arraySignalA != 0,0)|bit(arraySignalA == 0,1);
            }
            case COMPARATOR -> {
                family = "COMPARATOR"; count = 4;
                reflect = shape != 0;
                boolean w1 = (state & 2) != 0;
                boolean w3 = (state & 8) != 0;
                if (reflect) {
                    boolean t = w1; w1 = w3; w3 = t;
                }
                on = bit((state & 0x10) == 0,0)
                        | bit(w1,1)
                        | bit((state & 4) != 0,2)
                        | bit(w3,3);
            }
            case AND_CELL -> {
                family = "ANDCELL"; count = 2;
                boolean t0 = (state & 4) == 0;
                boolean t2 = arraySignalB == 0;
                on = bit(t0 || t2,0)|bit(!t0,1);
            }
            case BUS_RANDOMIZER -> {
                family = shape == 0 ? "BUSRAND1" : "BUSRAND2";
                count = 2;
                on = bit((state & 2) != 0,0)|bit((state & 8) != 0,1);
            }
            case BUS_CONVERTER -> {
                family = "BUSCONV"; count = 3;
                on = bit((state & 0x20) != 0,0)
                        | bit((state & 0x80) != 0,1)
                        | bit((state & 0xA0) != 0 || (state2 & 0xF) != 0,2);
            }
            case BUS_INPUT_PANEL -> {
                family = "BUSINPUT"; count = 1;
                on = bit((state & 1) != 0,0);
            }
            case TRANSPARENT_LATCH_CELL -> {
                family = "STACKLATCH"; count = 5;
                boolean high = (state & 0x10) != 0;
                int sig = arraySignalB;
                on = bit(!high,0)
                        | bit(sig != 0,1)
                        | bit(sig == 0,2)
                        | bit(high,3)
                        | bit((state & 4) != 0,4);
            }
            case SEGMENT_DISPLAY -> {
                return WirePlan.NONE;
            }
            case DEC_RANDOMIZER -> {
                family = "DECRAND"; count = 6;
                int high = state >> 4;
                on = bit(high == 2,0)
                        | bit(high == 8,1)
                        | bit((state & 4) != 0,2)
                        | bit((state & 4) != 0,3)
                        | bit(high == 1 || high == 2,4)
                        | bit(high == 1,5);
                disabled = bit(shape != 0,0)|bit(shape != 0,3);
            }
            default -> {
                return WirePlan.NONE;
            }
        }

        return new WirePlan(family, count, on, disabled, reflect);
    }

    static int flipMaskZ(int mask) {
        return (mask & 0x5) | ((mask & 0x2) << 2) | ((mask & 0x8) >> 2);
    }

    private static int bit(boolean value, int bit) {
        return value ? 1 << bit : 0;
    }

    private GateVisuals() {}
}
