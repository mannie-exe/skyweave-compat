package design.inherent.skyweavecompat.combat;

/** Bounded strike-phase tuning for inspected clips. Recovery remains distinct from attack recharge. */
public final class AttackPacing {
    private record Phase(float length, float strikeEnd, float boost) {}
    private static final java.util.Map<String, Phase> FIRST = java.util.Map.of(
            "attack_1", new Phase(2.75f, .6f, 1.25f),
            "attack_2", new Phase(2.8333f, .6f, 1.25f),
            "attack_3", new Phase(3.3333f, .6f, 1.25f),
            "attack_critical", new Phase(2.3333f, .5f, 1.5f),
            "new_punch_attack", new Phase(1.2083f, .35f, 1.25f),
            "new_punch_attack2", new Phase(1.2083f, .35f, 1.25f));
    private static final java.util.Map<String, Phase> THIRD = java.util.Map.of(
            "sword_attack_1", new Phase(.8333f, .6f, 4f),
            "sword_attack_2", new Phase(.8333f, .6f, 4f),
            "axe_attack_right", new Phase(.9167f, .3f, 1.5f),
            "axe_attack_left", new Phase(.9167f, .3f, 1.5f),
            "punch_right", new Phase(.7917f, .45f, 3f),
            "punch_left", new Phase(.75f, .45f, 3f));
    public static float firstPerson(AttackSnapshot attack, String clip, float length, float time) {
        return rate(attack, clip == null ? null : FIRST.get(clip), length, time);
    }
    public static float thirdPerson(AttackSnapshot attack, String clip, float length, float time) {
        return rate(attack, clip == null ? null : THIRD.get(clip), length, time);
    }
    private static float rate(AttackSnapshot attack, Phase phase, float length, float time) {
        if (attack == null || attack.kind() == AttackSnapshot.Kind.BLOCK || phase == null
                || !Float.isFinite(length) || Math.abs(length - phase.length()) > .0002f
                || !Float.isFinite(time) || time < 0 || attack.attackSpeed() <= 0) return 1;
        double reference = referenceSpeed(attack.item());
        if (reference == 0) return 1;
        float attributeRate = (float) Math.clamp(Math.sqrt(attack.attackSpeed() / reference), .5, 2);
        float fadeStart = phase.strikeEnd() * .8f;
        float blend = Math.clamp((time - fadeStart) / (phase.strikeEnd() - fadeStart), 0, 1);
        blend = blend * blend * (3 - 2 * blend);
        return Math.clamp((phase.boost() + (1 - phase.boost()) * blend) * attributeRate, .5f, 4f);
    }
    public static boolean supportedItem(AttackSnapshot attack) {
        return attack != null && attack.kind() != AttackSnapshot.Kind.BLOCK && attack.attackSpeed() > 0
                && referenceSpeed(attack.item()) > 0;
    }
    public static boolean knownThirdPersonClip(String name, float length) {
        Phase phase = name == null ? null : THIRD.get(name);
        return phase != null && Float.isFinite(length) && Math.abs(length - phase.length()) <= .0002f;
    }
    private static double referenceSpeed(String item) {
        return switch (item) {
            case "minecraft:air" -> 4;
            case "minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword", "minecraft:golden_sword",
                    "minecraft:diamond_sword", "minecraft:netherite_sword" -> 1.6;
            case "minecraft:wooden_axe", "minecraft:stone_axe" -> .8;
            case "minecraft:iron_axe" -> .9;
            case "minecraft:golden_axe", "minecraft:diamond_axe", "minecraft:netherite_axe" -> 1;
            default -> 0;
        };
    }
    private AttackPacing() {}
}
