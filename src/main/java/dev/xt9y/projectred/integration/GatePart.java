package dev.xt9y.projectred.integration;

import dev.xt9y.projectred.core.BundledSignals;
import dev.xt9y.projectred.multipart.MultipartBlockEntity;
import dev.xt9y.projectred.multipart.Part;
import java.util.Random;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.LightLayer;

public final class GatePart extends Part {
    private static final int[] ADVANCE_DEAD = { 1, 2, 4, 0, 5, 6, 3 };
    private static final int[] REPEATER_DELAYS = { 2, 4, 6, 8, 16, 32, 64, 128, 256 };
    private static final int[] DECODER_OUTPUTS = { 1, 8, 2 };
    private static final Random RANDOM = new Random();

    private final GateType type;
    private int rotation;
    private int shape;
    private int state;
    private int state2;
    private long scheduledAt = -1;

    // Stateful Integration gates.
    private int timerPeriod = 40;
    private long pointerStart = -1;
    private boolean pointerNeedsWorldTimeRebase;
    private int counterValue;
    private int counterMax = 10;
    private int counterIncrement = 1;
    private int counterDecrement = 1;

    // Array-cell redwire channels. A is local 0/2, B is local 1/3.
    private int arraySignalA;
    private int arraySignalB;

    // Bundled gates store digital channel masks. Every set bit is emitted at 255.
    private int bundleInput0;
    private int bundleInput2;
    private int bundleOutput0;
    private int bundleOutput2;
    private int bundleMask = 0xFFFF;
    private int pressMask;
    private boolean tickSoundPending;

    public GatePart(GateType type, int slot, int rotation) {
        super(slot);
        if (slot == CENTER_SLOT) {
            throw new IllegalArgumentException("gate cannot occupy center");
        }
        this.type = type;
        this.rotation = rotation & 3;

        if (type == GateType.SR_LATCH) {
            state2 = 2;
            state = 0x30;
        } else if (type == GateType.TOGGLE_LATCH) {
            state = 0x10;
        } else if (type == GateType.SEGMENT_DISPLAY) {
            // ProjectRed defaults the display to red.
            state = 14;
        }
    }

    public GateType type() {
        return type;
    }

    public int rotation() {
        return rotation;
    }

    public int shape() {
        return shape;
    }

    public int state() {
        return state;
    }

    public boolean isArrayCell() {
        return switch (type) {
            case NULL_CELL, INVERT_CELL, BUFFER_CELL,
                    AND_CELL, TRANSPARENT_LATCH_CELL -> true;
            default -> false;
        };
    }

    public int timerPeriod() {
        return timerPeriod;
    }

    public int counterValue() {
        return counterValue;
    }

    public int counterMax() {
        return counterMax;
    }

    public int counterIncrement() {
        return counterIncrement;
    }

    public int counterDecrement() {
        return counterDecrement;
    }

    public int segmentMask() {
        return bundleInput0 & 0xFFFF;
    }

    public int panelMask() {
        return pressMask & 0xFFFF;
    }

    public float pointerAngle(
            long gameTime,
            long clockTime,
            float partialTick
    ) {
        return switch (type) {
            case TIMER -> {
                if (pointerStart < 0) yield 0.0F;
                float max = Math.max(2, timerPeriod - 2);
                float progress = Math.min(
                        1.0F,
                        Math.max(
                                0.0F,
                                (gameTime - pointerStart + partialTick) / max
                        )
                );
                yield progress * ((float) Math.PI * 2.0F);
            }
            case SEQUENCER -> {
                long cycle = Math.max(4L, timerPeriod * 4L);
                float progress = (float) (
                        Math.floorMod(clockTime, cycle) + partialTick
                ) / (float) cycle;
                float angle = progress * ((float) Math.PI * 2.0F);
                yield shape == 1 ? -angle : angle;
            }
            case COUNTER -> {
                float progress = counterMax <= 0
                        ? 0.0F
                        : (float) counterValue / (float) counterMax;
                float degrees = 210.0F + progress * 120.0F;
                yield (float) Math.toRadians(degrees);
            }
            case STATE_CELL -> {
                if (pointerStart < 0) yield -(float) Math.PI / 2.0F;
                float max = Math.max(2, timerPeriod - 2);
                float progress = Math.min(
                        1.0F,
                        Math.max(
                                0.0F,
                                (gameTime - pointerStart + partialTick) / max
                        )
                );
                yield progress - (float) Math.PI / 2.0F;
            }
            default -> Float.NaN;
        };
    }

    public float pointerLateralOffset() {
        return switch (type) {
            case COUNTER -> (shape == 1 ? -1.0F : 1.0F) * (3.0F / 16.0F);
            case STATE_CELL -> (shape == 1 ? -1.0F : 1.0F) * (5.0F / 16.0F);
            default -> 0.0F;
        };
    }

