package sigf.mod;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** The mod's sound events (Kenney sci-fi sounds, CC0). */
final class SoundsMod {
	static SoundEvent LASER, BOOM, SHIELD_HIT, JUMP, HULL_HIT, CANNON;

	private SoundsMod() {}

	static void register() {
		LASER = Sigf.registerSound("laser");
		BOOM = Sigf.registerSound("boom");
		SHIELD_HIT = Sigf.registerSound("shield_hit");
		JUMP = Sigf.registerSound("jump");
		HULL_HIT = Sigf.registerSound("hull_hit");
		CANNON = Sigf.registerSound("cannon");
	}

	static void at(SoundEvent s, Vec3 pos, float volume, float pitch) { Sigf.sound(s, pos, volume, pitch); }
}
