package dev.xt9y.projectred.multipart;

import dev.xt9y.projectred.content.PRContent;
import dev.xt9y.projectred.core.BundledSignals;
import dev.xt9y.projectred.integration.GatePart;
import dev.xt9y.projectred.transmission.WireFamily;
import dev.xt9y.projectred.transmission.WirePart;
import dev.xt9y.projectred.transmission.WireSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

        for (int slot : unsupported) be.remove(slot);
        if (changed) be.syncChanged();
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
        int max = 0;
        for (Part p : parts()) {
            if (p == receiver || !(p instanceof WirePart other)) continue;
            if (!receiver.canConnect(other)) continue;
            if (other.spec().family() == WireFamily.BUNDLED && receiver.spec().family() == WireFamily.INSULATED) {
                max = Math.max(max, Math.max(0, other.bundled()[receiver.spec().color()] - 1));
            } else {
                max = Math.max(max, Math.max(0, other.signal() - 1));
            }
        }

        if (level == null) return max;
        for (Direction d : Direction.values()) {
            BlockPos np = worldPosition.relative(d);
            BlockEntity nbe = level.getBlockEntity(np);
            if (nbe instanceof MultipartBlockEntity mp) {
                max = Math.max(max, mp.redwireToward(d.getOpposite(), receiver.spec(), true));
            } else {
                max = Math.max(max, level.getSignal(np, d) * 17);
            }
        }
        return max;
    }

    public int[] calculateBundledInput(WirePart receiver) {
        int[] out = new int[16];
        for (Part p : parts()) {
            if (p == receiver || !(p instanceof WirePart other) || !receiver.canConnect(other)) continue;
            out = BundledSignals.raise(out, other.bundledSignal(), true);
        }
        if (level != null) {
            for (Direction d : Direction.values()) {
                BlockEntity nbe = level.getBlockEntity(worldPosition.relative(d));
                if (nbe instanceof MultipartBlockEntity mp) {
                    out = BundledSignals.raise(out, mp.bundledToward(d.getOpposite(), receiver.spec()), true);
                }
            }
        }
        return out;
    }

    private int redwireToward(Direction toward, WireSpec receiver, boolean attenuate) {
        int max = 0;
        for (Part p : parts()) {
            if (p instanceof WirePart wire) {
                if (wire.spec().family() == WireFamily.BUNDLED && receiver.family() == WireFamily.INSULATED) {
                    max = Math.max(max, wire.bundled()[receiver.color()]);
                } else if (receiver.redwireCompatible(wire.spec())) {
                    max = Math.max(max, wire.signal());
                }
            } else if (p instanceof GatePart gate) {
                max = Math.max(max, gateRawOutputToward(gate, toward));
            }
        }
        return attenuate ? Math.max(0, max - 1) : max;
    }

    private int[] bundledToward(Direction toward, WireSpec receiver) {
        int[] out = new int[16];
        for (Part p : parts()) {
            if (p instanceof WirePart wire) {
                if (wire.spec().family() == WireFamily.BUNDLED && receiver.bundledCompatible(wire.spec())) {
                    out = BundledSignals.raise(out, wire.bundled(), false);
                } else if (wire.spec().family() == WireFamily.INSULATED) {
                    int[] one = new int[16];
                    one[wire.spec().color()] = wire.signal();
                    out = BundledSignals.raise(out, one, false);
                }
            } else if (p instanceof GatePart gate) {
                out = BundledSignals.raise(out, gateBundledOutputToward(gate, toward), false);
            }
        }
        return out;
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
            if (p instanceof WirePart wire && wire.spec().family() != WireFamily.BUNDLED) {
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
                    neighborState.getAnalogOutputSignal(level, neighborPos)
            );
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
            if (p instanceof WirePart wire) {
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
            if (p instanceof WirePart wire) max = Math.max(max, wire.vanillaSignal());
            if (p instanceof GatePart other) max = Math.max(max, gateOutputToward(other, worldDir.getOpposite()));
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
            if (p instanceof WirePart wire && wire.spec().family() != WireFamily.BUNDLED) {
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
            if (p instanceof WirePart wire) {
                out = BundledSignals.raise(out, wire.bundledSignal(), false);
            } else if (p instanceof GatePart gate) {
                out = BundledSignals.raise(out, gateBundledOutputToward(gate, toward), false);
            }
        }
        return out;
    }

    public int vanillaSignal(Direction toward) {
        int max = 0;
        for (Part p : parts()) {
            if (p instanceof WirePart wire && wire.spec().family() != WireFamily.BUNDLED) max = Math.max(max, wire.vanillaSignal());
            if (p instanceof GatePart gate) max = Math.max(max, gateOutputToward(gate, toward));
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