    public void setDisplayColor(int color) {
        if (type != GateType.SEGMENT_DISPLAY) return;
        state = Math.max(0, Math.min(15, color));
    }

    public void rotate() {
        rotation = (rotation + 1) & 3;
    }

    public void cycleShape() {
        switch (type) {
            case OR, NOR, AND, NAND, NOT, RANDOMIZER -> shape = cycleDeadSides(shape, 3, 2);
            case BUFFER -> shape = cycleDeadSides(shape, 2, 2);
            case REPEATER -> shape = (shape + 1) % REPEATER_DELAYS.length;
            case TRANSPARENT_LATCH, DEC_RANDOMIZER, SEQUENCER, COUNTER, STATE_CELL,
                    COMPARATOR, BUS_TRANSCEIVER, BUS_RANDOMIZER, BUS_CONVERTER,
                    SEGMENT_DISPLAY -> shape ^= 1;
            case LIGHT_SENSOR -> shape = (shape + 1) % 3;
            case SR_LATCH -> {
                shape = (shape + 1) % 4;
                state = flipMaskZ(state & 0xF)
                        | flipMaskZ(state >> 4) << 4;
                state2 = flipMaskZ(state2);
                scheduleWithoutOwner();
            }
            default -> {
            }
        }
    }

    public void adjustTimer(int delta) {
        // Upstream changes the configured maximum in-place. A running timer
        // keeps its elapsed pointer and naturally fires sooner/later against
        // the new limit.
        timerPeriod = clampAdd(
                timerPeriod,
                delta,
                4,
                20 * 60 * 60
        );
    }

    public void adjustCounterMax(int delta) {
        int oldMax = counterMax;
        int oldValue = counterValue;
        counterMax = clampAdd(counterMax, delta, 1, 32767);
        counterValue = Math.min(counterValue, counterMax);

        // Counter increments/decrements are independent settings upstream;
        // changing max does not rewrite them. A value clamp does require the
        // normal delayed output update.
        if (counterValue != oldValue) {
            scheduleWithoutOwner();
        }
        if (counterMax != oldMax) {
            tickSoundPending = true;
        }
    }

    public void adjustCounterIncrement(int delta) {
        int old = counterIncrement;
        counterIncrement = clampAdd(
                counterIncrement,
                delta,
                1,
                counterMax
        );
        if (counterIncrement != old) {
            tickSoundPending = true;
        }
    }

    public void adjustCounterDecrement(int delta) {
        int old = counterDecrement;
        counterDecrement = clampAdd(
                counterDecrement,
                delta,
                1,
                counterMax
        );
        if (counterDecrement != old) {
            tickSoundPending = true;
        }
    }

    public void activate() {
        if (type == GateType.TOGGLE_LATCH) {
            state2 = state2 == 0 ? 1 : 0;
            scheduleWithoutOwner();
            tickSoundPending = true;
        } else if (type == GateType.REPEATER) {
            cycleShape();
        }
    }

    public void togglePanelBit(int bit) {
        if (type != GateType.BUS_INPUT_PANEL || bit < 0 || bit >= 16) {
            return;
        }
        pressMask ^= 1 << bit;
        scheduleWithoutOwner();
    }

    public boolean canConnectRedstoneLocal(int r) {
        int mask = inputMask() | outputMask() | redwireMask();
        return (mask & (1 << (r & 3))) != 0;
    }

    public boolean canConnectBundledLocal(int r) {
        return ((bundledInputMask() | bundledOutputMask()) & (1 << (r & 3))) != 0;
    }

    public boolean diminishesRedwireLocal(int r) {
        r &= 3;
        return (redwireMask() & (1 << r)) != 0;
    }

    public int outputLocal(int r) {
        r &= 3;

        if (type == GateType.COMPARATOR && r == 0) {
            return state2 & 0xF;
        }
        if (type == GateType.BUS_CONVERTER && shape != 0 && r == 2) {
            return state2 & 0xF;
        }
        if (type == GateType.LIGHT_SENSOR) {
            return r == 2 ? (state >> 4) & 15 : 0;
        }

        if ((outputMask() & (1 << r)) == 0) {
            return 0;
        }
        return (state & (0x10 << r)) != 0 ? 15 : 0;
    }

    public int outputRawLocal(int r) {
        r &= 3;

        switch (type) {
            case NULL_CELL -> {
                return (r & 1) == 0 ? arraySignalA : arraySignalB;
            }
            case INVERT_CELL -> {
                if ((r & 1) == 0) return arraySignalA;
                return (state & 2) != 0 ? 255 : arraySignalB;
            }
            case BUFFER_CELL -> {
                if ((r & 1) == 0) return arraySignalA;
                return (state & 2) == 0 ? 255 : arraySignalB;
            }
            case AND_CELL, TRANSPARENT_LATCH_CELL -> {
                if ((r & 1) == 1) return arraySignalB;
            }
            default -> {
            }
        }

        return outputLocal(r) * 17;
    }

