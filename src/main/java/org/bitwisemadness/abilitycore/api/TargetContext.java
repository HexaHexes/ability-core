package org.bitwisemadness.abilitycore.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * What an activation attempt points at (REQ-CORE-06).
 *
 * <p>Wire-safe by construction: instances are <em>hints</em> the client
 * proposes; the server resolves them ({@code TargetValidationStep}) before any
 * provider sees them - clients never carry authority (the pipeline's
 * {@code REFUSED_INVALID_TARGET} covers unresolved hints). Every parse path is
 * fail-closed: malformed input yields {@link Optional#empty()}, never a
 * guessed value.</p>
 */
public sealed interface TargetContext {

    /** No targeting intent (default for non-directional activations). */
    record None() implements TargetContext {
    }

    /** Self-targeted activation. */
    record Self() implements TargetContext {
    }

    /**
     * Entity hint resolved server-side against the player's own level;
     * an unresolvable id is refused, never ignored.
     */
    record EntityHint(UUID entityId) implements TargetContext {
        public EntityHint {
            if (entityId == null) {
                throw new IllegalArgumentException("entityId must not be null");
            }
        }
    }

    /** Block position hint; must be in a loaded chunk when resolved. */
    record BlockHint(BlockPos pos) implements TargetContext {
        public BlockHint {
            if (pos == null) {
                throw new IllegalArgumentException("pos must not be null");
            }
        }
    }

    /** Area centered on a position with a positive, finite radius. */
    record AreaHint(BlockPos center, double radius) implements TargetContext {
        public AreaHint {
            if (center == null) {
                throw new IllegalArgumentException("center must not be null");
            }
            if (!Double.isFinite(radius) || radius <= 0) {
                throw new IllegalArgumentException("radius must be finite and > 0, got " + radius);
            }
        }
    }

    enum Kind {
        NONE, SELF, ENTITY, BLOCK, AREA
    }

    default Kind kindOf() {
        if (this instanceof None) {
            return Kind.NONE;
        }
        if (this instanceof Self) {
            return Kind.SELF;
        }
        if (this instanceof EntityHint) {
            return Kind.ENTITY;
        }
        if (this instanceof BlockHint) {
            return Kind.BLOCK;
        }
        if (this instanceof AreaHint) {
            return Kind.AREA;
        }
        throw new IllegalStateException("unknown TargetContext implementation: " + getClass());
    }

    /**
     * Fail-closed wire parsing. Params for kinds that need them:
     * {@code entity=[uuid]}, {@code block=[x,y,z]}, {@code area=[x,y,z,radius]}.
     * Unknown kinds or malformed values return empty (the request layer then
     * refuses the request rather than activating on a guess).
     */
    static Optional<TargetContext> fromWire(String kind, Map<String, String> params) {
        if (kind == null) {
            return Optional.empty();
        }
        Map<String, String> p = params == null ? Map.of() : params;
        try {
            return switch (kind.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "none" -> Optional.of(new None());
                case "self" -> Optional.of(new Self());
                case "entity" -> Optional.of(new EntityHint(UUID.fromString(required(p, "entity"))));
                case "block" -> Optional.of(new BlockHint(parsePos(p)));
                case "area" -> Optional.of(new AreaHint(parsePos(p),
                        Double.parseDouble(required(p, "radius"))));
                default -> Optional.empty();
            };
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String required(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing param: " + key);
        }
        return value;
    }

    private static BlockPos parsePos(Map<String, String> params) {
        return new BlockPos(
                Integer.parseInt(required(params, "x")),
                Integer.parseInt(required(params, "y")),
                Integer.parseInt(required(params, "z")));
    }

    /** Convenience null-safe kind lookup (null target reads as NONE). */
    static Kind kindOfNullable(@Nullable TargetContext target) {
        return target == null ? Kind.NONE : target.kindOf();
    }
}
