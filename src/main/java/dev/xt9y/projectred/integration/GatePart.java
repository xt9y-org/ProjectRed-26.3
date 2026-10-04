package dev.xt9y.projectred.integration;

import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import java.util.Random;
import net.minecraft.world.level.LightLayer;

public final class GatePart extends Part {
    private static final int[] REPEATER_DELAYS = { 2, 4, 6, 8, 16, 32, 64, 128, 256 };
    private static final Random RANDOM = new Random();

    private final GateType type;
    private int rotation;
    private int shape;
    private int state;
    private int state2;
    private long scheduledAt = -1;

    public GatePart(GateType type, int slot, int rotation) {
        super(slot);
        if (slot == CENTER_SLOT) throw new IllegalArgumentException("gate cannot occupy center");
        this.type = type;
        this.rotation = rotation & 3;
    }

    public GateType type() { return type; }
    public int rotation() { return rotation; }
    public int shape() { return shape; }
    public int state() { return state; }

    public void rotate() {
        rotation = (rotation + 1) & 3;
    }

    public void cycleShape() {
        shape = switch (type) {
            case REPEATER -> (shape + 1) % REPEATER_DELAYS.length;
            case TRANSPARENT_LATCH, DEC_RANDOMIZER -> shape ^ 1;
            case LIGHT_SENSOR -> (shape + 1) % 3;
            case SR_LATCH -> (shape + 1) % 4;
            default -> shape;
        };
    }

    public boolean canConnectLocal(int r) {
        int mask = inputMask() | outputMask();
        return (mask & (1 << r)) != 0;
    }

    public int outputLocal(int r) {
        if ((outputMask() & (1 << r)) == 0) return 0;
        if (type == GateType.LIGHT_SENSOR) return r == 2 ? (state >> 4) & 15 : 0;
        return (state & (0x10 << r)) != 0 ? 15 : 0;
    }

    public boolean tick(MultipartBlockEntity owner) {
        long time = owner.getLevel() == null ? 0 : owner.getLevel().getGameTime();
        boolean changed = false;

        if (scheduledAt >= 0 && time >= scheduledAt) {
            scheduledAt = -1;
            int old = state;
            scheduled(owner);
            changed |= old != state;
        }

        int old = state;
        onChange(owner);
        changed |= old != state;

        if (type == GateType.LIGHT_SENSOR && owner.getLevel() != null) {
            int sky = owner.getLevel().getBrightness(LightLayer.SKY, owner.getBlockPos()) - owner.getLevel().getSkyDarken();
            int block = owner.getLevel().getBrightness(LightLayer.BLOCK, owner.getBlockPos());
            int out = shape == 1 ? sky : shape == 2 ? block : Math.max(sky, block);
            state = (state & 0xF) | (Math.max(0, Math.min(15, out)) << 4);
        } else if (type == GateType.RAIN_SENSOR && owner.getLevel() != null) {
            int out = owner.getLevel().isRaining() && owner.getLevel().canSeeSky(owner.getBlockPos()) ? 4 : 0;
            state = (state & 0xF) | (out << 4);
        }

        return changed || old != state;
    }

    private void onChange(MultipartBlockEntity owner) {
        int mask = inputMask();
        int input = owner.gateInput(this, mask);
        int oldInput = state & 0xF;

        if (type == GateType.PULSE) {
            if (input != oldInput) {
                state = state & 0xF0 | input;
                if (input != 0 && (state & 0xF0) == 0) {
                    state = state & 0xF | 0x10;
                    schedule(owner, 2);
                }
            }
            return;
        }

        if (type == GateType.TOGGLE_LATCH) {
            int high = input & ~oldInput;
            if (high == 2 || high == 8) {
                state2 = state2 == 0 ? 1 : 0;
                schedule(owner, 2);
            }
            state = state & 0xF0 | input;
            return;
        }

        if (type == GateType.SR_LATCH) {
            if (input != oldInput && input != 0 && input != state2) {
                state2 = input;
                state = input;
                schedule(owner, 2);
            } else {
                state = state & 0xF0 | input;
            }
            return;
        }

        if (type == GateType.LIGHT_SENSOR || type == GateType.RAIN_SENSOR) {
            state = state & 0xF0 | owner.gateInput(this, 4);
            return;
        }

        if (input != oldInput) state = state & 0xF0 | input;
        int output = calcOutput(state & mask) & outputMask();
        if (output != (state >> 4)) {
            if (type != GateType.REPEATER || scheduledAt < 0) schedule(owner, delay());
        }
    }