    public int[] bundledOutputLocal(int r) {
        r &= 3;
        int packed = switch (type) {
            case BUS_TRANSCEIVER -> r == 0 ? bundleOutput0 : r == 2 ? bundleOutput2 : 0;
            case BUS_RANDOMIZER -> r == 0 ? bundleOutput0 : 0;
            case BUS_CONVERTER -> shape == 0 && r == 0 ? bundleOutput0 : 0;
            case BUS_INPUT_PANEL -> r == 2 ? bundleOutput2 : 0;
            default -> 0;
        };
        return packed == 0 ? null : BundledSignals.unpackDigital(packed);
    }

    public void onAdded(MultipartBlockEntity owner) {
        long time = owner.getLevel() == null
                ? 0
                : owner.getLevel().getGameTime();

        // SimpleGatePart::gateLogicSetup() computes the initial output
        // immediately on placement rather than waiting for its first delayed
        // tick. This matters for normally-high gates such as NOT/NOR/NAND/XNOR
        // and for the decoding randomizer's default output.
        if (isSimpleGate()) {
            int mask = inputMask();
            int input = owner.gateInput(this, mask);
            int output = calcOutput(input & mask) & outputMask();
            if (output != 0) {
                state = output << 4;
            }
        }

        // ProjectRed starts the timer pointer during gate setup.
        if (type == GateType.TIMER && owner.getLevel() != null) {
            pointerStart = time;
        }

        // GatePart::onAdded() always follows setup with an onChange pass.
        switch (type) {
            case LIGHT_SENSOR -> tickLightSensor(owner);
            case RAIN_SENSOR -> tickRainSensor(owner);
            case TIMER -> tickTimer(owner, time);
            case SEQUENCER -> tickSequencer(owner);
            case NULL_CELL, INVERT_CELL, BUFFER_CELL, AND_CELL, TRANSPARENT_LATCH_CELL ->
                    tickArrayGate(owner);
            case BUS_TRANSCEIVER, BUS_RANDOMIZER, BUS_CONVERTER, BUS_INPUT_PANEL, SEGMENT_DISPLAY ->
                    tickBundledGate(owner);
            default -> onChange(owner);
        }
    }

    private boolean isSimpleGate() {
        return switch (type) {
            case OR, NOR, NOT, AND, NAND, XOR, XNOR, BUFFER,
                    MULTIPLEXER, PULSE, REPEATER, RANDOMIZER,
                    TRANSPARENT_LATCH, LIGHT_SENSOR, RAIN_SENSOR,
                    DEC_RANDOMIZER -> true;
            default -> false;
        };
    }

    public void restoreWorldTimeBase(MultipartBlockEntity owner) {
        if (!pointerNeedsWorldTimeRebase || owner.getLevel() == null) return;

        if (pointerStart >= 0) {
            pointerStart = owner.getLevel().getGameTime() - pointerStart;
        }
        pointerNeedsWorldTimeRebase = false;
    }

    public boolean tick(MultipartBlockEntity owner) {
        String before = encode();
        long time = owner.getLevel() == null ? 0 : owner.getLevel().getGameTime();

        if (tickSoundPending) {
            tickSoundPending = false;
            playTickSound(owner);
        }

        if (scheduledAt == 0) {
            // Interactions/config edits use zero as an owner-less scheduling
            // marker. Signal propagation can now evaluate this gate in the
            // same server tick, so preserve ProjectRed's full 2-tick delay.
            scheduledAt = time + 2;
        }

        if (scheduledAt >= 0 && time >= scheduledAt) {
            scheduledAt = -1;
            scheduled(owner);
        }

        switch (type) {
            case LIGHT_SENSOR -> tickLightSensor(owner);
            case RAIN_SENSOR -> tickRainSensor(owner);
            case TIMER -> tickTimer(owner, time);
            case SEQUENCER -> tickSequencer(owner);
            case NULL_CELL, INVERT_CELL, BUFFER_CELL, AND_CELL, TRANSPARENT_LATCH_CELL ->
                    tickArrayGate(owner);
            case BUS_TRANSCEIVER, BUS_RANDOMIZER, BUS_CONVERTER, BUS_INPUT_PANEL, SEGMENT_DISPLAY ->
                    tickBundledGate(owner);
            default -> onChange(owner);
        }

        return !before.equals(encode());
    }

    private void tickLightSensor(MultipartBlockEntity owner) {
        if (owner.getLevel() == null) return;
        int sky = owner.getLevel().getBrightness(LightLayer.SKY, owner.getBlockPos())
                - owner.getLevel().getSkyDarken();
        int block = owner.getLevel().getBrightness(LightLayer.BLOCK, owner.getBlockPos());
        int out = shape == 1 ? sky : shape == 2 ? block : Math.max(sky, block);

        // Modern ProjectRed also samples the output-side feedback into the
        // low state nibble. It does not drive the sensor output, but it keeps
        // the gate's redstone connection/input state consistent with the
        // upstream implementation.
        int feedback = owner.gateInput(this, 4);
        state = (feedback & 0xF)
                | (Math.max(0, Math.min(15, out)) << 4);
    }

