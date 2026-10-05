package dev.xt9y.projectred.multipart;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.core.BundledSignals;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.integration.GateType;
import dev.xt9y.projectred.transmission.WireFamily;
import dev.xt9y.projectred.transmission.WirePart;
import dev.xt9y.projectred.transmission.WireSpec;
import dev.xt9y.projectred.transmission.RedwirePowerContext;
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
import net.minecraft.world.level.block.RedstoneWireBlock;
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
    private boolean handlingNeighborSignalChange;
    private boolean initialSignalRefreshDone;

    public MultipartBlockEntity(BlockPos pos, BlockState state) {
        super(PRContent.MULTIPART_BE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MultipartBlockEntity be) {
        if (level.isClientSide()) return;

        if (!be.initialSignalRefreshDone) {
            be.initialSignalRefreshDone = true;
            be.onNeighborSignalChanged();
            return;
        }

        boolean changed = false;
        List<Integer> unsupported = new ArrayList<>();

        for (Part part : be.parts()) {
            if (!part.center() && !be.hasSupport(part)) {
                unsupported.add(part.slot());
                continue;
            }

            // Wires are event-driven, and idle logic gates are too.
            // Tick only timers/sensors or gates with pending delayed work.
            if (part instanceof GatePart gate && gate.needsServerTick()) {
                gate.restoreWorldTimeBase(be);
                changed |= gate.tick(be);
            }
        }

        for (int slot : unsupported) {
            changed |= be.removeAndDrop(slot);
        }

        if (changed && level.getBlockEntity(pos) == be) {
            be.syncChanged();
            be.propagateConnectedSignals();
        }
    }

    public void onNeighborSignalChanged() {
        if (level == null
                || level.isClientSide()
                || handlingNeighborSignalChange) {
            return;
        }

        handlingNeighborSignalChange = true;
        try {
            boolean changed = false;
            List<Integer> unsupported = new ArrayList<>();

            for (Part part : parts()) {
                if (!part.center() && !hasSupport(part)) {
                    unsupported.add(part.slot());
                    continue;
                }

                if (part instanceof WirePart wire) {
                    changed |= wire.recompute(this);
                } else if (part instanceof GatePart gate) {
                    gate.restoreWorldTimeBase(this);
                    changed |= gate.tick(this);
                }
            }

            for (int slot : unsupported) {
                changed |= removeAndDrop(slot);
            }

            if (changed && level.getBlockEntity(worldPosition) == this) {
                syncChanged();
                propagateConnectedSignals();
            }
        } finally {
            handlingNeighborSignalChange = false;
        }
    }

    private void propagateConnectedSignals() {
        if (level == null || level.isClientSide()) return;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> queued = new HashSet<>();
        enqueuePropagationTargets(queue, queued, this);

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
                } else if (part instanceof GatePart gate) {
                    gate.restoreWorldTimeBase(multipart);
                    localChanged |= gate.tick(multipart);
                }
            }

            if (!localChanged) continue;

            multipart.syncChanged();
            enqueuePropagationTargets(queue, queued, multipart);
        }

        if (operations >= maxOperations) {
            dev.xt9y.projectred.ProjectRed263.LOGGER.warn(
                    "ProjectRed signal propagation reached operation cap near {}",
                    worldPosition
            );
        }
    }

    private void enqueuePropagationTargets(
            ArrayDeque<BlockPos> queue,
            Set<Long> queued,
            MultipartBlockEntity multipart
    ) {
        enqueueMultipart(queue, queued, multipart.worldPosition);

        for (Part part : multipart.parts()) {
            if (part instanceof WirePart wire) {
                enqueueWireTargets(queue, queued, multipart, wire);
            } else if (part instanceof GatePart gate) {
                enqueueGateTargets(queue, queued, multipart, gate);
            }
        }
    }

    private void enqueueWireTargets(
            ArrayDeque<BlockPos> queue,
            Set<Long> queued,
            MultipartBlockEntity multipart,
            WirePart wire
    ) {
        int connections = multipart.visualWireConnections(wire);
        Direction attachment = wire.center()
                ? null
                : Direction.values()[wire.slot()];

        for (Direction direction : Direction.values()) {
            if ((connections & (1 << direction.ordinal())) == 0) continue;

            BlockPos straight = multipart.worldPosition.relative(direction);
            enqueueMultipart(queue, queued, straight);

            if (attachment != null
                    && direction.getAxis() != attachment.getAxis()
                    && multipart.outsideCornerEdgeOpen(direction, attachment)) {
                enqueueMultipart(
                        queue,
                        queued,
                        straight.relative(attachment)
                );
            }
        }
    }

    private void enqueueGateTargets(
            ArrayDeque<BlockPos> queue,
            Set<Long> queued,
            MultipartBlockEntity multipart,
            GatePart gate
    ) {
        Direction attachment = Direction.values()[gate.slot()];

        for (int local = 0; local < 4; local++) {
            if (!gate.canConnectRedstoneLocal(local)
                    && !gate.canConnectBundledLocal(local)) {
                continue;
            }

            Direction direction = localToWorld(
                    attachment,
                    gate.rotation(),
                    local
            );
            BlockPos straight = multipart.worldPosition.relative(direction);
            enqueueMultipart(queue, queued, straight);

            if (multipart.outsideCornerEdgeOpen(direction, attachment)) {
                enqueueMultipart(
                        queue,
                        queued,
                        straight.relative(attachment)
                );
            }
        }
    }

    private void enqueueMultipart(
            ArrayDeque<BlockPos> queue,
            Set<Long> queued,
            BlockPos pos
    ) {
        if (!(level.getBlockEntity(pos) instanceof MultipartBlockEntity)) {
            return;
        }

        long key = pos.asLong();
        if (queued.add(key)) {
            queue.addLast(pos);
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

    public void preparePlacement(Part part) {
        if (!(part instanceof GatePart gate)
                || gate.type() != GateType.NULL_CELL
                || part.center()) {
            return;
        }

        Direction side = Direction.values()[part.slot()];
        Part opposite = face[side.getOpposite().ordinal()];
        if (opposite instanceof GatePart other
                && other.type() == gate.type()
                && (other.rotation() & 1) == (gate.rotation() & 1)) {
            // ArrayGatePart::preparePlacement() automatically rotates a
            // crossing Null Cell so the two independent redwire paths are
            // perpendicular instead of occupying the same track.
            gate.rotate();
        }
    }

    public boolean canAdd(Part part) {
        return part != null
                && !hasSlot(part.slot())
                && fitsWithExistingParts(part, null);
    }

    public boolean canRemainAfterGeometryChange(Part part) {
        return part != null
                && part(part.slot()) == part
                && fitsWithExistingParts(part, part);
    }

    private boolean fitsWithExistingParts(
            Part candidate,
            Part ignored
    ) {
        if (candidate.center()) {
            for (Part existing : face) {
                if (existing == ignored) continue;
                if (isArrayCell(existing)) return false;
            }
            return true;
        }

        Direction candidateSide = Direction.values()[candidate.slot()];
        boolean candidateArray = isArrayCell(candidate);

        if (candidateArray && center != null && center != ignored) {
            return false;
        }

        for (Part existing : face) {
            if (existing == null || existing == ignored) continue;

            boolean existingArray = isArrayCell(existing);
            Direction existingSide = Direction.values()[existing.slot()];

            if (!candidateArray && !existingArray) {
                if (candidate instanceof GatePart
                        && existing instanceof GatePart
                        && candidateSide.getAxis() != existingSide.getAxis()) {
                    // GatePart's upstream cross-shaped occlusion volume
                    // intersects another gate mounted on a perpendicular
                    // face. Opposite-face gates remain valid.
                    return false;
                }
                continue;
            }

            if (candidateArray && existingArray) {
                if (!arrayCellsCanCross(candidate, existing)) {
                    return false;
                }
                continue;
            }

            // The 6/8-deep array body can coexist only with a normal thin
            // part on the directly opposite face.
            if (candidateArray && existingSide != candidateSide.getOpposite()) {
                return false;
            }
            if (existingArray && candidateSide != existingSide.getOpposite()) {
                return false;
            }
        }
        return true;
    }

    private static boolean arrayCellsCanCross(Part a, Part b) {
        if (!(a instanceof GatePart ga) || !(b instanceof GatePart gb)) {
            return false;
        }

        Direction sa = Direction.values()[a.slot()];
        Direction sb = Direction.values()[b.slot()];

        return ga.type() == gb.type()
                && sb == sa.getOpposite()
                && (ga.rotation() & 1) != (gb.rotation() & 1);
    }

    public boolean add(Part part) {
        if (!canAdd(part)) return false;
        if (part.center()) center = part; else face[part.slot()] = part;

        if (part instanceof GatePart gate
                && level != null
                && !level.isClientSide()) {
            gate.onAdded(this);
        }

        syncChanged();
        if (level != null && !level.isClientSide()) {
            propagateConnectedSignals();
        }
        return true;
    }

    public Part remove(int slot) {
        Part old;
        if (slot == Part.CENTER_SLOT) { old = center; center = null; }
        else if (slot >= 0 && slot < 6) { old = face[slot]; face[slot] = null; }
        else return null;

        if (old != null) {
            syncChanged();
            if (parts().isEmpty() && level != null) {
                level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), 3);
            } else if (level != null && !level.isClientSide()) {
                propagateConnectedSignals();
            }
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

        if (center instanceof WirePart wire
                && hitCenterWire(wire, x, y, z)) {
            return Part.CENTER_SLOT;
        }

        double[] d = { y, 1-y, z, 1-z, x, 1-x };
        int best = -1;
        double dist = Double.MAX_VALUE;
        for (int i = 0; i < 6; i++) {
            if (face[i] != null && d[i] < dist) {
                best = i;
                dist = d[i];
            }
        }
        return best;
    }

    private boolean hitCenterWire(
            WirePart wire,
            double x,
            double y,
            double z
    ) {
        if (inside(x, .25, .75)
                && inside(y, .25, .75)
                && inside(z, .25, .75)) {
            return true;
        }

        int connections = visualWireConnections(wire);

        if ((connections & (1 << Direction.DOWN.ordinal())) != 0
                && inside(x,.25,.75) && y <= .25 && inside(z,.25,.75)) return true;
        if ((connections & (1 << Direction.UP.ordinal())) != 0
                && inside(x,.25,.75) && y >= .75 && inside(z,.25,.75)) return true;
        if ((connections & (1 << Direction.NORTH.ordinal())) != 0
                && inside(x,.25,.75) && inside(y,.25,.75) && z <= .25) return true;
        if ((connections & (1 << Direction.SOUTH.ordinal())) != 0
                && inside(x,.25,.75) && inside(y,.25,.75) && z >= .75) return true;
        if ((connections & (1 << Direction.WEST.ordinal())) != 0
                && x <= .25 && inside(y,.25,.75) && inside(z,.25,.75)) return true;
        if ((connections & (1 << Direction.EAST.ordinal())) != 0
                && x >= .75 && inside(y,.25,.75) && inside(z,.25,.75)) return true;

        return false;
    }

    private static boolean inside(double value, double min, double max) {
        return value >= min && value <= max;
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

    private boolean hasSupport(Part part) {
        if (level == null || part.center()) return true;

        Direction attachment = Direction.values()[part.slot()];
        BlockPos support = worldPosition.relative(attachment);
        Direction supportFace = attachment.getOpposite();

        if (part instanceof WirePart) {
            return PlacementRules.canPlaceWireOnSide(
                    level,
                    support,
                    supportFace
            );
        }
        if (part instanceof GatePart) {
            return PlacementRules.canPlaceGateOnSide(
                    level,
                    support,
                    supportFace
            );
        }
        return false;
    }

    public VoxelShape collisionShape() {
        VoxelShape out = Shapes.empty();

        if (center instanceof WirePart wire) {
            out = Shapes.or(out, centerWireShape(visualWireConnections(wire)));
        }

        for (int i = 0; i < 6; i++) {
            Part part = face[i];
            if (!(part instanceof GatePart gate)) continue;

            Direction side = Direction.values()[i];
            out = Shapes.or(
                    out,
                    gate.isArrayCell()
                            ? arrayCellShape(side)
                            : gateFaceShape(side)
            );
        }
        return out;
    }

    public VoxelShape shape() {
        VoxelShape out = Shapes.empty();

        if (center instanceof WirePart wire) {
            out = Shapes.or(out, centerWireShape(visualWireConnections(wire)));
        }

        for (int i = 0; i < 6; i++) {
            Part part = face[i];
            if (part == null) continue;

            Direction side = Direction.values()[i];
            VoxelShape partShape;

            if (part instanceof WirePart wire) {
                partShape = faceWireShape(side, wire.spec().family());
            } else if (part instanceof GatePart gate && gate.isArrayCell()) {
                partShape = arrayCellShape(side);
            } else {
                partShape = gateFaceShape(side);
            }

            out = Shapes.or(out, partShape);
        }
        return out.isEmpty() ? Shapes.block() : out;
    }

    private static VoxelShape gateFaceShape(Direction d) {
        return slabShape(d, .125);
    }

    private static VoxelShape faceWireShape(
            Direction d,
            WireFamily family
    ) {
        double depth = switch (family) {
            case RED_ALLOY -> 2.0 / 16.0;
            case INSULATED -> 3.0 / 16.0;
            case BUNDLED -> 4.0 / 16.0;
        };
        return slabShape(d, depth);
    }

    private static VoxelShape slabShape(Direction d, double depth) {
        return switch (d) {
            case DOWN -> Shapes.box(0,0,0,1,depth,1);
            case UP -> Shapes.box(0,1-depth,0,1,1,1);
            case NORTH -> Shapes.box(0,0,0,1,1,depth);
            case SOUTH -> Shapes.box(0,0,1-depth,1,1,1);
            case WEST -> Shapes.box(0,0,0,depth,1,1);
            case EAST -> Shapes.box(1-depth,0,0,1,1,1);
        };
    }

    private static VoxelShape centerWireShape(int connections) {
        VoxelShape out = Shapes.box(.25,.25,.25,.75,.75,.75);

        for (Direction direction : Direction.values()) {
            if ((connections & (1 << direction.ordinal())) == 0) continue;
            out = Shapes.or(out, switch (direction) {
                case DOWN -> Shapes.box(.25,0,.25,.75,.25,.75);
                case UP -> Shapes.box(.25,.75,.25,.75,1,.75);
                case NORTH -> Shapes.box(.25,.25,0,.75,.75,.25);
                case SOUTH -> Shapes.box(.25,.25,.75,.75,.75,1);
                case WEST -> Shapes.box(0,.25,.25,.25,.75,.75);
                case EAST -> Shapes.box(.75,.25,.25,1,.75,.75);
            });
        }
        return out;
    }

    private static VoxelShape arrayCellShape(Direction d) {
        return switch (d) {
            case DOWN -> Shapes.box(0,0,0,1,.75,1);
            case UP -> Shapes.box(0,.25,0,1,1,1);
            case NORTH -> Shapes.box(0,0,0,1,1,.75);
            case SOUTH -> Shapes.box(0,0,.25,1,1,1);
            case WEST -> Shapes.box(0,0,0,.75,1,1);
            case EAST -> Shapes.box(.25,0,0,1,1,1);
        };
    }

    private static boolean isArrayCell(Part part) {
        return part instanceof GatePart gate && gate.isArrayCell();
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
            if (!faceWireExternalOpen(receiver, direction)) continue;

            boolean straightConnected = hasStraightWireConnection(
                    receiver,
                    direction,
                    attachment
            );

            if (straightConnected) {
                max = Math.max(
                        max,
                        readStraightRedwire(receiver, direction, attachment)
                );
            } else if (outsideCornerEdgeOpen(direction, attachment)
                    && hasCornerWireConnection(receiver, direction, attachment)) {
                max = Math.max(
                        max,
                        readCornerRedwire(receiver, direction, attachment)
                );
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
            } else if (p instanceof GatePart gate
                    && !receiver.center()) {
                Direction toward = Direction.values()[receiver.slot()];
                max = Math.max(
                        max,
                        gateRedwireOutputToward(gate, toward)
                );
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
            int projectRedSignal = multipart.redwireToward(
                    direction.getOpposite(),
                    receiver.spec(),
                    expectedAttachment
            );
            if (projectRedSignal > 0) {
                return projectRedSignal;
            }

            // A center wire may still see an adjacent multipart's ordinary
            // redstone output (for example a gate) through the vanilla
            // interaction path, just like RedstoneCenterLookup upstream.
            if (receiver.center()) {
                return level.getSignal(neighborPos, direction) * 17;
            }
            return 0;
        }

        BlockState neighborState = level.getBlockState(neighborPos);

        // Face redwire has ProjectRed's explicit dust lookup. Dust's normal
        // signaling is conceptually disabled during propagation, but its
        // stored POWER level can still feed a face wire with one level of
        // attenuation. Center/framed wires do not make this face-style dust
        // connection.
        if (neighborState.is(Blocks.REDSTONE_WIRE)) {
            if (receiver.center()) return 0;
            return Math.max(
                    neighborState.getValue(RedstoneWireBlock.POWER) - 1,
                    0
            );
        }

        return level.getSignal(neighborPos, direction) * 17;
    }

    private int readCornerRedwire(
            WirePart receiver,
            Direction direction,
            Direction attachment
    ) {
        BlockPos cornerPos = worldPosition
                .relative(direction)
                .relative(attachment);
        BlockEntity corner = level.getBlockEntity(cornerPos);
        if (!(corner instanceof MultipartBlockEntity multipart)) return 0;

        return multipart.cornerRedwireSignal(
                direction.getOpposite(),
                attachment.getOpposite(),
                receiver.spec()
        );
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
                            false
                    );
                }
            }
            return out;
        }

        Direction attachment = Direction.values()[receiver.slot()];
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == attachment.getAxis()) continue;
            if (!faceWireExternalOpen(receiver, direction)) continue;

            boolean straightConnected = hasStraightWireConnection(
                    receiver,
                    direction,
                    attachment
            );

            if (straightConnected) {
                BlockEntity neighbor = level.getBlockEntity(
                        worldPosition.relative(direction)
                );
                if (neighbor instanceof MultipartBlockEntity multipart) {
                    int[] straight = multipart.bundledToward(
                            direction.getOpposite(),
                            receiver.spec(),
                            attachment
                    );
                    out = BundledSignals.raise(out, straight, false);
                }
            } else if (outsideCornerEdgeOpen(direction, attachment)
                    && hasCornerWireConnection(receiver, direction, attachment)) {
                BlockPos cornerPos = worldPosition
                        .relative(direction)
                        .relative(attachment);
                BlockEntity corner = level.getBlockEntity(cornerPos);
                if (corner instanceof MultipartBlockEntity multipart) {
                    int[] cornerSignal = multipart.cornerBundledSignal(
                            direction.getOpposite(),
                            attachment.getOpposite(),
                            receiver.spec()
                    );
                    out = BundledSignals.raise(out, cornerSignal, false);
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
        if (expectedAttachment == null) {
            if (!(center instanceof WirePart wire)) {
                return 0;
            }

            if (wire.spec().family() == WireFamily.BUNDLED
                    && receiver.family() == WireFamily.INSULATED) {
                return Math.max(
                        0,
                        wire.bundled()[receiver.color()] - 1
                );
            }

            if (!receiver.redwireCompatible(wire.spec())) {
                return 0;
            }
            return Math.max(0, wire.signal() - 1);
        }

        int max = 0;
        for (Part p : parts()) {
            if (!partConnectsToward(p, toward)) continue;

            if (p instanceof WirePart wire) {
                if (!straightAttachmentMatches(wire, expectedAttachment)) continue;

                if (wire.spec().family() == WireFamily.BUNDLED
                        && receiver.family() == WireFamily.INSULATED) {
                    max = Math.max(
                            max,
                            Math.max(0, wire.bundled()[receiver.color()] - 1)
                    );
                } else if (receiver.redwireCompatible(wire.spec())) {
                    max = Math.max(max, Math.max(0, wire.signal() - 1));
                }
            } else if (p instanceof GatePart gate) {
                if (expectedAttachment != null
                        && Direction.values()[gate.slot()] != expectedAttachment) {
                    continue;
                }
                max = Math.max(
                        max,
                        gateRedwireOutputToward(gate, toward)
                );
            }
        }
        return max;
    }

    private int cornerRedwireSignal(
            Direction expectedAttachment,
            Direction edgeDirection,
            WireSpec receiver
    ) {
        Part p = face[expectedAttachment.ordinal()];

        if (p instanceof WirePart wire) {
            if (!faceWireExternalOpen(wire, edgeDirection)) return 0;

            if (wire.spec().family() == WireFamily.BUNDLED
                    && receiver.family() == WireFamily.INSULATED) {
                return Math.max(
                        0,
                        wire.bundled()[receiver.color()] - 1
                );
            }

            if (!receiver.redwireCompatible(wire.spec())) return 0;
            return Math.max(0, wire.signal() - 1);
        }

        if (p instanceof GatePart gate
                && gateConnectsToward(gate, edgeDirection, false)) {
            return gateRedwireOutputToward(gate, edgeDirection);
        }

        return 0;
    }

    private int[] bundledToward(
            Direction toward,
            WireSpec receiver,
            Direction expectedAttachment
    ) {
        int[] out = new int[16];

        if (expectedAttachment == null) {
            if (!(center instanceof WirePart wire)) {
                return null;
            }

            if (wire.spec().family() == WireFamily.BUNDLED
                    && receiver.bundledCompatible(wire.spec())) {
                return BundledSignals.raise(out, wire.bundled(), true);
            }

            if (wire.spec().family() == WireFamily.INSULATED) {
                out[wire.spec().color()] = Math.max(0, wire.signal() - 1);
                return out;
            }
            return null;
        }

        for (Part p : parts()) {
            if (!partConnectsToward(p, toward)) continue;

            if (p instanceof WirePart wire) {
                if (!straightAttachmentMatches(wire, expectedAttachment)) {
                    continue;
                }

                if (wire.spec().family() == WireFamily.BUNDLED
                        && receiver.bundledCompatible(wire.spec())) {
                    out = BundledSignals.raise(out, wire.bundled(), true);
                } else if (wire.spec().family() == WireFamily.INSULATED) {
                    int channel = wire.spec().color();
                    out[channel] = Math.max(
                            out[channel],
                            Math.max(0, wire.signal() - 1)
                    );
                }
            } else if (p instanceof GatePart gate) {
                if (Direction.values()[gate.slot()] != expectedAttachment) {
                    continue;
                }

                // Bundled emitters inject their output at full 0..255
                // strength; only cable/insulated-wire hops diminish.
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
            Direction edgeDirection,
            WireSpec receiver
    ) {
        Part p = face[expectedAttachment.ordinal()];

        if (p instanceof WirePart wire) {
            if (!faceWireExternalOpen(wire, edgeDirection)) return null;

            int[] out = new int[16];
            if (wire.spec().family() == WireFamily.BUNDLED
                    && receiver.bundledCompatible(wire.spec())) {
                return BundledSignals.raise(out, wire.bundled(), true);
            }

            if (wire.spec().family() == WireFamily.INSULATED) {
                out[wire.spec().color()] = Math.max(
                        0,
                        wire.signal() - 1
                );
                return out;
            }
            return null;
        }

        if (p instanceof GatePart gate
                && gateConnectsToward(gate, edgeDirection, true)) {
            return gateBundledOutputToward(gate, edgeDirection);
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
        // A face gate's four IO ports terminate at the four edge face slots.
        // Center/framed wires do not occupy any of those slots.
        return !wire.center()
                && Direction.values()[wire.slot()] == direction;
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

            boolean connected = receiver.center()
                    ? hasCenterInsideWireConnection(receiver, direction)
                    : hasInsideWireConnection(receiver, direction);

            boolean externalOpen = attachment == null
                    || faceWireExternalOpen(receiver, direction);

            if (!connected && externalOpen) {
                connected = hasStraightWireConnection(
                        receiver,
                        direction,
                        attachment
                );
            }

            if (!connected
                    && attachment != null
                    && externalOpen
                    && outsideCornerEdgeOpen(direction, attachment)) {
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

    private boolean faceWireExternalOpen(
            WirePart receiver,
            Direction direction
    ) {
        if (receiver.center()) return true;

        Part inside = face[direction.ordinal()];
        if (inside == null) return true;

        if (inside instanceof WirePart other) {
            return receiver.canConnect(other);
        }

        if (inside instanceof GatePart gate) {
            return gateConnectsToward(
                    gate,
                    Direction.values()[receiver.slot()],
                    receiver.spec().family() == WireFamily.BUNDLED
            );
        }

        return false;
    }

    private boolean outsideCornerEdgeOpen(
            Direction direction,
            Direction attachment
    ) {
        if (level == null) return false;

        BlockPos bendPos = worldPosition.relative(direction);
        if (level.isEmptyBlock(bendPos)) return true;

        BlockEntity bendEntity = level.getBlockEntity(bendPos);
        if (!(bendEntity instanceof MultipartBlockEntity multipart)) {
            return false;
        }

        // Upstream checks the two face slots touching the traversed edge
        // (plus an edge-strip slot, which this Fabric multipart subset does
        // not implement). A center/framed wire does not block the bend.
        return !multipart.hasSlot(direction.getOpposite().ordinal())
                && !multipart.hasSlot(attachment.ordinal());
    }

    private boolean hasCenterInsideWireConnection(
            WirePart receiver,
            Direction direction
    ) {
        if (!receiver.center()) return false;

        Part inside = face[direction.ordinal()];
        if (inside instanceof WirePart other) {
            return receiver.canConnect(other);
        }

        // Framed red/insulated wire does not handshake directly with gates in
        // upstream ProjectRed. Bundled-gate center handshakes depend on CCL's
        // legacy sentinel direction and are intentionally not synthesized
        // here until that behavior is represented explicitly.
        return false;
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
                    Direction.values()[receiver.slot()],
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
            if (expectedAttachment == null) {
                Part centerPart = multipart.part(Part.CENTER_SLOT);
                return centerPart instanceof WirePart other
                        && receiver.canConnect(other);
            }

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
        BlockPos cornerPos = worldPosition
                .relative(direction)
                .relative(attachment);
        BlockEntity corner = level.getBlockEntity(cornerPos);
        if (!(corner instanceof MultipartBlockEntity multipart)) return false;

        Direction edgeDirection = attachment.getOpposite();
        Part p = multipart.part(direction.getOpposite().ordinal());

        if (p instanceof WirePart other) {
            return receiver.canConnect(other)
                    && multipart.faceWireExternalOpen(other, edgeDirection);
        }

        if (p instanceof GatePart gate) {
            return gateConnectsToward(
                    gate,
                    edgeDirection,
                    receiver.spec().family() == WireFamily.BUNDLED
            );
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
                    : gate.canConnectRedstoneLocal(local);
        }
        return false;
    }

    public int gateRedwireRawInput(GatePart receiver, int local) {
        if (!receiver.diminishesRedwireLocal(local)) {
            return 0;
        }

        Direction attachment = Direction.values()[receiver.slot()];
        Direction direction = localToWorld(
                attachment,
                receiver.rotation(),
                local
        );

        // ArrayGatePart::calculateSignal() gives the internal multipart edge
        // highest priority, then straight, then corner, then vanilla.
        Integer inside = insideArrayRedwireRaw(
                receiver,
                attachment,
                direction
        );
        if (inside != null) {
            return inside;
        }

        if (level == null) return 0;

        Integer straight = straightArrayRedwireRaw(
                receiver,
                attachment,
                direction
        );
        if (straight != null) {
            return straight;
        }

        if (outsideCornerEdgeOpen(direction, attachment)) {
            Integer corner = cornerArrayRedwireRaw(
                    receiver,
                    attachment,
                    direction
            );
            if (corner != null) {
                return corner;
            }
        }

        BlockPos neighborPos = worldPosition.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.is(Blocks.REDSTONE_WIRE)) {
            // RedstoneFaceLookup.resolveVanillaSignal(..., limitDust=true)
            // intentionally returns dust POWER - 1 without scaling by 17.
            return Math.max(
                    neighborState.getValue(RedstoneWireBlock.POWER) - 1,
                    0
            );
        }

        return level.getSignal(neighborPos, direction) * 17;
    }

    private Integer insideArrayRedwireRaw(
            GatePart receiver,
            Direction receiverAttachment,
            Direction direction
    ) {
        Part inside = face[direction.ordinal()];

        if (inside instanceof WirePart wire
                && wire.spec().family() != WireFamily.BUNDLED) {
            return Math.max(0, wire.signal() - 1);
        }

        if (inside instanceof GatePart gate
                && gate.isArrayCell()
                && gateConnectsToward(
                        gate,
                        receiverAttachment,
                        false
                )) {
            return gateRedwireOutputToward(
                    gate,
                    receiverAttachment
            );
        }

        return null;
    }

    private Integer straightArrayRedwireRaw(
            GatePart receiver,
            Direction attachment,
            Direction direction
    ) {
        BlockEntity neighbor = level.getBlockEntity(
                worldPosition.relative(direction)
        );
        if (!(neighbor instanceof MultipartBlockEntity multipart)) {
            return null;
        }

        Part part = multipart.part(attachment.ordinal());

        if (part instanceof WirePart wire
                && wire.spec().family() != WireFamily.BUNDLED
                && partConnectsToward(wire, direction.getOpposite())
                && multipart.faceWireExternalOpen(
                        wire,
                        direction.getOpposite()
                )) {
            return Math.max(0, wire.signal() - 1);
        }

        if (part instanceof GatePart gate
                && gate.isArrayCell()
                && gateConnectsToward(
                        gate,
                        direction.getOpposite(),
                        false
                )) {
            return gateRedwireOutputToward(
                    gate,
                    direction.getOpposite()
            );
        }

        return null;
    }

    private Integer cornerArrayRedwireRaw(
            GatePart receiver,
            Direction attachment,
            Direction direction
    ) {
        BlockPos cornerPos = worldPosition
                .relative(direction)
                .relative(attachment);
        BlockEntity corner = level.getBlockEntity(cornerPos);
        if (!(corner instanceof MultipartBlockEntity multipart)) {
            return null;
        }

        Part part = multipart.part(direction.getOpposite().ordinal());
        if (!(part instanceof WirePart wire)
                || wire.spec().family() == WireFamily.BUNDLED) {
            return null;
        }

        Direction farEdge = attachment.getOpposite();
        if (!multipart.faceWireExternalOpen(wire, farEdge)) {
            return null;
        }

        return Math.max(0, wire.signal() - 1);
    }

    public int gateAnalogInput(GatePart receiver, int local) {
        Direction direction = localToWorld(
                Direction.values()[receiver.slot()],
                receiver.rotation(),
                local
        );
        int raw = gateRedstoneRawInput(receiver, local);
        int result = Math.max(0, Math.min(15, (raw + 16) / 17));

        if (level == null) return result;

        BlockPos neighborPos = worldPosition.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.hasAnalogOutputSignal()) {
            result = Math.max(
                    result,
                    neighborState.getAnalogOutputSignal(
                            level,
                            neighborPos,
                            direction.getOpposite()
                    )
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
        if (!receiver.canConnectBundledLocal(local)) {
            return new int[16];
        }

        Direction attachment = Direction.values()[receiver.slot()];
        Direction direction = localToWorld(
                attachment,
                receiver.rotation(),
                local
        );

        if (level == null) {
            int[] inside = insideGateBundledSignal(
                    receiver,
                    attachment,
                    direction
            );
            return inside == null ? new int[16] : inside;
        }

        int[] straight = straightGateBundledSignal(
                receiver,
                attachment,
                direction
        );
        if (straight != null) {
            return straight;
        }

        if (outsideCornerEdgeOpen(direction, attachment)) {
            int[] corner = cornerGateBundledSignal(
                    receiver,
                    attachment,
                    direction
            );
            if (corner != null) {
                return corner;
            }
        }

        int[] inside = insideGateBundledSignal(
                receiver,
                attachment,
                direction
        );
        return inside == null ? new int[16] : inside;
    }

    private int[] insideGateBundledSignal(
            GatePart receiver,
            Direction receiverAttachment,
            Direction direction
    ) {
        Part inside = face[direction.ordinal()];

        if (inside instanceof WirePart wire
                && wire.spec().family() == WireFamily.BUNDLED) {
            return wire.bundled();
        }

        if (inside instanceof GatePart gate
                && gateConnectsToward(
                        gate,
                        receiverAttachment,
                        true
                )) {
            return gateBundledOutputToward(
                    gate,
                    receiverAttachment
            );
        }

        return null;
    }

    private int[] straightGateBundledSignal(
            GatePart receiver,
            Direction attachment,
            Direction direction
    ) {
        BlockEntity neighbor = level.getBlockEntity(
                worldPosition.relative(direction)
        );
        if (!(neighbor instanceof MultipartBlockEntity multipart)) {
            return null;
        }

        Part part = multipart.part(attachment.ordinal());

        if (part instanceof WirePart wire
                && wire.spec().family() == WireFamily.BUNDLED
                && partConnectsToward(wire, direction.getOpposite())
                && multipart.faceWireExternalOpen(
                        wire,
                        direction.getOpposite()
                )) {
            return wire.bundled();
        }

        if (part instanceof GatePart gate
                && gateConnectsToward(
                        gate,
                        direction.getOpposite(),
                        true
                )) {
            return gateBundledOutputToward(
                    gate,
                    direction.getOpposite()
            );
        }

        return null;
    }

    private int[] cornerGateBundledSignal(
            GatePart receiver,
            Direction attachment,
            Direction direction
    ) {
        BlockPos cornerPos = worldPosition
                .relative(direction)
                .relative(attachment);
        BlockEntity corner = level.getBlockEntity(cornerPos);
        if (!(corner instanceof MultipartBlockEntity multipart)) {
            return null;
        }

        // Gates themselves cannot turn a corner upstream. The far endpoint
        // must therefore be a face bundled cable, whose canConnectCorner()
        // supplies the corner capability for the handshake.
        Part part = multipart.part(direction.getOpposite().ordinal());
        if (!(part instanceof WirePart wire)
                || wire.spec().family() != WireFamily.BUNDLED) {
            return null;
        }

        Direction farEdge = attachment.getOpposite();
        if (!multipart.faceWireExternalOpen(wire, farEdge)) {
            return null;
        }

        return wire.bundled();
    }

    public int gateInput(GatePart gate, int mask) {
        int input = 0;
        for (int local = 0; local < 4; local++) {
            if ((mask & (1 << local)) == 0) continue;
            if (gateRedstoneRawInput(gate, local) > 0) {
                input |= 1 << local;
            }
        }
        return input;
    }

    private int gateRedstoneRawInput(
            GatePart receiver,
            int local
    ) {
        if (!receiver.canConnectRedstoneLocal(local)) {
            return 0;
        }

        Direction attachment = Direction.values()[receiver.slot()];
        Direction direction = localToWorld(
                attachment,
                receiver.rotation(),
                local
        );

        if (level == null) {
            return internalGateRedstoneRawInput(direction);
        }

        // IConnectableFacePart discovers an external straight connection
        // first. Only when none exists may it wrap around the outer corner.
        Integer straight = straightGateRedwireRaw(
                attachment,
                direction
        );
        if (straight != null) {
            return straight;
        }

        if (outsideCornerEdgeOpen(direction, attachment)) {
            Integer corner = cornerGateRedwireRaw(
                    attachment,
                    direction
            );
            if (corner != null) {
                return corner;
            }
        }

        // RedstoneGatePart#getRedstoneInput checks an inside-face connection
        // only when no corner/straight ProjectRed connection was discovered.
        Integer inside = insideGateRedwireRaw(direction);
        if (inside != null) {
            return inside;
        }

        // No ProjectRed connection map entry: use the vanilla interaction
        // lookup exactly as RedstoneGatePart does.
        BlockPos neighborPos = worldPosition.relative(direction);
        return level.getSignal(neighborPos, direction) * 17;
    }

    private int internalGateRedstoneRawInput(Direction direction) {
        Integer inside = insideGateRedwireRaw(direction);
        return inside == null ? 0 : inside;
    }

    private Integer insideGateRedwireRaw(Direction direction) {
        Part inside = face[direction.ordinal()];
        if (inside instanceof WirePart wire
                && wire.spec().family() != WireFamily.BUNDLED) {
            return wire.signal();
        }
        return null;
    }

    private Integer straightGateRedwireRaw(
            Direction attachment,
            Direction direction
    ) {
        BlockEntity neighbor = level.getBlockEntity(
                worldPosition.relative(direction)
        );
        if (!(neighbor instanceof MultipartBlockEntity multipart)) {
            return null;
        }

        Part part = multipart.part(attachment.ordinal());
        if (!(part instanceof WirePart wire)
                || wire.spec().family() == WireFamily.BUNDLED
                || !partConnectsToward(wire, direction.getOpposite())
                || !multipart.faceWireExternalOpen(
                        wire,
                        direction.getOpposite()
                )) {
            return null;
        }

        return wire.signal();
    }

    private Integer cornerGateRedwireRaw(
            Direction attachment,
            Direction direction
    ) {
        BlockPos cornerPos = worldPosition
                .relative(direction)
                .relative(attachment);
        BlockEntity corner = level.getBlockEntity(cornerPos);
        if (!(corner instanceof MultipartBlockEntity multipart)) {
            return null;
        }

        Part part = multipart.part(direction.getOpposite().ordinal());
        Direction farEdge = attachment.getOpposite();

        if (!(part instanceof WirePart wire)
                || wire.spec().family() == WireFamily.BUNDLED
                || !multipart.faceWireExternalOpen(wire, farEdge)) {
            return null;
        }

        return wire.signal();
    }

    private int[] bundledSignalTowardFace(
            Direction toward,
            Direction expectedAttachment
    ) {
        int[] out = new int[16];
        for (Part p : parts()) {
            if (p.center()
                    || Direction.values()[p.slot()] != expectedAttachment
                    || !partConnectsToward(p, toward)) {
                continue;
            }

            if (p instanceof WirePart wire
                    && wire.spec().family() == WireFamily.BUNDLED) {
                out = BundledSignals.raise(
                        out,
                        wire.bundledSignal(),
                        false
                );
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

    private int gateRedwireOutputToward(
            GatePart gate,
            Direction toward
    ) {
        Direction attachment = Direction.values()[gate.slot()];
        if (toward.getAxis() == attachment.getAxis()) return 0;

        for (int r = 0; r < 4; r++) {
            if (localToWorld(attachment, gate.rotation(), r) != toward) {
                continue;
            }

            int raw = gate.outputRawLocal(r);
            return gate.diminishesRedwireLocal(r)
                    ? Math.max(0, raw - 1)
                    : raw;
        }
        return 0;
    }

    private int gateOutputToward(GatePart gate, Direction toward) {
        return Math.min(15, (gateRawOutputToward(gate, toward) + 16) / 17);
    }

    private int gateStrongOutputToward(
            GatePart gate,
            Direction toward
    ) {
        Direction attachment = Direction.values()[gate.slot()];
        if (toward.getAxis() == attachment.getAxis()) return 0;

        for (int r = 0; r < 4; r++) {
            if (localToWorld(attachment, gate.rotation(), r) == toward) {
                return gate.outputLocal(r);
            }
        }
        return 0;
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

    public boolean canConnectVanillaRedstone(Direction toward) {
        for (Part p : parts()) {
            if (p instanceof WirePart wire) {
                if (wire.spec().family() != WireFamily.BUNDLED
                        && partConnectsToward(wire, toward)) {
                    return true;
                }
            } else if (p instanceof GatePart gate
                    && gateConnectsToward(gate, toward, false)) {
                return true;
            }
        }
        return false;
    }

    public int vanillaSignal(Direction toward) {
        int max = 0;
        for (Part p : parts()) {
            if (p instanceof WirePart wire
                    && wire.spec().family() != WireFamily.BUNDLED
                    && !RedwirePowerContext.suppressed()) {
                max = Math.max(max, wireWeakSignalToward(wire, toward));
            } else if (p instanceof GatePart gate
                    && partConnectsToward(gate, toward)) {
                max = Math.max(max, gateOutputToward(gate, toward));
            }
        }
        return max;
    }

    public int directSignal(Direction toward) {
        int max = 0;
        for (Part p : parts()) {
            if (p instanceof WirePart wire) {
                if (RedwirePowerContext.suppressed()
                        || wire.spec().family() != WireFamily.RED_ALLOY
                        || wire.center()) {
                    continue;
                }

                Direction attachment = Direction.values()[wire.slot()];
                if (toward == attachment) {
                    max = Math.max(max, wire.vanillaSignal());
                }
            } else if (p instanceof GatePart gate
                    && partConnectsToward(gate, toward)) {
                // RedstoneGatePart strong power comes from getOutput(), not
                // from IRedwirePart#getRedwireSignal(). This distinction is
                // essential for array cells: their passing 0..255 tracks are
                // weak redwire only, while true logic outputs may strong-power.
                max = Math.max(max, gateStrongOutputToward(gate, toward));
            }
        }
        return max;
    }

    private int wireWeakSignalToward(WirePart wire, Direction toward) {
        if (wire.spec().family() == WireFamily.RED_ALLOY) {
            // Face red-alloy weak-powers every side except a tangent side
            // whose connection is consumed by another part inside this same
            // multipart. Framed red-alloy has no such face-slot exclusion.
            if (!wire.center()) {
                Direction attachment = Direction.values()[wire.slot()];
                if (toward.getAxis() != attachment.getAxis()
                        && hasInsideWireConnection(wire, toward)) {
                    return 0;
                }
            }
            return wire.vanillaSignal();
        }

        int connections = visualWireConnections(wire);
        if ((connections & (1 << toward.ordinal())) == 0) {
            return 0;
        }

        if (!wire.center()) {
            Direction attachment = Direction.values()[wire.slot()];
            // Insulated face wire never powers above/below its mounting plane.
            if (toward.getAxis() == attachment.getAxis()) {
                return 0;
            }
        }

        return wire.vanillaSignal();
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
        if (level != null && !level.isClientSide()) {
            propagateConnectedSignals();
        }
    }

    private void syncChanged() {
        setChanged();
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, state, state, 3);

            // A multipart's redstone ports live in block-entity state. When a
            // gate rotates, changes shape, or is added/removed, the blockstate
            // itself does not change, so vanilla dust would otherwise keep
            // its stale connection shape and never evaluate the new output.
            state.updateNeighbourShapes(level, worldPosition, 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());

            if (!level.isClientSide()) {
                notifyExternalRedstoneNeighbors(state);
            }
        }
    }

    private void notifyExternalRedstoneNeighbors(BlockState sourceState) {
        // Upstream RedstoneGatePart::notifyExternals() and
        // RedAlloyWirePart::propagateOther() deliberately notify not only
        // the directly powered block, but also its surrounding blocks.
        // Vanilla needs those second-ring notifications when a ProjectRed
        // output strongly powers a solid conductor.
        Set<Long> notified = new HashSet<>();

        for (Direction outputSide : Direction.values()) {
            BlockPos poweredPos = worldPosition.relative(outputSide);
            if (notified.add(poweredPos.asLong())) {
                // updateNeighborsAt() notifies every block surrounding the
                // adjacent output block, which is the second ring that
                // ProjectRed explicitly updates for strong-power changes.
                level.updateNeighborsAt(
                        poweredPos,
                        sourceState.getBlock()
                );
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        List<Part> all = parts();
        output.putInt("part_count", all.size());
        long gameTime = level == null ? 0 : level.getGameTime();
        for (int i = 0; i < all.size(); i++) {
            output.putString("part_" + i, all.get(i).encode(gameTime));
        }
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
