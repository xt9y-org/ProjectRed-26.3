package dev.xt9y.projectred.multipart;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.core.BundledSignals;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.transmission.WireFamily;
import dev.xt9y.projectred.transmission.WirePart;
import dev.xt9y.projectred.transmission.WireSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MultipartBlockEntity extends BlockEntity {
    private final Part[] face = new Part[6];
    private Part center;

    public MultipartBlockEntity(BlockPos pos, BlockState state) {
        super(PRContent.MULTIPART_BE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MultipartBlockEntity be) {
        if (level.isClientSide()) return;

        boolean changed = false;
        List<Integer> unsupported = new ArrayList<>();

        for (Part part : be.parts()) {
            if (!part.center() && !be.hasSupport(part.slot())) {
                unsupported.add(part.slot());
                continue;
            }
            if (part instanceof WirePart wire) changed |= wire.recompute(be);
            if (part instanceof GatePart gate) changed |= gate.tick(be);
        }

        for (int slot : unsupported) be.removeAndDrop(slot);
        if (changed) {
            be.syncChanged();
            be.propagateConnectedSignals();
        }
    }

    private void propagateConnectedSignals() {
        if (level == null || level.isClientSide()) return;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> queued = new HashSet<>();

        enqueuePropagationNeighborhood(queue, queued, worldPosition);

        int operations = 0;
        final int maxOperations = 65_536;

        while (!queue.isEmpty() && operations++ < maxOperations) {
            BlockPos pos = queue.removeFirst();
            queued.remove(pos.asLong());

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof MultipartBlockEntity multipart)) {
                continue;
            }

            boolean localChanged = false;
            for (Part part : multipart.parts()) {
                if (part instanceof WirePart wire) {
                    localChanged |= wire.recompute(multipart);
                }
            }

            if (!localChanged) continue;

            multipart.syncChanged();
            enqueuePropagationNeighborhood(queue, queued, pos);
        }

        if (operations >= maxOperations) {
            dev.xt9y.projectred.ProjectRed263.LOGGER.warn(
                    "ProjectRed signal propagation reached operation cap near {}",
                    worldPosition
            );
        }
    }

    private static void enqueuePropagationNeighborhood(
            ArrayDeque<BlockPos> queue,
            Set<Long> queued,
            BlockPos center
    ) {
        // Straight connections plus ProjectRed outer-corner wrapping all fit
        // inside the surrounding 3x3x3 cube. Queueing that compact
        // neighborhood keeps the algorithm topology-independent while the
        // actual wire recomputation still enforces connection rules.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue;

                    BlockPos pos = center.offset(dx, dy, dz);
                    if (queued.add(pos.asLong())) {
                        queue.addLast(pos);
                    }
                }
            }
        }

        if (queued.add(center.asLong())) {
            queue.addLast(center);
        }
    }

    public List<Part> parts() {
        List<Part> out = new ArrayList<>(7);
        if (center != null) out.add(center);
        for (Part p : face) if (p != null) out.add(p);
        return out;
    }

    public boolean hasSlot(int slot) {
        return slot == Part.CENTER_SLOT ? center != null : slot >= 0 && slot < 6 && face[slot] != null;
    }

    public Part part(int slot) {
        return slot == Part.CENTER_SLOT ? center : slot >= 0 && slot < 6 ? face[slot] : null;
    }

    public boolean add(Part part) {
        if (part == null || hasSlot(part.slot())) return false;
        if (part.center()) center = part; else face[part.slot()] = part;
        syncChanged();
        return true;
    }

    public Part remove(int slot) {
        Part old;
        if (slot == Part.CENTER_SLOT) { old = center; center = null; }
        else if (slot >= 0 && slot < 6) { old = face[slot]; face[slot] = null; }
        else return null;

        if (old != null) {
            syncChanged();
            if (parts().isEmpty() && level != null) level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
        }
        return old;
    }

    public boolean removeAndDrop(int slot) {
        if (level == null) return false;

        Part removed = remove(slot);
        if (removed == null) return false;

        net.minecraft.world.item.ItemStack stack = PRContent.stackFor(removed);
        if (!stack.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(
                    level,
                    worldPosition,
                    stack
            );
        }
        return true;
    }

    public int slotFromHit(Vec3 hit) {
        double x = hit.x - worldPosition.getX();
        double y = hit.y - worldPosition.getY();
        double z = hit.z - worldPosition.getZ();
        if (center != null && x > .32 && x < .68 && y > .32 && y < .68 && z > .32 && z < .68) return Part.CENTER_SLOT;
        double[] d = { y, 1-y, z, 1-z, x, 1-x };
        int best = -1;
        double dist = Double.MAX_VALUE;
        for (int i=0;i<6;i++) if (face[i] != null && d[i] < dist) { best=i; dist=d[i]; }
        return best;
    }

    public int panelBit(GatePart gate, Vec3 hit) {
        Direction attachment = Direction.values()[gate.slot()];
        Direction right = localToWorld(attachment, gate.rotation(), 1);
        Direction down = localToWorld(attachment, gate.rotation(), 2);

        double cx = hit.x - worldPosition.getX() - 0.5;
        double cy = hit.y - worldPosition.getY() - 0.5;
        double cz = hit.z - worldPosition.getZ() - 0.5;

        double u = 0.5
                + cx * right.getStepX()
                + cy * right.getStepY()
                + cz * right.getStepZ();
        double v = 0.5
                + cx * down.getStepX()
                + cy * down.getStepY()
                + cz * down.getStepZ();

        int column = Math.max(0, Math.min(3, (int) Math.floor(u * 4.0)));
        int row = Math.max(0, Math.min(3, (int) Math.floor(v * 4.0)));
        return row * 4 + column;
    }

    private boolean hasSupport(int slot) {
        Direction attachment = Direction.values()[slot];
        BlockPos support = worldPosition.relative(attachment);
        return level != null && level.getBlockState(support).isFaceSturdy(level, support, attachment.getOpposite());
    }

    public VoxelShape shape() {
        VoxelShape out = Shapes.empty();
        if (center != null) out = Shapes.or(out, Shapes.box(.375,.375,.375,.625,.625,.625));
        for (int i=0;i<6;i++) if (face[i] != null) out = Shapes.or(out, faceShape(Direction.values()[i]));
        return out.isEmpty() ? Shapes.block() : out;
    }

    private static VoxelShape faceShape(Direction d) {
        return switch (d) {
            case DOWN -> Shapes.box(.0625,0,.0625,.9375,.125,.9375);
            case UP -> Shapes.box(.0625,.875,.0625,.9375,1,.9375);
            case NORTH -> Shapes.box(.0625,.0625,0,.9375,.9375,.125);
            case SOUTH -> Shapes.box(.0625,.0625,.875,.9375,.9375,1);
            case WEST -> Shapes.box(0,.0625,.0625,.125,.9375,.9375);
            case EAST -> Shapes.box(.875,.0625,.0625,1,.9375,.9375);
        };
    }

    public int calculateRedwireInput(WirePart receiver) {
        int max = internalRedwireInput(receiver);

        if (level == null) return max;

        if (receiver.center()) {
            for (Direction direction : Direction.values()) {
                max = Math.max(max, readStraightRedwire(receiver, direction, null));
            }
            return Math.max(0, Math.min(255, max));
        }

        Direction attachment = Direction.values()[receiver.slot()];
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == attachment.getAxis()) continue;

            int straight = readStraightRedwire(receiver, direction, attachment);
            if (straight > 0) {
                max = Math.max(max, straight);
            } else {
                max = Math.max(max, readCornerRedwire(receiver, direction, attachment));
            }
        }

        // Red-alloy wire is the only transmission wire that powers through
        // its support face in upstream ProjectRed.
        if (receiver.spec().family() == WireFamily.RED_ALLOY) {
            BlockPos support = worldPosition.relative(attachment);
            max = Math.max(max, level.getSignal(support, attachment) * 17);
        }

        return Math.max(0, Math.min(255, max));
    }

    private int internalRedwireInput(WirePart receiver) {
        int max = 0;
        for (Part p : parts()) {
            if (p == receiver || !internallyTouches(receiver, p)) continue;

            if (p instanceof WirePart other) {
                if (!receiver.canConnect(other)) continue;

                if (other.spec().family() == WireFamily.BUNDLED
                        && receiver.spec().family() == WireFamily.INSULATED) {
                    max = Math.max(
                            max,
                            Math.max(0, other.bundled()[receiver.spec().color()] - 1)
                    );
                } else {
                    max = Math.max(max, Math.max(0, other.signal() - 1));
                }
            } else if (p instanceof GatePart gate) {
                if (receiver.center()) {
                    for (int local = 0; local < 4; local++) {
                        max = Math.max(max, Math.max(0, gate.outputRawLocal(local) - 1));
                    }
                } else {
                    Direction toward = Direction.values()[receiver.slot()];
                    max = Math.max(
                            max,
                            Math.max(0, gateRawOutputToward(gate, toward) - 1)
                    );
                }
            }
        }
        return max;
    }

    private int readStraightRedwire(
            WirePart receiver,
            Direction direction,
            Direction expectedAttachment
    ) {
        BlockPos neighborPos = worldPosition.relative(direction);
        BlockEntity neighbor = level.getBlockEntity(neighborPos);

        if (neighbor instanceof MultipartBlockEntity multipart) {
            int raw = multipart.redwireToward(
                    direction.getOpposite(),
                    receiver.spec(),
                    expectedAttachment
            );
            return Math.max(0, raw - 1);
        }

        return level.getSignal(neighborPos, direction) * 17;
    }

    private int readCornerRedwire(
            WirePart receiver,
            Direction direction,
            Direction attachment
    ) {
        BlockPos cornerPos = worldPosition.relative(direction).relative(attachment);
        BlockEntity corner = level.getBlockEntity(cornerPos);
        if (!(corner instanceof MultipartBlockEntity multipart)) return 0;

        int raw = multipart.cornerRedwireSignal(
                direction.getOpposite(),
                receiver.spec()
        );
        return Math.max(0, raw - 1);
    }

    public int[] calculateBundledInput(WirePart receiver) {
        int[] out = internalBundledInput(receiver);

        if (level == null) return out;

        if (receiver.center()) {
            for (Direction direction : Direction.values()) {
                BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
                if (neighbor instanceof MultipartBlockEntity multipart) {
                    out = BundledSignals.raise(
                            out,
                            multipart.bundledToward(
                                    direction.getOpposite(),
                                    receiver.spec(),
                                    null
                            ),
                            true
                    );
                }
            }
            return out;
        }

        Direction attachment = Direction.values()[receiver.slot()];
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == attachment.getAxis()) continue;

            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            boolean straightFound = false;
            if (neighbor instanceof MultipartBlockEntity multipart) {
                int[] straight = multipart.bundledToward(
                        direction.getOpposite(),
                        receiver.spec(),
                        attachment
                );
                if (!BundledSignals.isZero(straight)) {
                    out = BundledSignals.raise(out, straight, true);
                    straightFound = true;
                }
            }

            if (!straightFound) {
                BlockPos cornerPos = worldPosition.relative(direction).relative(attachment);
                BlockEntity corner = level.getBlockEntity(cornerPos);
                if (corner instanceof MultipartBlockEntity multipart) {
                    int[] cornerSignal = multipart.cornerBundledSignal(
                            direction.getOpposite(),
                            receiver.spec()
                    );
                    out = BundledSignals.raise(out, cornerSignal, true);
                }
            }
        }
        return out;
    }

    private int[] internalBundledInput(WirePart receiver) {
        int[] out = new int[16];
        for (Part p : parts()) {
            if (p == receiver || !internallyTouches(receiver, p)) continue;

            if (p instanceof WirePart other && receiver.canConnect(other)) {
                out = BundledSignals.raise(out, other.bundledSignal(), true);
            } else if (p instanceof GatePart gate) {
                if (receiver.center()) {
                    for (int local = 0; local < 4; local++) {
                        out = BundledSignals.raise(out, gate.bundledOutputLocal(local), false);
                    }
                } else {
                    out = BundledSignals.raise(
                            out,
                            gateBundledOutputToward(gate, Direction.values()[receiver.slot()]),
                            false
                    );
                }
            }
        }
        return out;
    }

    private int redwireToward(
            Direction toward,
            WireSpec receiver,
            Direction expectedAttachment
    ) {
        int max = 0;
        for (Part p : parts()) {
            if (!partConnectsToward(p, toward)) continue;

            if (p instanceof WirePart wire) {
                if (!straightAttachmentMatches(wire, expectedAttachment)) continue;

                if (wire.spec().family() == WireFamily.BUNDLED
                        && receiver.family() == WireFamily.INSULATED) {
                    max = Math.max(max, wire.bundled()[receiver.color()]);
                } else if (receiver.redwireCompatible(wire.spec())) {
                    max = Math.max(max, wire.signal());
                }
            } else if (p instanceof GatePart gate) {
                if (expectedAttachment != null
                        && Direction.values()[gate.slot()] != expectedAttachment) {
                    continue;
                }
                max = Math.max(max, gateRawOutputToward(gate, toward));
            }
        }
        return max;
    }

    private int cornerRedwireSignal(
            Direction expectedAttachment,
            WireSpec receiver
    ) {
        Part p = face[expectedAttachment.ordinal()];
        if (p instanceof WirePart wire) {
            if (wire.spec().family() == WireFamily.BUNDLED
                    && receiver.family() == WireFamily.INSULATED) {
                return wire.bundled()[receiver.color()];
            }
            return receiver.redwireCompatible(wire.spec()) ? wire.signal() : 0;
        }
        return 0;
    }

    private int[] bundledToward(
            Direction toward,
            WireSpec receiver,
            Direction expectedAttachment
    ) {
        int[] out = new int[16];
        for (Part p : parts()) {
            if (!partConnectsToward(p, toward)) continue;

            if (p instanceof WirePart wire) {
                if (!straightAttachmentMatches(wire, expectedAttachment)) continue;

                if (wire.spec().family() == WireFamily.BUNDLED
                        && receiver.bundledCompatible(wire.spec())) {
                    out = BundledSignals.raise(out, wire.bundled(), false);
                } else if (wire.spec().family() == WireFamily.INSULATED) {
                    int[] one = new int[16];
                    one[wire.spec().color()] = wire.signal();
                    out = BundledSignals.raise(out, one, false);
                }
            } else if (p instanceof GatePart gate) {
                if (expectedAttachment != null
                        && Direction.values()[gate.slot()] != expectedAttachment) {
                    continue;
                }
                out = BundledSignals.raise(
                        out,
                        gateBundledOutputToward(gate, toward),
                        false
                );
            }
        }
        return out;
    }

    private int[] cornerBundledSignal(
            Direction expectedAttachment,
            WireSpec receiver
    ) {
        Part p = face[expectedAttachment.ordinal()];
        if (p instanceof WirePart wire) {
            if (wire.spec().family() == WireFamily.BUNDLED
                    && receiver.bundledCompatible(wire.spec())) {
                return wire.bundled();
            }
            if (wire.spec().family() == WireFamily.INSULATED) {
                int[] out = new int[16];
                out[wire.spec().color()] = wire.signal();
                return out;
            }
        }
        return null;
    }

    private static boolean straightAttachmentMatches(
            WirePart wire,
            Direction expectedAttachment
    ) {
        if (expectedAttachment == null || wire.center()) return true;
        return Direction.values()[wire.slot()] == expectedAttachment;
    }

    private static boolean partConnectsToward(Part part, Direction toward) {
        if (part.center()) return true;
        Direction attachment = Direction.values()[part.slot()];
        return attachment.getAxis() != toward.getAxis();
    }

    private static boolean internallyTouches(Part a, Part b) {
        if (a.center() || b.center()) return true;
        Direction da = Direction.values()[a.slot()];
        Direction db = Direction.values()[b.slot()];
        return da.getAxis() != db.getAxis();
    }

    private static boolean internalWireMeetsDirection(
            WirePart wire,
            Direction direction
    ) {
        return wire.center()
                || Direction.values()[wire.slot()] == direction;
    }

    public int visualWireConnections(WirePart receiver) {
        if (level == null) return 0;

        int mask = 0;
        Direction attachment = receiver.center()
                ? null
                : Direction.values()[receiver.slot()];

        for (Direction direction : Direction.values()) {
            if (attachment != null && direction.getAxis() == attachment.getAxis()) {
                continue;
            }

            boolean connected =
                    attachment != null
                            && hasInsideWireConnection(receiver, direction);

            if (!connected) {
                connected = hasStraightWireConnection(
                        receiver,
                        direction,
                        attachment
                );
            }

            if (!connected && attachment != null) {
                connected = hasCornerWireConnection(
                        receiver,
                        direction,
                        attachment
                );
            }

            if (connected) {
                mask |= 1 << direction.ordinal();
            }
        }
        return mask;
    }

    private boolean hasInsideWireConnection(
            WirePart receiver,
            Direction direction
    ) {
        Part inside = face[direction.ordinal()];

        if (inside instanceof WirePart other) {
            return receiver.canConnect(other);
        }

        if (inside instanceof GatePart gate) {
            return gateConnectsToward(
                    gate,
                    Direction.values()[receiver.slot()].getOpposite(),
                    receiver.spec().family() == WireFamily.BUNDLED
            );
        }

        return false;
    }

    private boolean hasStraightWireConnection(
            WirePart receiver,
            Direction direction,
            Direction expectedAttachment
    ) {
        BlockPos neighborPos = worldPosition.relative(direction);
        BlockEntity neighbor = level.getBlockEntity(neighborPos);

        if (neighbor instanceof MultipartBlockEntity multipart) {
            for (Part p : multipart.parts()) {
                if (!partConnectsToward(p, direction.getOpposite())) continue;

                if (p instanceof WirePart other) {
                    if (!straightAttachmentMatches(other, expectedAttachment)) continue;
                    if (receiver.canConnect(other)) return true;
                } else if (p instanceof GatePart gate) {
                    if (expectedAttachment != null
                            && Direction.values()[gate.slot()] != expectedAttachment) {
                        continue;
                    }
                    if (gateConnectsToward(
                            gate,
                            direction.getOpposite(),
                            receiver.spec().family() == WireFamily.BUNDLED
                    )) {
                        return true;
                    }
                }
            }
            return false;
        }

        return receiver.spec().family() != WireFamily.BUNDLED
                && level.getBlockState(neighborPos).isSignalSource();
    }

    private boolean hasCornerWireConnection(
            WirePart receiver,
            Direction direction,
            Direction attachment
    ) {
        BlockPos cornerPos = worldPosition.relative(direction).relative(attachment);
        BlockEntity corner = level.getBlockEntity(cornerPos);
        if (!(corner instanceof MultipartBlockEntity multipart)) return false;

        Part p = multipart.part(direction.getOpposite().ordinal());
        if (p instanceof WirePart other) {
            return receiver.canConnect(other);
        }
        return false;
    }

    private static boolean gateConnectsToward(
            GatePart gate,
            Direction toward,
            boolean bundled
    ) {
        Direction attachment = Direction.values()[gate.slot()];
        if (toward.getAxis() == attachment.getAxis()) return false;

        for (int local = 0; local < 4; local++) {
            if (localToWorld(attachment, gate.rotation(), local) != toward) continue;
            return bundled
                    ? gate.canConnectBundledLocal(local)
                    : gate.canConnectLocal(local);
        }
        return false;
    }

    public int gateRedwireRawInput(GatePart receiver, int local) {
        Direction direction = localToWorld(
                Direction.values()[receiver.slot()],
                receiver.rotation(),
                local
        );
        int max = 0;

        for (Part p : parts()) {
            if (p == receiver) continue;
            if (p instanceof WirePart wire
                    && wire.spec().family() != WireFamily.BUNDLED
                    && internalWireMeetsDirection(wire, direction)) {
                max = Math.max(max, wire.signal());
            } else if (p instanceof GatePart gate) {
                max = Math.max(max, gateRawOutputToward(gate, direction.getOpposite()));
            }
        }

        if (level == null) return max;

        BlockPos neighborPos = worldPosition.relative(direction);
        BlockEntity neighbor = level.getBlockEntity(neighborPos);
        if (neighbor instanceof MultipartBlockEntity multipart) {
            max = Math.max(max, multipart.rawSignal(direction.getOpposite()));
        } else {
            max = Math.max(max, level.getSignal(neighborPos, direction) * 17);
        }
        return max;
    }

    public int gateAnalogInput(GatePart receiver, int local) {
        Direction direction = localToWorld(
                Direction.values()[receiver.slot()],
                receiver.rotation(),
                local
        );
        int result = Math.min(15, (gateRedwireRawInput(receiver, local) + 16) / 17);

        if (level == null) return result;

        BlockPos neighborPos = worldPosition.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.hasAnalogOutputSignal()) {
            result = Math.max(
                    result,
                    neighborState.getAnalogOutputSignal(level, neighborPos, direction.getOpposite())
            );
        }
        return Math.max(0, Math.min(15, result));
    }

    public int gateComparatorBackInput(GatePart receiver) {
        Direction direction = localToWorld(
                Direction.values()[receiver.slot()],
                receiver.rotation(),
                2
        );

        int result = gateAnalogInput(receiver, 2);
        if (level == null || result >= 15) return result;

        BlockPos firstPos = worldPosition.relative(direction);
        BlockState firstState = level.getBlockState(firstPos);

        if (!firstState.isRedstoneConductor(level, firstPos)) {
            return result;
        }

        BlockPos secondPos = firstPos.relative(direction);
        BlockState secondState = level.getBlockState(secondPos);

        if (secondState.hasAnalogOutputSignal()) {
            result = Math.max(
                    result,
                    secondState.getAnalogOutputSignal(
                            level,
                            secondPos,
                            direction.getOpposite()
                    )
            );
        }

        AABB box = new AABB(secondPos);
        List<ItemFrame> frames = level.getEntitiesOfClass(
                ItemFrame.class,
                box,
                frame -> frame.getDirection() == direction
        );
        if (frames.size() == 1) {
            result = Math.max(result, frames.getFirst().getAnalogOutput());
        }

        return Math.max(0, Math.min(15, result));
    }

    public int[] gateBundledInput(GatePart receiver, int local) {
        Direction direction = localToWorld(
                Direction.values()[receiver.slot()],
                receiver.rotation(),
                local
        );
        int[] out = new int[16];

        for (Part p : parts()) {
            if (p == receiver) continue;
            if (p instanceof WirePart wire && internalWireMeetsDirection(wire, direction)) {
                out = BundledSignals.raise(out, wire.bundledSignal(), false);
            } else if (p instanceof GatePart gate) {
                out = BundledSignals.raise(
                        out,
                        gateBundledOutputToward(gate, direction.getOpposite()),
                        false
                );
            }
        }

        if (level != null) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbor instanceof MultipartBlockEntity multipart) {
                out = BundledSignals.raise(
                        out,
                        multipart.bundledSignal(direction.getOpposite()),
                        false
                );
            }
        }
        return out;
    }

    public int gateInput(GatePart gate, int mask) {
        int input = 0;
        for (int r=0;r<4;r++) {
            if ((mask & 1 << r) == 0) continue;
            Direction worldDir = localToWorld(Direction.values()[gate.slot()], gate.rotation(), r);
            int signal = gateInputToward(gate, worldDir);
            if (signal > 0) input |= 1 << r;
        }
        return input;
    }

    private int gateInputToward(GatePart receiver, Direction worldDir) {
        int max = 0;
        for (Part p : parts()) {
            if (p == receiver) continue;
            if (p instanceof WirePart wire && internalWireMeetsDirection(wire, worldDir)) {
                max = Math.max(max, wire.vanillaSignal());
            }
            if (p instanceof GatePart other) {
                max = Math.max(max, gateOutputToward(other, worldDir.getOpposite()));
            }
        }
        if (level != null) {
            BlockPos np = worldPosition.relative(worldDir);
            max = Math.max(max, level.getSignal(np, worldDir));
            BlockEntity nbe = level.getBlockEntity(np);
            if (nbe instanceof MultipartBlockEntity mp) max = Math.max(max, mp.vanillaSignal(worldDir.getOpposite()));
        }
        return max;
    }

    private int gateOutputToward(GatePart gate, Direction toward) {
        return Math.min(15, (gateRawOutputToward(gate, toward) + 16) / 17);
    }

    private int gateRawOutputToward(GatePart gate, Direction toward) {
        Direction attachment = Direction.values()[gate.slot()];
        if (toward.getAxis() == attachment.getAxis()) return 0;
        for (int r = 0; r < 4; r++) {
            if (localToWorld(attachment, gate.rotation(), r) == toward) {
                return gate.outputRawLocal(r);
            }
        }
        return 0;
    }

    private int[] gateBundledOutputToward(GatePart gate, Direction toward) {
        Direction attachment = Direction.values()[gate.slot()];
        if (toward.getAxis() == attachment.getAxis()) return null;
        for (int r = 0; r < 4; r++) {
            if (localToWorld(attachment, gate.rotation(), r) == toward) {
                return gate.bundledOutputLocal(r);
            }
        }
        return null;
    }

    public int rawSignal(Direction toward) {
        int max = 0;
        for (Part p : parts()) {
            if (!partConnectsToward(p, toward)) continue;

            if (p instanceof WirePart wire
                    && wire.spec().family() != WireFamily.BUNDLED) {
                max = Math.max(max, wire.signal());
            } else if (p instanceof GatePart gate) {
                max = Math.max(max, gateRawOutputToward(gate, toward));
            }
        }
        return max;
    }

    public int[] bundledSignal(Direction toward) {
        int[] out = new int[16];
        for (Part p : parts()) {
            if (!partConnectsToward(p, toward)) continue;

            if (p instanceof WirePart wire) {
                out = BundledSignals.raise(out, wire.bundledSignal(), false);
            } else if (p instanceof GatePart gate) {
                out = BundledSignals.raise(
                        out,
                        gateBundledOutputToward(gate, toward),
                        false
                );
            }
        }
        return out;
    }

    public int vanillaSignal(Direction toward) {
        int max = 0;
        for (Part p : parts()) {
            if (!partConnectsToward(p, toward)) continue;

            if (p instanceof WirePart wire
                    && wire.spec().family() != WireFamily.BUNDLED) {
                max = Math.max(max, wire.vanillaSignal());
            }
            if (p instanceof GatePart gate) {
                max = Math.max(max, gateOutputToward(gate, toward));
            }
        }
        return max;
    }

    public static Direction localToWorld(Direction attachment, int rotation, int local) {
        Direction[] dirs = switch (attachment) {
            case DOWN -> new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            case UP -> new Direction[] {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
            case NORTH -> new Direction[] {Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST};
            case SOUTH -> new Direction[] {Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST};
            case WEST -> new Direction[] {Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH};
            case EAST -> new Direction[] {Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH};
        };
        return dirs[(local + rotation) & 3];
    }

    public void markPartChanged() {
        syncChanged();
    }

    private void syncChanged() {
        setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        List<Part> all = parts();
        output.putInt("part_count", all.size());
        for (int i=0;i<all.size();i++) output.putString("part_" + i, all.get(i).encode());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Arrays.fill(face, null);
        center = null;
        int count = Math.max(0, Math.min(7, input.getIntOr("part_count", 0)));
        for (int i=0;i<count;i++) {
            String encoded = input.getStringOr("part_" + i, "");
            if (encoded.isEmpty()) continue;
            try {
                Part p = Part.decode(encoded);
                if (!hasSlot(p.slot())) {
                    if (p.center()) center = p; else face[p.slot()] = p;
                }
            } catch (RuntimeException ignored) {}
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