    private void tickRainSensor(MultipartBlockEntity owner) {
        if (owner.getLevel() == null) return;

        int out = owner.getLevel().isRaining()
                && owner.getLevel().canSeeSky(owner.getBlockPos())
                ? 4
                : 0;

        // RainSensor inherits SimpleGatePart's feedbackMask(4) path upstream,
        // so its low nibble mirrors redstone seen on the output side just
        // like LightSensor. Keep the feedback bookkeeping identical.
        int feedback = owner.gateInput(this, 4);
        state = (feedback & 0xF) | (out << 4);
    }

    private void tickTimer(MultipartBlockEntity owner, long time) {
        int input = owner.gateInput(this, 0xE);
        state = (state & 0xF0) | input;

        if (input != 0) {
            pointerStart = -1;
            return;
        }

        if ((state & 0xF0) != 0 || scheduledAt >= 0) {
            return;
        }

        if (pointerStart < 0) {
            pointerStart = time;
        } else if (time >= pointerStart + Math.max(2, timerPeriod - 2)) {
            pointerStart = -1;
            state = (state & 0xF) | 0xB0;
            schedule(owner, 2);
            playTickSound(owner);
        }
    }

    private void tickSequencer(MultipartBlockEntity owner) {
        if (owner.getLevel() == null) return;
        int step = (int) (owner.getLevel().getDefaultClockTime() % (timerPeriod * 4L) / timerPeriod);
        int out = 1 << step;
        if (shape == 1) out = flipMaskZ(out);
        int oldOut = state >> 4;
        state = out << 4;
        if (oldOut != out) {
            playTickSound(owner);
        }
    }

    private void tickArrayGate(MultipartBlockEntity owner) {
        int a = Math.max(owner.gateRedwireRawInput(this, 0), owner.gateRedwireRawInput(this, 2));
        int b = Math.max(owner.gateRedwireRawInput(this, 1), owner.gateRedwireRawInput(this, 3));
        arraySignalA = Math.max(0, a);
        arraySignalB = Math.max(0, b);

        if (type == GateType.NULL_CELL) return;

        if (type == GateType.INVERT_CELL || type == GateType.BUFFER_CELL) {
            boolean oldInput = (state & 1) != 0;
            boolean newInput = arraySignalA != 0;
            if (oldInput != newInput) {
                state = (state & 2) | (newInput ? 1 : 0);
                schedule(owner, 2);
            }
            return;
        }

        int oldInput = state & 0xF;
        int digital = owner.gateInput(this, 4);
        if (arraySignalB != 0) digital |= 0xA;
        if (oldInput != digital) state = (state & 0xF0) | digital;

        int out = type == GateType.AND_CELL
                ? ((digital & 4) != 0 && (digital & 0xA) != 0 ? 1 : 0)
                : ((digital & 0xA) == 0 ? state >> 4 : (digital & 4) == 0 ? 0 : 1);

        if (out != (state >> 4)) schedule(owner, 2);
    }

    private void tickBundledGate(MultipartBlockEntity owner) {
        switch (type) {
            case BUS_TRANSCEIVER -> {
                int control = owner.gateInput(this, 0xA);
                state = (state & 0xF0) | control;
                bundleInput0 = BundledSignals.packDigital(owner.gateBundledInput(this, 0))
                        | bundleOutput0;
                bundleInput2 = BundledSignals.packDigital(owner.gateBundledInput(this, 2))
                        | bundleOutput2;

                int c = shape == 1 ? flipMaskZ(control) : control;
                int next0 = (c & 2) != 0 ? bundleInput2 : 0;
                int next2 = (c & 8) != 0 ? bundleInput0 : 0;
                if (next0 != bundleOutput0 || next2 != bundleOutput2) schedule(owner, 2);
            }
            case BUS_RANDOMIZER -> {
                int control = owner.gateInput(this, 0xA);
                state = (state & 0xF0) | control;
                int mask = BundledSignals.packDigital(owner.gateBundledInput(this, 2));
                bundleMask = mask == 0 ? 0xFFFF : mask;
                if (control != 0) schedule(owner, 2);
            }
            case BUS_CONVERTER -> {
                if (shape == 0) {
                    int analog = owner.gateAnalogInput(this, 2);
                    state2 = analog & 0xF;
                    int next = 1 << (analog & 0xF);
                    if (bundleOutput0 != next) schedule(owner, 2);
                } else {
                    int nextInput = BundledSignals.packDigital(
                            owner.gateBundledInput(this, 0)
                    );
                    if (bundleInput0 != nextInput) {
                        bundleInput0 = nextInput;
                        schedule(owner, 2);
                    }
                }
            }
            case BUS_INPUT_PANEL -> {
                int reset = owner.gateInput(this, 1);
                state = (state & 0xF0) | reset;
                if (reset != 0) pressMask = 0;
                if (bundleOutput2 != pressMask) schedule(owner, 2);
            }
            case SEGMENT_DISPLAY -> bundleInput0 = BundledSignals.packDigital(owner.gateBundledInput(this, 0));
            default -> {
            }
        }
    }