    private void scheduled(MultipartBlockEntity owner) {
        if (type == GateType.TOGGLE_LATCH) {
            int out = state2 == 0 ? 1 : 4;
            state = state & 0xF | out << 4;
            return;
        }
        if (type == GateType.SR_LATCH) {
            int input = state & 0xF;
            int s = state2;
            if (s == 0xA) s = input == 0 ? (RANDOM.nextBoolean() ? 2 : 8) : input;
            if (s == 0) s = 2;
            int out = shiftMask(s, 1);
            if ((shape & 2) == 0) out |= s;
            state = state & 0xF | (out & outputMask()) << 4;
            state2 = s;
            return;
        }
        int output = calcOutput(state & inputMask()) & outputMask();
        state = state & 0xF | output << 4;
    }

    private int delay() {
        return type == GateType.REPEATER ? REPEATER_DELAYS[Math.min(shape, REPEATER_DELAYS.length - 1)] : 2;
    }

    private void schedule(MultipartBlockEntity owner, int ticks) {
        if (scheduledAt < 0 && owner.getLevel() != null) scheduledAt = owner.getLevel().getGameTime() + ticks;
    }

    private int inputMask() {
        return switch (type) {
            case OR, NOR, AND, NAND -> ~shape << 1 & 0xE;
            case NOT, BUFFER, PULSE, REPEATER, RANDOMIZER, DEC_RANDOMIZER -> 4;
            case XOR, XNOR, TOGGLE_LATCH, SR_LATCH -> 0xA;
            case MULTIPLEXER -> 0xE;
            case TRANSPARENT_LATCH -> shape == 0 ? 0xC : 6;
            case LIGHT_SENSOR, RAIN_SENSOR -> 0;
            default -> 0;
        };
    }

    private int outputMask() {
        return switch (type) {
            case OR, NOR, AND, NAND, XOR, XNOR, MULTIPLEXER, PULSE, REPEATER -> 1;
            case NOT, RANDOMIZER -> 0xB;
            case BUFFER -> 0xB;
            case SR_LATCH -> (shape >> 1) == 0 ? 0xF : 5;
            case TOGGLE_LATCH -> 5;
            case TRANSPARENT_LATCH -> shape == 0 ? 3 : 9;
            case LIGHT_SENSOR, RAIN_SENSOR -> 4;
            case DEC_RANDOMIZER -> shape == 0 ? 0xB : 9;
            default -> 0;
        };
    }

    private int calcOutput(int input) {
        return switch (type) {
            case OR -> input != 0 ? 1 : 0;
            case NOR -> input == 0 ? 1 : 0;
            case NOT -> input == 0 ? 0xB : 0;
            case AND -> input == inputMask() ? 1 : 0;
            case NAND -> input == inputMask() ? 0 : 1;
            case XOR -> (((input & 2) != 0) ^ ((input & 8) != 0)) ? 1 : 0;
            case XNOR -> (((input & 2) != 0) == ((input & 8) != 0)) ? 1 : 0;
            case BUFFER -> input != 0 ? 0xB : 0;
            case MULTIPLEXER -> (input & 4) != 0 ? (input >> 3) & 1 : (input >> 1) & 1;
            case REPEATER -> input != 0 ? 1 : 0;
            case RANDOMIZER -> input == 0 ? state >> 4 : (RANDOM.nextInt(8) & outputMask());
            case TRANSPARENT_LATCH -> (input & 4) == 0 ? state >> 4 : (input & 0xA) == 0 ? 0 : 0xF;
            case DEC_RANDOMIZER -> input == 0 ? ((state >> 4) == 0 ? 1 : state >> 4) : new int[] {1, 8, 2}[RANDOM.nextInt(shape == 0 ? 3 : 2)];
            default -> 0;
        };
    }

    private static int shiftMask(int mask, int r) {
        r &= 3;
        return ((mask << r) | (mask >> (4 - r))) & 0xF;
    }

    @Override
    public String encode() {
        return "g|" + type.id() + "|" + slot() + "|" + rotation + "|" + shape + "|" + state + "|" + state2 + "|" + scheduledAt;
    }

    public static GatePart decode(GateType type, int slot, String[] p) {
        GatePart part = new GatePart(type, slot, p.length > 3 ? Integer.parseInt(p[3]) : 0);
        if (p.length > 4) part.shape = Integer.parseInt(p[4]);
        if (p.length > 5) part.state = Integer.parseInt(p[5]);
        if (p.length > 6) part.state2 = Integer.parseInt(p[6]);
        if (p.length > 7) part.scheduledAt = Long.parseLong(p[7]);
        return part;
    }
}