    private void onChange(MultipartBlockEntity owner) {
        if (type == GateType.REPEATER && scheduledAt >= 0) {
            return;
        }

        int mask = inputMask();
        int input = owner.gateInput(this, mask | feedbackMask());
        int oldInput = state & 0xF;

        switch (type) {
            case PULSE -> {
                if (input != oldInput) {
                    state = state & 0xF0 | input;
                    if (input != 0 && (state & 0xF0) == 0) {
                        state = state & 0xF | 0x10;
                        schedule(owner, 2);
                    }
                }
                return;
            }
            case TOGGLE_LATCH -> {
                int high = input & ~oldInput;
                if (high == 2 || high == 8) {
                    state2 = state2 == 0 ? 1 : 0;
                    schedule(owner, 2);
                    playTickSound(owner);
                }
                state = state & 0xF0 | input;
                return;
            }
            case SR_LATCH -> {
                int newInput = input;
                if (newInput != oldInput) {
                    if (state2 != 0xA && newInput != 0 && newInput != state2) {
                        state = newInput;
                        state2 = newInput;
                        schedule(owner, 2);
                    } else {
                        state = (state & 0xF0) | newInput;
                    }
                }
                return;
            }
            case COUNTER -> {
                int newInput = input;
                if (shape == 1) newInput = flipMaskZ(newInput);
                int high = newInput & ~oldInput;
                int oldValue = counterValue;
                if ((high & 2) != 0) counterValue = Math.min(counterMax, counterValue + counterIncrement);
                if ((high & 8) != 0) counterValue = Math.max(0, counterValue - counterDecrement);
                if (counterValue != oldValue) {
                    playTickSound(owner);
                }
                if (newInput != oldInput) {
                    state = (state & 0xF0) | newInput;
                    schedule(owner, 2);
                }
                return;
            }
            case STATE_CELL -> {
                int newInput = owner.gateInput(this, 0xE);
                if (newInput != oldInput) {
                    state = (state & 0xF0) | newInput;
                    int oriented = shape == 1 ? flipMaskZ(newInput) : newInput;
                    if ((oriented & 4) != 0 && state2 == 0) {
                        state2 = 1;
                        schedule(owner, 2);
                    }
                    if (state2 != 0) {
                        if ((oriented & 6) != 0) pointerStart = -1;
                        else if (owner.getLevel() != null && pointerStart < 0) pointerStart = owner.getLevel().getGameTime();
                    }
                }
                if (state2 != 0 && pointerStart >= 0 && owner.getLevel() != null
                        && owner.getLevel().getGameTime() >= pointerStart + Math.max(2, timerPeriod - 2)) {
                    pointerStart = -1;
                    state2 = 0;
                    state = (state & 0xF) | 0x10;
                    schedule(owner, 2);
                    playTickSound(owner);
                }
                return;
            }
            case SYNCHRONIZER -> {
                int newInput = input;
                int high = newInput & ~oldInput;
                if (newInput != oldInput) {
                    state = (state & 0xF0) | newInput;
                    if ((newInput & 4) != 0) {
                        state2 = 0;
                    } else {
                        if ((high & 2) != 0) state2 |= 1;
                        if ((high & 8) != 0) state2 |= 2;
                    }
                    if ((state2 & 3) == 3) schedule(owner, 2);
                }
                return;
            }
            case COMPARATOR -> {
                int left = owner.gateAnalogInput(this, 1);
                int back = owner.gateComparatorBackInput(this);
                int right = owner.gateAnalogInput(this, 3);
                int oldOut = state2 & 0xF;
                int side = Math.max(left, right);
                int out = shape == 0
                        ? (back > side ? back : 0)
                        : Math.max(back - side, 0);
                int digital = (left > 0 ? 2 : 0)
                        | (back > 0 ? 4 : 0)
                        | (right > 0 ? 8 : 0);
                state = (state & 0xF0) | digital;
                state2 = (left << 4)
                        | (back << 8)
                        | (right << 12)
                        | oldOut;
                if (out != oldOut) {
                    schedule(owner, 2);
                }
                return;
            }
            default -> {
            }
        }

        if (input != oldInput) state = state & 0xF0 | input;
        int output = calcOutput(state & mask) & outputMask();
        if (output != (state >> 4)) {
            if (type != GateType.REPEATER || scheduledAt < 0) schedule(owner, delay());
        }

        // ProjectRed randomizers continue scheduling while their trigger
        // remains high, even if a random roll happens to equal the current
        // output value.
        if ((type == GateType.RANDOMIZER || type == GateType.DEC_RANDOMIZER)
                && (state & 4) != 0) {
            schedule(owner, 2);
        }
    }

    private void scheduled(MultipartBlockEntity owner) {
        switch (type) {
            case TOGGLE_LATCH -> {
                int out = state2 == 0 ? 1 : 4;
                state = state & 0xF | out << 4;
                return;
            }
            case SR_LATCH -> {
                int input = state & 0xF;
                int s = state2;
                if ((shape & 1) != 0) {
                    input = flipMaskZ(input);
                    s = flipMaskZ(s);
                }
                if (s == 0xA) {
                    if (input == 0xA) {
                        schedule(owner, 2);
                        state &= 0xF;
                        return;
                    }
                    s = input == 0 ? (RANDOM.nextBoolean() ? 2 : 8) : input;
                }
                if (s == 0) s = 2;
                int out = shiftMask(s, 1);
                if ((shape & 2) == 0) out |= s;
                if ((shape & 1) != 0) out = flipMaskZ(out);
                state = state & 0xF | (out & outputMask()) << 4;
                state2 = (shape & 1) != 0 ? flipMaskZ(s) : s;
                return;
            }
            case TIMER -> {
                state &= 0xF;
                return;
            }
            case COUNTER -> {
                int out = counterValue == counterMax ? 1 : counterValue == 0 ? 4 : 0;
                state = state & 0xF | out << 4;
                return;
            }
            case STATE_CELL -> {
                int out = state2 != 0 ? 8 : 0;
                if (shape == 1) out = flipMaskZ(out);
                state = state & 0xF | out << 4;
                return;
            }
            case SYNCHRONIZER -> {
                if ((state2 & 4) == 0 && (state2 & 3) == 3) {
                    state |= 0x10;
                    state2 |= 4;
                    schedule(owner, 2);
                } else if ((state2 & 4) != 0) {
                    state &= ~0x10;
                    state2 = 0;
                }
                return;
            }
            case NULL_CELL, INVERT_CELL, BUFFER_CELL -> {
                boolean input = (state & 1) != 0;
                boolean output = !input;
                state = (state & 1) | (output ? 2 : 0);
                return;
            }
            case AND_CELL, TRANSPARENT_LATCH_CELL -> {
                int digital = state & 0xF;
                int out = type == GateType.AND_CELL
                        ? ((digital & 4) != 0 && (digital & 0xA) != 0 ? 1 : 0)
                        : ((digital & 0xA) == 0 ? state >> 4 : (digital & 4) == 0 ? 0 : 1);
                state = (state & 0xF) | out << 4;
                return;
            }
            case COMPARATOR -> {
                int oldOut = state2 & 0xF;
                int left = state2 >> 4 & 0xF;
                int back = state2 >> 8 & 0xF;
                int right = state2 >> 12 & 0xF;
                int side = Math.max(left, right);
                int out = shape == 0
                        ? (back > side ? back : 0)
                        : Math.max(back - side, 0);
                if (out != oldOut) {
                    state2 = (state2 & 0xFFF0) | out;
                    state = (state & 0xF) | (out != 0 ? 0x10 : 0);
                }
                return;
            }
            case BUS_TRANSCEIVER -> {
                int control = state & 0xF;
                if (shape == 1) control = flipMaskZ(control);
                bundleOutput0 = (control & 2) != 0 ? bundleInput2 : 0;
                bundleOutput2 = (control & 8) != 0 ? bundleInput0 : 0;
                return;
            }
            case BUS_RANDOMIZER -> {
                if ((state & 0xF) != 0) {
                    bundleOutput0 = shape == 0 ? randomOneBit(bundleMask) : randomBits(bundleMask);
                }
                return;
            }
            case BUS_CONVERTER -> {
                if (shape == 0) {
                    int analog = state2 & 0xF;
                    bundleOutput0 = 1 << analog;
                    state = (state & 0xF) | (analog != 0 ? 0xA0 : 0);
                } else {
                    int analog = mostSignificantBit(bundleInput0);
                    state2 = analog;
                    state = (state & 0xF) | (bundleInput0 != 0 ? 0xA0 : 0);
                    bundleOutput0 = 0;
                }
                return;
            }
            case BUS_INPUT_PANEL -> {
                bundleOutput2 = pressMask & 0xFFFF;
                return;
            }
            default -> {
            }
        }

        int output = calcOutput(state & inputMask()) & outputMask();
        state = state & 0xF | output << 4;
    }

    int delay() {
        return type == GateType.REPEATER
                ? REPEATER_DELAYS[Math.min(shape, REPEATER_DELAYS.length - 1)]
                : 2;
    }

    private void schedule(MultipartBlockEntity owner, int ticks) {
        if (scheduledAt < 0 && owner.getLevel() != null) {
            scheduledAt = owner.getLevel().getGameTime() + ticks;
        }
    }

    private void scheduleWithoutOwner() {
        // Interaction/config methods do not have a level reference. Zero is a
        // deferred marker converted to gameTime + 2 on the next server tick.
        // Like upstream scheduleTick(), never replace a transition that is
        // already pending.
        if (scheduledAt < 0) {
            scheduledAt = 0;
        }
    }

    int inputMask() {
        return switch (type) {
            case OR, NOR, AND, NAND -> ~shape << 1 & 0xE;
            case NOT, BUFFER, PULSE, REPEATER, RANDOMIZER, DEC_RANDOMIZER -> 4;
            case XOR, XNOR, TOGGLE_LATCH, SR_LATCH, COUNTER -> 0xA;
            case MULTIPLEXER, SYNCHRONIZER -> 0xE;
            case TRANSPARENT_LATCH -> shape == 0 ? 0xC : 6;
            case STATE_CELL -> shape == 0 ? 6 : flipMaskZ(6);
            case COMPARATOR -> 0xE;
            case BUS_TRANSCEIVER, BUS_RANDOMIZER -> 0xA;
            case BUS_CONVERTER -> shape == 0 ? 4 : 0;
            case BUS_INPUT_PANEL -> 1;
            default -> 0;
        };
    }

    private int feedbackMask() {
        return switch (type) {
            case NOR -> 1;
            case NOT, BUFFER, RANDOMIZER -> outputMask();
            case LIGHT_SENSOR -> 4;
            case DEC_RANDOMIZER -> 2;
            default -> 0;
        };
    }

    int outputMask() {
        return switch (type) {
            case OR, NOR, AND, NAND, XOR, XNOR, MULTIPLEXER, PULSE, REPEATER,
                    SYNCHRONIZER, COMPARATOR, AND_CELL, TRANSPARENT_LATCH_CELL -> 1;
            case NOT, RANDOMIZER -> notOutputMask();
            case BUFFER -> bufferOutputMask();
            case SR_LATCH -> (shape >> 1) == 0 ? 0xF : 5;
            case TOGGLE_LATCH, COUNTER -> 5;
            case TRANSPARENT_LATCH -> shape == 0 ? 3 : 9;
            case LIGHT_SENSOR, RAIN_SENSOR -> 4;
            case TIMER -> 0xB;
            case SEQUENCER -> 0xF;
            case STATE_CELL -> shape == 0 ? 9 : flipMaskZ(9);
            case DEC_RANDOMIZER -> shape == 0 ? 0xB : 9;
            case BUS_CONVERTER -> shape == 0 ? 0xA : 0xE;
            default -> 0;
        };
    }

    private int redwireMask() {
        return switch (type) {
            case NULL_CELL, INVERT_CELL, BUFFER_CELL -> 0xF;
            case AND_CELL, TRANSPARENT_LATCH_CELL -> 0xA;
            default -> 0;
        };
    }

    private int bundledInputMask() {
        return switch (type) {
            case BUS_TRANSCEIVER -> 0x5;
            case BUS_RANDOMIZER -> 4;
            case BUS_CONVERTER -> shape == 0 ? 0 : 1;
            case SEGMENT_DISPLAY -> 1;
            default -> 0;
        };
    }

    private int bundledOutputMask() {
        return switch (type) {
            case BUS_TRANSCEIVER -> 0x5;
            case BUS_RANDOMIZER -> 0x5;
            case BUS_CONVERTER -> shape == 0 ? 1 : 0;
            case BUS_INPUT_PANEL -> 4;
            default -> 0;
        };
    }

    int calcOutput(int input) {
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
            case RANDOMIZER -> input == 0 ? state >> 4 : randomBits(outputMask());
            case TRANSPARENT_LATCH -> (input & 4) == 0 ? state >> 4 : (input & 0xA) == 0 ? 0 : 0xF;
            case DEC_RANDOMIZER -> input == 0
                    ? ((state >> 4) == 0 ? 1 : state >> 4)
                    : DECODER_OUTPUTS[RANDOM.nextInt(shape == 0 ? 3 : 2)];
            default -> 0;
        };
    }

    private int notOutputMask() {
        return ~((shape & 1) << 1 | (shape & 2) >> 1 | (shape & 4) << 1) & 0xB;
    }

    private int bufferOutputMask() {
        return ~((shape & 1) << 1 | (shape & 2) << 2) & 0xB;
    }

    private static int cycleDeadSides(int shape, int deadSides, int maxDeadSides) {
        int next = shape;
        do {
            next = ADVANCE_DEAD[next];
        } while (Integer.bitCount(next) > maxDeadSides
                || 32 - Integer.numberOfLeadingZeros(next) > deadSides);
        return next;
    }

    private static int randomOneBit(int mask) {
        int count = Integer.bitCount(mask & 0xFFFF);
        if (count == 0) return 0;
        int target = RANDOM.nextInt(count);
        for (int i = 0; i < 16; i++) {
            if ((mask & (1 << i)) != 0 && target-- == 0) return 1 << i;
        }
        return 0;
    }

    private static int randomBits(int mask) {
        int out = 0;
        for (int i = 0; i < 16; i++) {
            if ((mask & (1 << i)) != 0 && RANDOM.nextBoolean()) out |= 1 << i;
        }
        return out;
    }

    private static int mostSignificantBit(int mask) {
        if ((mask & 0xFFFF) == 0) return 0;
        return 31 - Integer.numberOfLeadingZeros(mask & 0xFFFF);
    }

    private static int shiftMask(int mask, int r) {
        r &= 3;
        return ((mask << r) | (mask >> (4 - r))) & 0xF;
    }

    private static int flipMaskZ(int mask) {
        return (mask & 0x5) | ((mask & 0x2) << 2) | ((mask & 0x8) >> 2);
    }

    @Override
    public String encode() {
        return "g|" + type.id()
                + "|" + slot()
                + "|" + rotation
                + "|" + shape
                + "|" + state
                + "|" + state2
                + "|" + scheduledAt
                + "|" + timerPeriod
                + "|" + pointerStart
                + "|" + counterValue
                + "|" + counterMax
                + "|" + counterIncrement
                + "|" + counterDecrement
                + "|" + arraySignalA
                + "|" + arraySignalB
                + "|" + bundleInput0
                + "|" + bundleInput2
                + "|" + bundleOutput0
                + "|" + bundleOutput2
                + "|" + bundleMask
                + "|" + pressMask;
    }

    @Override
    public String encode(long gameTime) {
        long persistedPointer = pointerStart < 0
                ? -1
                : Math.max(0, gameTime - pointerStart);

        return "g|" + type.id()
                + "|" + slot()
                + "|" + rotation
                + "|" + shape
                + "|" + state
                + "|" + state2
                + "|" + scheduledAt
                + "|" + timerPeriod
                + "|" + persistedPointer
                + "|" + counterValue
                + "|" + counterMax
                + "|" + counterIncrement
                + "|" + counterDecrement
                + "|" + arraySignalA
                + "|" + arraySignalB
                + "|" + bundleInput0
                + "|" + bundleInput2
                + "|" + bundleOutput0
                + "|" + bundleOutput2
                + "|" + bundleMask
                + "|" + pressMask
                + "|elapsed";
    }

    public static GatePart decode(GateType type, int slot, String[] p) {
        GatePart part = new GatePart(type, slot, intAt(p, 3, 0));
        part.shape = intAt(p, 4, part.shape);
        part.state = intAt(p, 5, part.state);
        part.state2 = intAt(p, 6, part.state2);
        part.scheduledAt = longAt(p, 7, -1);
        part.timerPeriod = intAt(p, 8, 40);
        part.pointerStart = longAt(p, 9, -1);
        part.pointerNeedsWorldTimeRebase =
                p.length > 22 && "elapsed".equals(p[22]);
        part.counterValue = intAt(p, 10, 0);
        part.counterMax = intAt(p, 11, 10);
        part.counterIncrement = intAt(p, 12, 1);
        part.counterDecrement = intAt(p, 13, 1);
        part.arraySignalA = intAt(p, 14, 0);
        part.arraySignalB = intAt(p, 15, 0);
        part.bundleInput0 = intAt(p, 16, 0);
        part.bundleInput2 = intAt(p, 17, 0);
        part.bundleOutput0 = intAt(p, 18, 0);
        part.bundleOutput2 = intAt(p, 19, 0);
        part.bundleMask = intAt(p, 20, 0xFFFF);
        part.pressMask = intAt(p, 21, 0);
        return part;
    }

    private static void playTickSound(
            MultipartBlockEntity owner
    ) {
        if (owner.getLevel() == null || owner.getLevel().isClientSide()) {
            return;
        }

        owner.getLevel().playSound(
                null,
                owner.getBlockPos(),
                SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS,
                0.15F,
                0.5F
        );
    }

    private static int clampAdd(
            int value,
            int delta,
            int minimum,
            int maximum
    ) {
        long sum = (long) value + delta;
        return (int) Math.max(minimum, Math.min((long) maximum, sum));
    }

    private static int intAt(String[] p, int index, int fallback) {
        return p.length > index && !p[index].isEmpty() ? Integer.parseInt(p[index]) : fallback;
    }

    private static long longAt(String[] p, int index, long fallback) {
        return p.length > index && !p[index].isEmpty() ? Long.parseLong(p[index]) : fallback;
    }
}
